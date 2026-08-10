---
id: check-digits
title: Check digits and why a valid-looking number is rejected
summary: Retail codes carry a computed digit that must be correct.
order: 50
---

Several symbologies append a digit derived arithmetically from the rest of the value. A reader recomputes it and rejects the scan if it does not match, which catches most misreads.

This is why a thirteen-digit number can be refused by an EAN-13 encoder even though the length and character set look right. The final digit is not free.

## The practical rule

- Supply twelve digits to EAN-13 and the check digit is computed for you. This is almost always what you want.
- Supply thirteen and the encoder validates the one you provided.
- The same pattern applies to EAN-8, UPC-A and UPC-E.

> [!NOTE]
> Length and character rules can be checked as you type, but a check digit cannot be judged from the shape of the input. This app runs a real trial encode before allowing a save, so the encoder itself has the final word rather than an approximation of its rules.
