---
id: payloads-are-bytes
title: Payloads are bytes, not text
summary: The single idea that explains most barcode surprises.
order: 10
---

A barcode stores a sequence of bytes. Text is one interpretation of those bytes, not what is actually encoded. Most confusing barcode behaviour comes from somewhere in the chain treating the payload as a string when it is not.

This matters in practice because plenty of legitimate payloads are not text at all. A GS1 label separates its variable-length fields with a Group Separator, byte 0x1D, which has no printable form. Some industrial labels carry raw binary. A payload can even contain a NUL byte, which will silently truncate anything that treats it as a C string.

## How this app handles it

- Payloads are stored and compared as bytes everywhere, including in the database and in backup files.
- The byte inspector shows exactly what will be encoded, in hex, before you encode it.
- Scan results offer a hex view alongside text, because a decoded symbol is bytes too.

```example
input: AB\x1DCD
result: 41 42 1D 43 44
comment: Five bytes. The Group Separator is one byte, not four characters.
```

> [!NOTE]
> If a scanner appears to drop part of a payload, check the hex view first. A truncation at a NUL byte, or a missing Group Separator, is far more common than a genuine decode failure.
