# Configuration pack format

A pack is one JSON file describing programming barcodes for a device family.

## Why vendor packs ship empty

Programming barcodes reconfigure real hardware, and the correct parameter string
differs between vendors, product families and sometimes firmware revisions. A wrong
string can leave a scanner misconfigured or awkward to recover.

The app therefore ships no vendor parameter codes it cannot stand behind. What ships
instead is the format, the search, the safety handling, and an importer, so you can
author packs from the reference guide for your exact model. The bundled `selftest`
pack contains ordinary data barcodes only; it carries no commands and cannot change
any device setting.

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
