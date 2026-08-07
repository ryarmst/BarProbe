#!/usr/bin/env python3
"""
Builds a config pack by decoding the barcodes printed in a vendor's own guide.

The important idea: the parameter string is taken from the barcode image, not from
the surrounding prose. A vendor's table tells you what a setting means; the barcode
tells you exactly which bytes the scanner receives. Decoding it removes the step
where a human transcribes a hex string and gets it wrong, which is the failure mode
that kept vendor packs out of this app in the first place.

Every entry is round-tripped before it is written: the decoded data is re-encoded,
decoded again, and dropped unless it survives unchanged.

Usage:
    tools/extract-config-pack.py --pdf GUIDE.pdf --vendor Zebra \\
        --pack-id zebra-ssi --source "Guide title, rev A" --out pack.json

Requires: pymupdf, zxing-cpp, pillow  (pip install pymupdf zxing-cpp pillow)
"""

from __future__ import annotations

import argparse
import collections
import json
import re
import sys

try:
    import pymupdf
    import zxingcpp
    from PIL import Image
except ImportError as exc:  # pragma: no cover - operator feedback only
    sys.exit(f"missing dependency: {exc}\n  pip install pymupdf zxing-cpp pillow")

# 200 dpi is comfortably enough for the Code 128 in these guides and keeps a
# 250-page document under half a minute. Higher resolutions found nothing extra.
DPI = 200
SCALE = DPI / 72.0

# Symbology names as this app's registry spells them.
FORMAT_TO_SYMBOLOGY = {
    "Code128": "CODE_128",
    "Code 128": "CODE_128",
    "Code39": "CODE_39",
    "Code 39": "CODE_39",
}

FORMAT_TO_ZXING = {
    "CODE_128": zxingcpp.BarcodeFormat.Code128,
    "CODE_39": zxingcpp.BarcodeFormat.Code39,
}


# --------------------------------------------------------------------------
# Security-oriented classification
# --------------------------------------------------------------------------
#
# The guide is organised by feature area, which is the wrong axis for anyone
# auditing or hardening a device: "Symbologies" is a single 292-entry chapter
# mixing "which codes will this scanner read at all" with "how loud is the beep".
#
# These categories are ordered by how much damage a wrong scan does, and grouped
# by the question being asked rather than by the chapter it appeared in.

CAT_RECOVERY = "Recovery & Defaults"
CAT_LOCK = "Programming Lock"
CAT_OUTPUT = "Host Output & Injection"
CAT_SYM_ENABLE = "Symbology Enablement"
CAT_SYM_LENGTH = "Symbology Length Limits"
CAT_SYM_INTEGRITY = "Symbology Integrity"
CAT_SYM_OPTIONS = "Symbology Options"
CAT_IMAGING = "Image & Signature Capture"
CAT_DOCCAP = "Document Capture"
CAT_OCR = "OCR & Sensitive Text"
CAT_BEHAVIOUR = "Scanner Behaviour"
CAT_ENTRY = "Parameter Entry Values"

# Ordered by consequence, not by the guide's chapters: the way back first, then
# whether the device will accept further programming at all, then what it types
# into the host, then how much it will decode.
CATEGORY_ORDER = [
    CAT_RECOVERY, CAT_LOCK, CAT_OUTPUT,
    CAT_SYM_ENABLE, CAT_SYM_LENGTH, CAT_SYM_INTEGRITY, CAT_SYM_OPTIONS,
    CAT_IMAGING, CAT_DOCCAP, CAT_OCR, CAT_BEHAVIOUR, CAT_ENTRY,
]


def numbered(category: str) -> str:
    """
    Prefix a category with its rank.

    The database orders categories alphabetically (after floating the one holding
    the restore-defaults codes), and it has no column expressing a pack's intended
    order. Left alone that puts "Programming Lock" -- the group that decides
    whether the scanner can be reprogrammed at all -- between "Parameter Entry
    Values" and "Scanner Behaviour", which is precisely the wrong place for it.
    A zero-padded rank in the name makes the alphabetical sort reproduce the
    intended one, and tells the reader the order means something.
    """
    return f"{CATEGORY_ORDER.index(category) + 1:02d} {category}"

