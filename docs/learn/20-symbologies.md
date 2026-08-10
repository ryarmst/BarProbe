---
id: symbologies
title: "Symbologies: choosing a barcode type"
summary: What linear and matrix codes are for, and how to pick one.
order: 20
---

A symbology is a set of rules for turning data into a pattern. Each one fixes which characters it can carry, how much data fits, and how errors are handled. Choosing the wrong one is the most common reason a barcode cannot be produced at all.

## Linear, or 1D

Data is encoded in the widths of bars and spaces, and read along a single line across the symbol. Linear codes are compact, print reliably at small sizes, and are what almost all retail and warehouse scanning uses.

Because a linear symbol is read on one scan line, a smudge, fold or motion blur can produce a wrong-but-valid reading from a single frame. That is why this app requires a linear result to be seen twice consistently before accepting it.

## Matrix, or 2D

Data is encoded across an area. Matrix codes hold far more, carry their own error correction, and can usually be read even when partly damaged or obscured. They need a camera or imager rather than a laser scanner.

## Practical guidance

- **Retail products** — EAN-13, EAN-8, UPC-A or UPC-E. Fixed-length numeric, with a check digit. Not a free choice: the number identifies the product.
- **General purpose text or mixed data** — Code 128 for linear, QR Code or Data Matrix for matrix. All three handle the full ASCII range.
- **Supply chain with structured fields** — GS1-128 or GS1 DataBar, which carry Application Identifiers.
- **Small parts and direct marking** — Data Matrix. It stays readable at very small sizes and is the usual choice for etched or laser-marked parts.
- **Documents and logistics labels** — PDF417. Wide and flat, high capacity, used on shipping labels and identity documents.

> [!NOTE]
> The symbology reference in this app lists every supported format with its character set, length rules and capabilities, taken directly from the encoder's own definitions.
