#!/usr/bin/env python3
"""
Builds a config pack for a "command-grammar" scanner (Datalogic, Honeywell).

Same principle as tools/extract-config-pack.py (the Zebra one): the parameter string is
read from the printed barcode, never transcribed from prose. These vendors differ from
Zebra in a way that helps -- every programming code shares a command prefix ($ for
Datalogic, ~ for Honeywell), so the sample/illustration barcodes a manual also contains
(EAN-13 examples, reseller watermarks, "here is what Code 39 looks like") fall out for
free: anything not matching the grammar is dropped.

There is no rich chapter structure to mine here, so entries are organised by what a
setting does -- read from caption keywords -- rather than by the manual's layout, and a
best-effort section banner becomes the subcategory.

Every entry is re-encoded and decoded before it is kept.

Usage:
    tools/extract-command-pack.py --vendor datalogic --pdf QD2220.pdf \\
        --pack-id datalogic --source "Datalogic QD2220 Product Reference Guide" \\
        --out datalogic.json

Requires: pymupdf, zxing-cpp, pillow
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
except ImportError as exc:  # pragma: no cover
    sys.exit(f"missing dependency: {exc}\n  pip install pymupdf zxing-cpp pillow")

DPI = 200
SCALE = DPI / 72.0

# --- categories, ordered by consequence (see the Zebra pack's rationale) ------------

CAT_RECOVERY = "Recovery & Defaults"
CAT_LOCK = "Programming Lock"
CAT_INTERFACE = "Host Interface"
CAT_OUTPUT = "Host Output & Formatting"
CAT_SYM_ENABLE = "Symbology Enablement"
CAT_SYM_OPTIONS = "Symbology Options"
CAT_BEHAVIOUR = "Scanner Behaviour"
CAT_OTHER = "Other Settings"

CATEGORY_ORDER = [
    CAT_RECOVERY, CAT_LOCK, CAT_INTERFACE, CAT_OUTPUT,
    CAT_SYM_ENABLE, CAT_SYM_OPTIONS, CAT_BEHAVIOUR, CAT_OTHER,
]

RE_DEFAULTS = re.compile(r"default|factory|reset|restore", re.I)
RE_LOCK = re.compile(r"\block\b|host commands|configuration lock|obey|ignore", re.I)
RE_INTERFACE = re.compile(
    r"interface|usb|rs-?232|rs232|keyboard|wedge|\bhid\b|\bibm\b|\bcom\b|\bport\b"
    r"|\boem\b|serial|bluetooth|emulation|nationality|country|national keyboard", re.I)
RE_OUTPUT = re.compile(
    r"prefix|suffix|data format|code id|aim id|label id|\bcr\b|\blf\b|\btab\b"
    r"|terminator|carriage return|line feed|transmit|send|encoding|character set"
    r"|case conversion|global.*format", re.I)
RE_ENABLE = re.compile(r"\benable\b|\bdisable\b|read.+enable|\bon/off\b", re.I)
RE_BEHAVIOUR = re.compile(
    r"beep|volume|tone|\bled\b|aiming|aimer|illumination|illuminator|presentation|trigger"
    r"|sleep|reread|re-?read|timeout|good read|green spot|motion|hands.?free|scan mode"
    r"|scanning active|stand|pick.?list|mobile phone", re.I)
RE_SYMBOLOGY = re.compile(
    r"code ?128|code ?39|code ?93|code ?11|code ?32|codabar|ean|upc|gs1|databar"
    r"|interleaved|2 of 5|itf|msi|plessey|pdf417|data ?matrix|\bqr\b|aztec|maxicode"
    r"|postal|isbt|telepen|matrix 2 of 5|standard 2 of 5|china post|issn|bc412"
    r"|trioptic|iata|gtin|add-?on", re.I)

def when(cond: bool, text: str):
    """Small helper so the warning ladder reads as first-match-wins."""
    return text if cond else None


def classify(caption: str, section: str | None, data: str) -> str:
    # Defaults and lock keyed on the caption alone: a "DEFAULTS" section banner should
    # not make every code beneath it a reset. Everything else may use the section
    # banner too, which is how keyboard-country and per-symbology option pages land in
    # the right bucket even when the individual caption is terse.
    if RE_DEFAULTS.search(caption):
        return CAT_RECOVERY
    if RE_LOCK.search(caption):
        return CAT_LOCK
    text = f"{section or ''} {caption}"
    if RE_INTERFACE.search(text):
        return CAT_INTERFACE
    if RE_OUTPUT.search(text):
        return CAT_OUTPUT
    if RE_SYMBOLOGY.search(text):
        return CAT_SYM_ENABLE if RE_ENABLE.search(caption) else CAT_SYM_OPTIONS
    if RE_BEHAVIOUR.search(text):
        return CAT_BEHAVIOUR
    return CAT_OTHER


VENDORS = {
    "datalogic": {
        # Datalogic programming labels are $-prefixed command strings.
        "grammar": re.compile(r"^\$"),
        "noise": None,
        "vendor_name": "Datalogic",
    },
    "honeywell": {
        # Honeywell menu commands are ~-prefixed.
        "grammar": re.compile(r"^~"),
        # ~K<nibble>K. are single hex-character entry codes used only inside a
        # multi-scan sequence (like Zebra's keypad codes); they are not standalone
        # settings and their captions are meaningless out of context, so drop them.
        "noise": re.compile(r"^~K[0-9A-Fa-f]K\.$"),
        "vendor_name": "Honeywell",
    },
}


def numbered(category: str) -> str:
    return f"{CATEGORY_ORDER.index(category) + 1:02d} {category}"


def strip_glyphs(s: str) -> tuple[str, bool]:
    """Remove private-use 'default' star glyphs; report whether one was present."""
    had = any(0xE000 <= ord(c) <= 0xF8FF or c in "★✱*" for c in s)
    cleaned = "".join(c for c in s if not (0xE000 <= ord(c) <= 0xF8FF)).strip()
    cleaned = cleaned.lstrip("★✱").strip()
    return " ".join(cleaned.split()), had


def is_label(text: str) -> bool:
    """A usable caption: a real option label, not a section banner or a paragraph."""
    if not text or len(text) > 90:
        return False
    if "ENTER/EXIT" in text.upper() or is_section(text):
        return False
    return any(c.islower() for c in text)  # labels have lower case; banners do not


def caption_for(box, blocks):
    x0, y0, x1, y1 = box
    best = None
    for bx0, by0, bx1, by1, t in blocks:
        if by1 < y0 + 2 or bx1 < x0 - 20 or bx0 > x1 + 20:
            continue
        if by0 - y1 < -30:
            continue
        if not is_label(t):
            continue
        if best is None or by0 - y1 < best[0]:
            best = (by0 - y1, t)
    return best[1] if best else ""


def is_section(text: str) -> bool:
    # A section banner is a short, mostly-uppercase phrase. Reject code-like strings:
    # a programming label printed near a barcode (e.g. "$CIACT00") is uppercase and
    # short but is not a heading.
    if text.startswith(("$", "~")):
        return False
    if any(c.isdigit() for c in text) and " " not in text:
        return False
    letters = [c for c in text if c.isalpha()]
    if len(letters) < 3 or len(text) > 60:
        return False
    upper = sum(1 for c in letters if c.isupper())
    return upper / len(letters) > 0.8


def clean_section(text: str) -> str:
    """Trim the boilerplate Datalogic appends to every section banner."""
    t = re.sub(r"ENTER/EXIT.*$", "", text, flags=re.I).strip(" -–—")
    t = re.sub(r"COUNTRY MODE$", "", t, flags=re.I).strip(" -–—")
    return " ".join(t.split())


def section_for(box, blocks):
    """Nearest ALL-CAPS banner above the barcode, cleaned, as a subcategory."""
    y0 = box[1]
    best = None
    for _bx0, by0, _bx1, _by1, t in blocks:
        if by0 <= y0 and is_section(t):
            if best is None or by0 > best[0]:
                best = (by0, t)
    if best is None:
        return None
    cleaned = clean_section(best[1])
    return cleaned or None


def round_trips(data: str) -> bool:
    try:
        img = zxingcpp.write_barcode(zxingcpp.BarcodeFormat.Code128, data, width=600, height=200)
        back = zxingcpp.read_barcodes(img)
    except Exception:
        return False
    return len(back) == 1 and back[0].text == data


def read_pdf(path, grammar, noise):
    doc = pymupdf.open(path)
    out = []
    for pno in range(doc.page_count):
        page = doc[pno]
        pix = page.get_pixmap(dpi=DPI)
        img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
        codes = zxingcpp.read_barcodes(img)
        blocks = [(b[0], b[1], b[2], b[3], " ".join(b[4].split()))
                  for b in page.get_text("blocks") if b[4].strip()]
        for r in codes:
            # Only Code 128 command labels; the grammar prefix drops sample barcodes.
            if str(r.format) != "Code 128" or not grammar.match(r.text):
                continue
            if noise is not None and noise.match(r.text):
                continue
            pos = r.position
            xs = [pos.top_left.x, pos.top_right.x, pos.bottom_right.x, pos.bottom_left.x]
            ys = [pos.top_left.y, pos.top_right.y, pos.bottom_right.y, pos.bottom_left.y]
            box = (min(xs) / SCALE, min(ys) / SCALE, max(xs) / SCALE, max(ys) / SCALE)
            out.append({
                "page": pno + 1,
                "data": r.text,
                "caption": caption_for(box, blocks),
                "section": section_for(box, blocks),
            })
    return out


def build_entries(raw, source):
    entries = []
    dropped = collections.Counter()
    best_by_payload = {}

    for item in raw:
        name, is_default = strip_glyphs(item["caption"])
        if not name:
            dropped["no caption"] += 1
            continue
        if not round_trips(item["data"]):
            dropped["failed round-trip"] += 1
            continue
        # One entry per payload; keep the earliest page (where it is documented, not a
        # later quick-reference reprint).
        prev = best_by_payload.get(item["data"])
        if prev is not None and prev["page"] <= item["page"]:
            dropped["duplicate payload"] += 1
            continue
        if prev is not None:
            dropped["duplicate payload"] += 1
        best_by_payload[item["data"]] = {**item, "name": name, "is_default": is_default}

    for item in best_by_payload.values():
        name = item["name"]
        category = classify(name, item["section"], item["data"])
        # Destructive/warnings are deliberately narrow: only the settings that can lose
        # configuration or cut the scanner off from the host, not every setting in those
        # categories. "USB Keyboard Speed = 10ms" is an interface setting but harmless;
        # "Select USB-COM-STD" changes the interface itself.
        restores = bool(RE_DEFAULTS.search(name))
        selects_interface = bool(
            re.search(r"\bselect\b.*(usb|rs-?232|keyboard|wedge|interface|\bhid\b|\bibm\b"
                      r"|\boem\b|emulation)|interface\s*=|usb suspend", name, re.I))
        is_lock = bool(re.search(r"host commands|configuration lock|interface options", name, re.I))
        destructive = restores or selects_interface or is_lock
        warning = when(restores, "Resets settings. Anything you have configured on the "
                       "scanner is lost.") \
            or when(selects_interface, "Changes the host interface. The scanner may stop "
                    "communicating with the host until the matching interface is selected "
                    "on both ends; recovery can need a specific cable or host setting.") \
            or when(is_lock, "Affects whether the scanner will accept further programming.")

        desc_bits = []
        if item["is_default"]:
            desc_bits.append("Factory default.")
        desc_bits.append(f"Programming label {item['data']}.")
        entries.append({
            "name": name,
            "description": " ".join(desc_bits),
            "category": numbered(category),
            "subcategory": item["section"],
            "symbology": "CODE_128",
            "data": item["data"],
            "escapes_enabled": False,
            "provenance": f"{source}, p.{item['page']} (barcode decoded from the PDF)",
            "verification": "VERIFIED",
            "warning": warning,
            "destructive": destructive,
            "restores_defaults": restores,
        })

    disambiguate(entries)
    entries.sort(key=lambda e: (e["category"], e["subcategory"] or "~", e["name"]))
    return entries, dropped


def disambiguate(entries):
    """Make (category, name) unique -- the DB index drops collisions silently."""
    groups = collections.defaultdict(list)
    for e in entries:
        groups[(e["category"], e["name"])].append(e)
    for (_c, _n), group in groups.items():
        if len(group) == 1:
            continue
        # Disambiguate with the payload, which is short and unique -- the section
        # banners here are long ("... ENTER/EXIT PROGRAMMING MODE") and make ugly names.
        for e in group:
            e["name"] = f"{e['name']} [{e['data']}]"


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--vendor", required=True, choices=sorted(VENDORS))
    ap.add_argument("--pdf", required=True)
    ap.add_argument("--pack-id", required=True)
    ap.add_argument("--source", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    profile = VENDORS[args.vendor]
    raw = read_pdf(args.pdf, profile["grammar"], profile["noise"])
    print(f"decoded {len(raw)} command labels", file=sys.stderr)

    entries, dropped = build_entries(raw, args.source)
    for reason, n in dropped.most_common():
        print(f"  dropped {n}: {reason}", file=sys.stderr)
    print(f"kept {len(entries)} entries", file=sys.stderr)

    pack = {
        "format_version": 1,
        "pack_id": args.pack_id,
        "vendor": profile["vendor_name"],
        "description": (
            f"Programming barcodes for {profile['vendor_name']} scanners, decoded from "
            f"the barcodes printed in {args.source} rather than transcribed, and "
            f"re-encoded to confirm each round-trips. Organised by what a setting does; "
            f"parameter codes vary by model and firmware, so check a code against the "
            f"guide for your exact scanner before scanning it at hardware you care about."
        ),
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