# Keypad barcodes used to type a numeric value during a multi-scan sequence.
# They are matched on the payload because they appear on pages belonging to
# whatever section preceded them, so their heading is meaningless.
KEYPAD_RE = re.compile(r"^[AB](?:[0-9A-F]{1,2}|[-+])$")

RE_DEFAULTS = re.compile(r"restore defaults|factory defaults|custom defaults", re.I)
RE_LOCK = re.compile(
    r"parameter scanning|parameter pass through|concatenated parameter", re.I)
RE_OUTPUT = re.compile(
    r"transmission format|prefix/suffix|prefix|suffix|code id|aim id"
    r"|fn1 substitution|no.?read message|scan data", re.I)
RE_LENGTH = re.compile(r"^set lengths|discrete length|length within range|any length", re.I)
RE_INTEGRITY = re.compile(
    r"check digit|security level|redundancy|quiet zone|checksum|verification", re.I)
RE_ENABLE = re.compile(r"^enable/disable|^\*?(enable|disable)\b", re.I)
RE_IMAGING = re.compile(
    r"image|imaging|snapshot|video|illumination|signature|jpeg|aim brightness"
    r"|pixel|resolution|cropping|sharpen|contrast|mirror", re.I)
RE_BEHAVIOUR = re.compile(
    r"beep|led|blink|timeout|power|picklist|session|aiming pattern|volume|tone"
    r"|duration|mobile phone|continuous", re.I)

# Settings that deserve an explicit caution beyond the destructive flag.
SENSITIVE_NOTES = [
    # \b on both sides: without it this matched "MicroPDF417" and "MicroQR" and
    # attached a cheque-reading caution to two ordinary 2D symbologies.
    (re.compile(r"\bmicr\b", re.I),
     "MICR E13B reads the magnetic-ink line on cheques, including account and "
     "routing numbers. Enabling it changes what the scanner will capture."),
    (re.compile(r"currency", re.I),
     "Reads serial numbers from banknotes. Enabling it changes what the scanner "
     "will capture."),
    (re.compile(r"disable parameter scanning", re.I),
     "Stops the scanner acting on programming barcodes at all, including this "
     "pack. Recovery may need the host interface or a power-cycle sequence."),
    # Ordered before the plain "lock" rule so the recovery direction is not
    # described as though it were the lockout.
    (re.compile(r"\bunlock\b", re.I),
     "Restores the scanner's ability to accept programming barcodes."),
    (re.compile(r"\block\b", re.I),
     "Locks the scanner against further parameter scanning. Scan Unlock from this "
     "same group to reverse it; without it the device cannot be reprogrammed by "
     "barcode."),
    # Phrased for both directions: the same note is attached to Enable and Disable.
    (re.compile(r"pass through", re.I),
     "Controls whether parameter barcode contents are forwarded to the host "
     "instead of being applied to the scanner."),
]


def classify(chapter: str, section: str, caption: str, data: str) -> str:
    """Assign one entry to a category. Order matters: earlier rules win."""
    text = f"{section} {caption}"

    if KEYPAD_RE.match(data):
        return CAT_ENTRY
    if RE_DEFAULTS.search(text):
        return CAT_RECOVERY
    if RE_LOCK.search(text):
        return CAT_LOCK
    if RE_OUTPUT.search(text):
        return CAT_OUTPUT

    if chapter.startswith("OCR"):
        return CAT_OCR
    if chapter.startswith("Intelligent") or re.match(r"Parameter Name: (DocCap|Sig)_", section):
        return CAT_DOCCAP

    if chapter.startswith("Symbologies"):
        if RE_LENGTH.search(section) or RE_LENGTH.search(caption):
            return CAT_SYM_LENGTH
        if RE_INTEGRITY.search(text):
            return CAT_SYM_INTEGRITY
        if RE_ENABLE.search(section) or RE_ENABLE.search(caption):
            return CAT_SYM_ENABLE
        return CAT_SYM_OPTIONS

    if chapter.startswith("Imaging") or RE_IMAGING.search(text):
        return CAT_IMAGING
    if RE_BEHAVIOUR.search(text):
        return CAT_BEHAVIOUR
    return CAT_BEHAVIOUR


