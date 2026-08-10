---
id: character-encoding
title: Character encodings and ECI
summary: Why non-English text sometimes decodes as nonsense, and how to fix it.
order: 30
---

If a payload is bytes, then any text in it must have been converted to bytes by some encoding. The reader has to apply the same encoding to get the text back. When the two disagree, the symbol still scans perfectly and returns the wrong characters.

For plain unaccented English this never comes up, because every common encoding agrees on the ASCII range. It appears as soon as the content includes an accent, a currency symbol, or any non-Latin script.

## What ECI does

Extended Channel Interpretation is a marker placed inside the symbol that names the encoding of the data that follows. A reader that honours it knows exactly how to interpret the bytes instead of guessing.

```example
input: café
result: 63 61 66 C3 A9
comment: The e-acute is two bytes in UTF-8. Set ECI 26 so the reader knows that, rather than guessing and returning "cafÃ©".
```

## Which to use

- **ECI 26, UTF-8** — The safe default for anything modern, and the only sensible choice for mixed scripts.
- **ECI 3, ISO-8859-1** — Latin-1. Common in older systems and western European data.
- **ECI 20, Shift JIS** — Japanese.
- **ECI 899** — Explicitly uninterpreted binary, for data that is not text.

> [!NOTE]
> Not every reader acts on ECI, and some ignore it entirely. If you control both ends, test the round trip before committing to a large print run. Leaving ECI unset lets the encoder decide, which is right for pure ASCII and a gamble for anything else.

> [!NOTE]
> ECI is carried in the symbol's own encoding modes, which linear symbologies do not have. It is available on matrix formats only, and the app hides the control when it does not apply.
