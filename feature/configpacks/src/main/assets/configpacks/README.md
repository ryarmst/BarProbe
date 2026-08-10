# Configuration pack format

A pack is one JSON file describing programming barcodes for a device family.

## What ships

Three vendor packs ship, each decoded from the barcodes printed in the vendor's own
guide — never transcribed from the tables beside them — then re-encoded and decoded
again to confirm each survives a round trip unchanged:

- `zebra` — 546 codes for Zebra/Symbol SSI scanners, from the SSI configuration guide.
- `datalogic` — 617 codes, from the QD2220 product reference guide.
- `honeywell` — 14 codes, from the Voyager 1200g quick-start guide.

Two extractors build them. `tools/extract-config-pack.py` handles Zebra's SSI manual
with its rich chapter structure. `tools/extract-command-pack.py` handles the
command-grammar vendors (Datalogic `$…`, Honeywell `~…`): the command prefix itself
separates real programming codes from the sample/illustration barcodes a manual also
prints, and multi-scan character primitives (which mean nothing standalone) are dropped.

Codes vary by model and firmware, so a pack is a starting point — check a code against
the guide for your exact scanner before scanning it at hardware you care about. Author
more from a guide and import them the same way.

The bundled `selftest` pack contains ordinary data barcodes only; it carries no commands
and cannot change any device setting.

## Organising a vendor pack

`category` is the navigation level in the UI, so it should answer "what does this do to
the device", not "which chapter was it in". The Zebra pack uses, in order:

`Recovery & Defaults`, `Programming Lock`, `Host Output & Injection`,
`Symbology Enablement`, `Symbology Length Limits`, `Symbology Integrity`,
`Symbology Options`, `Image & Signature Capture`, `Document Capture`,
`OCR & Sensitive Text`, `Scanner Behaviour`, `Parameter Entry Values`.

Recovery comes first so the way back is always visible. The two groups after it are the
ones that matter most to anyone auditing a device: whether the scanner will accept
further programming barcodes at all, and what it injects into the host alongside the
data it reads.

`subcategory` keeps the vendor's own setting name, which is what someone holding the
guide will search for.

## (category, name) must be unique within a pack

`config_entries` has a unique index on `(pack_id, category, name)` and the DAO inserts
with `REPLACE`. Two entries sharing a category and a name means the second silently
evicts the first: no error, the barcode is just missing. Vendor guides reuse wording
constantly -- "Inverse Autodetect" appears under five different symbologies in the Zebra
guide -- so qualify colliding names with their subcategory. A test enforces this.

## Schema

```json
{
  "format_version": 1,
  "pack_id": "vendor-model",
  "vendor": "Vendor name",
  "description": "Optional",
  "entries": [
    {
      "name": "Enable Code 39",
      "description": "Optional longer explanation.",
      "category": "symbology-enable",
      "subcategory": "linear",
      "symbology": "CODE_128",
      "data": "...",
      "escapes_enabled": false,
      "provenance": "Model X reference guide, rev A, page 42",
      "verification": "VERIFIED",
      "warning": "Optional caution shown before the symbol is displayed",
      "destructive": false,
      "restores_defaults": false
    }
  ]
}
```

## Required fields

`name`, `category`, `data` and `provenance` are all mandatory. Provenance is required
deliberately: an entry nobody can trace back to documentation is an entry nobody can
check.

## verification

- `VERIFIED` — cross-checked against the vendor's own documentation.
- `COMMUNITY_REPORTED` — reported to work, not confirmed against primary docs.
- `EXAMPLE_ONLY` — structural example, not a real parameter code.
- `UNSPECIFIED` — assumed when omitted.

Anything other than `VERIFIED` or `COMMUNITY_REPORTED` is gated behind a confirmation
before the app will render a scannable symbol.

## Flags

- `destructive` — resets, interface changes, anything disruptive or awkward to undo.
  Gated behind a confirmation.
- `restores_defaults` — the recovery path. Surfaced first within a vendor, so getting
  back to a known state is always one step away.

## Escape sequences

Set `escapes_enabled` to true when `data` uses escapes. The same syntax as the
generator applies: `\xNN` for a byte, `\uNNNN` for a code point, and for Code 128
`\^A` / `\^B` / `\^C` to switch code sets plus `\^1` for FNC1. Many parameter strings
need a control character that cannot be written literally.