# --------------------------------------------------------------------------
# PDF reading
# --------------------------------------------------------------------------

def chapter_map(doc):
    """
    Recover chapter names from the running headers.

    Odd pages read "Symbologies 4 - 31"; even pages read "1 - 2 <book title>".
    So the chapter *number* is available on every page, but the name only on
    half of them. Build number -> name from the pages that have it, then apply
    it to the rest. Table-of-contents pages match the same shape, so entries
    containing dot leaders are rejected.
    """
    candidates = collections.defaultdict(collections.Counter)
    page_chapter = {}
    for i, page in enumerate(doc):
        blocks = [b[4].strip() for b in page.get_text("blocks") if b[4].strip()][:3]
        for head in blocks:
            flat = " ".join(head.split())
            if "...." in flat or len(flat) > 70:
                continue
            m = re.match(r"^(.+?)\s+(\d+)\s*-\s*\d+$", flat)
            if m and not m.group(1).lower().startswith("barcode"):
                page_chapter[i] = int(m.group(2))
                candidates[int(m.group(2))][m.group(1).strip()] += 1
                break
            m = re.match(r"^(\d+)\s*-\s*\d+\s+(.+)$", flat)
            if m:
                page_chapter[i] = int(m.group(1))
                break
    names = {k: v.most_common(1)[0][0] for k, v in candidates.items()}
    return {i: names.get(n, "Other") for i, n in page_chapter.items()}


def page_sections(blocks):
    """
    Locate the setting headings on a page.

    A heading is recognised by what follows it: these guides print the parameter
    name, then a line carrying "SSI #" or "Parameter #". Keying off the marker
    line and stepping back one block is far more reliable than trying to detect
    heading typography.
    """
    found = []
    for i, (_x0, _y0, _x1, _y1, text) in enumerate(blocks):
        if not re.search(r"Parameter #|SSI #", text):
            continue
        for j in range(i - 1, -1, -1):
            cand = blocks[j][4]
            if len(cand) < 70 and not re.search(r"Parameter #|SSI #|^\d+ -", cand):
                found.append((blocks[j][1], cand))
                break
    return found


def caption_for(barcode_box, blocks):
    """Nearest text block below the barcode that overlaps it horizontally."""
    x0, y0, x1, y1 = barcode_box
    best = None
    for bx0, by0, bx1, by1, text in blocks:
        if by1 < y0 + 2:
            continue
        if bx1 < x0 - 20 or bx0 > x1 + 20:
            continue
        gap = by0 - y1
        # Captions sit directly beneath and sometimes overlap the symbol's
        # bounding box by a few points, so a small negative gap is allowed.
        if gap < -30:
            continue
        if best is None or gap < best[0]:
            best = (gap, text)
    return best[1] if best else ""


def read_pdf(path):
    doc = pymupdf.open(path)
    chapters = chapter_map(doc)
    results = []
    carried = None
    for pno in range(doc.page_count):
        page = doc[pno]
        pix = page.get_pixmap(dpi=DPI)
        img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
        codes = zxingcpp.read_barcodes(img)
        blocks = [(b[0], b[1], b[2], b[3], " ".join(b[4].split()))
                  for b in page.get_text("blocks") if b[4].strip()]
        blocks.sort(key=lambda b: (b[1], b[0]))
        sections = page_sections(blocks)

        # Captured before `carried` advances: barcodes above the first heading on
        # this page belong to the section that ran on from the previous page, not
        # to the last heading printed here.
        incoming = carried
        if sections:
            carried = sections[-1][1]
        if not codes:
            continue

        for r in codes:
            pos = r.position
            xs = [pos.top_left.x, pos.top_right.x, pos.bottom_right.x, pos.bottom_left.x]
            ys = [pos.top_left.y, pos.top_right.y, pos.bottom_right.y, pos.bottom_left.y]
            box = (min(xs) / SCALE, min(ys) / SCALE, max(xs) / SCALE, max(ys) / SCALE)
            section = incoming
            for sy, st in sections:
                if sy <= box[1]:
                    section = st
            results.append({
                "page": pno + 1,
                "chapter": chapters.get(pno, "Other"),
                "section": section or "General",
                "caption": caption_for(box, blocks),
                "format": str(r.format),
                "data": r.text,
            })
    return results


# --------------------------------------------------------------------------
# Entry construction
# --------------------------------------------------------------------------

# The legend "* Indicates Default" is printed on many pages and lands in a
# caption whenever it happens to be the nearest block.
LEGEND_RE = re.compile(r"^\*?\s*Indicates Default\s*", re.I)
VALUE_RE = re.compile(r"\s*\(([0-9A-Fa-f]{2})h\)\s*$")
PAREN_NOTE_RE = re.compile(r"\s*\((?:Optimum Setting|Default)\)\s*$", re.I)


def clean_caption(raw):
    """Split a caption into (name, is_default, parameter_value)."""
    text = LEGEND_RE.sub("", raw).strip()
    is_default = text.startswith("*")
    text = text.lstrip("*").strip()
    value = None
    m = VALUE_RE.search(text)
    if m:
        value = m.group(1).upper()
        text = VALUE_RE.sub("", text)
    text = PAREN_NOTE_RE.sub("", text).strip()
    # A caption that ran onto a second line arrives with the break as a space
    # already; collapse any remaining whitespace runs.
    return " ".join(text.split()), is_default, value


def round_trips(data, symbology):
    """Re-encode and decode again; an entry that changes is not shipped."""
    fmt = FORMAT_TO_ZXING.get(symbology)
    if fmt is None:
        return False
    try:
        img = zxingcpp.write_barcode(fmt, data, width=600, height=200)
        back = zxingcpp.read_barcodes(img)
    except Exception:
        return False
    return len(back) == 1 and back[0].text == data


def build_entries(raw, source):
    entries = []
    seen = set()
    dropped = collections.Counter()

    for item in raw:
        symbology = FORMAT_TO_SYMBOLOGY.get(item["format"])
        if symbology is None:
            dropped["unsupported symbology"] += 1
            continue

        name, is_default, value = clean_caption(item["caption"])
        if not name:
            dropped["no caption"] += 1
            continue

        key = (item["data"], name)
        if key in seen:
            dropped["duplicate"] += 1
            continue

        if not round_trips(item["data"], symbology):
            dropped["failed round-trip"] += 1
            continue
        seen.add(key)
        item["_name"] = name

        category = classify(item["chapter"], item["section"], name, item["data"])
        text = f"{item['section']} {name}"

        restores = bool(RE_DEFAULTS.search(text))
        # Destructive covers more than resets: anything that can leave the device
        # unable to be reprogrammed by barcode belongs here too. "Lock" qualifies
        # and "Unlock" explicitly does not, since Unlock is the way back.
        destructive = restores or (
            bool(re.search(r"factory defaults|custom defaults|disable parameter scanning",
                           text, re.I))
            or (bool(re.search(r"\block\b", name, re.I))
                and not re.search(r"\bunlock\b", name, re.I))
        )

        # Matched against the entry name alone, never the section. The section here
        # is often "Lock/Unlock Parameter Scanning", which contains both words, so
        # matching the combined text gave the Lock code the Unlock caution -- the
        # exact opposite of what it does.
        warning = None
        for pattern, note in SENSITIVE_NOTES:
            if pattern.search(name):
                warning = note
                break

        bits = []
        if is_default:
            bits.append("Factory default.")
        if value:
            bits.append(f"Parameter value {value}h.")
        bits.append(f"Setting group: {item['section']}.")
        description = " ".join(bits)

        entries.append({
            "name": name,
            "description": description,
            "category": numbered(category),
            "subcategory": item["section"],
            "symbology": symbology,
            "data": item["data"],
            "escapes_enabled": False,
            "provenance": f"{source}, p.{item['page']} (barcode decoded from the PDF)",
            "verification": "VERIFIED",
            "warning": warning,
            "destructive": destructive,
            "restores_defaults": restores,
            "_page": item["page"],
        })

    entries = dedupe_by_payload(entries, dropped)
    for e in entries:
        del e["_page"]

    disambiguate(entries)

    entries.sort(key=lambda e: (e["category"], e["subcategory"], e["name"]))
    return entries, dropped


def dedupe_by_payload(entries, dropped):
    """
    Keep one entry per payload.

    For SSI one payload is one command, so a repeat is the same barcode printed
    twice. That happens because these guides reprint codes in defaults-summary
    tables, and those pages carry no usable heading structure -- the reprint
    inherits whatever section ran on from earlier, producing nonsense like
    "Disable OCR-A" filed under "Video Resolution".

    The reprints are recognisable: the summary tables caption them
    "<setting> Feature/Option". Prefer anything else, then the earliest page,
    which is where the setting is actually documented.
    """
    def rank(e):
        return ("Feature/Option" in e["name"], e["_page"])

    best = {}
    for e in entries:
        cur = best.get(e["data"])
        if cur is None or rank(e) < rank(cur):
            if cur is not None:
                dropped["duplicate payload"] += 1
            best[e["data"]] = e
        else:
            dropped["duplicate payload"] += 1
    return list(best.values())


def disambiguate(entries):
    """
    Make (category, name) unique across the pack.

    Not cosmetic. The database holds a unique index on (pack_id, category, name)
    and inserts with REPLACE, so two entries sharing a category and a name means
    the second silently evicts the first at load time -- no error, just a missing
    barcode. Captions collide constantly here because the guide reuses wording
    like "Inverse Autodetect" across five different symbologies.

    Colliding names take a subcategory prefix, which is also what makes them
    readable in search results; unique ones are left alone.
    """
    by_key = collections.defaultdict(list)
    for e in entries:
        by_key[(e["category"], e["name"])].append(e)

    for (category, name), group in by_key.items():
        if len(group) == 1:
            continue
        for e in group:
            e["name"] = f"{e['subcategory']} - {e['name']}"

    # A subcategory prefix is usually enough. Where it is not -- the same wording
    # twice inside one setting group -- fall back to the payload, which is unique
    # by construction and at least tells the reader which code they are looking at.
    seen = collections.Counter()
    for e in sorted(entries, key=lambda x: (x["category"], x["name"], x["data"])):
        key = (e["category"], e["name"])
        seen[key] += 1
        if seen[key] > 1:
            e["name"] = f"{e['name']} [{e['data']}]"


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pdf", required=True)
    ap.add_argument("--vendor", required=True)
    ap.add_argument("--pack-id", required=True)
    ap.add_argument("--source", required=True, help="Citation used as provenance")
    ap.add_argument("--description", default=None)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    raw = read_pdf(args.pdf)
    print(f"decoded {len(raw)} barcodes", file=sys.stderr)

    entries, dropped = build_entries(raw, args.source)
    for reason, count in dropped.most_common():
        print(f"  dropped {count}: {reason}", file=sys.stderr)
    print(f"kept {len(entries)} entries", file=sys.stderr)

    pack = {
        "format_version": 1,
        "pack_id": args.pack_id,
        "vendor": args.vendor,
        "description": args.description or (
            f"Parameter barcodes decoded from {args.source}. Every entry's data was "
            f"read from the barcode printed in the guide, not transcribed from its "
            f"text, and re-encoded to confirm it round-trips."),
        "entries": entries,
    }
    with open(args.out, "w") as fh:
        json.dump(pack, fh, indent=2)
        fh.write("\n")

    by_cat = collections.Counter(e["category"] for e in entries)
    print("\ncategories:", file=sys.stderr)
    for cat in sorted(by_cat):
        print(f"  {by_cat[cat]:4}  {cat}", file=sys.stderr)


if __name__ == "__main__":
    main()
