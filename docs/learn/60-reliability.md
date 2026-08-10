---
id: reliability
title: Making barcodes that actually scan
summary: Quiet zones, module size, error correction and contrast.
order: 60
---

## Quiet zone

The blank margin around a symbol is part of the symbol. Without it a reader cannot tell where the code begins. Cropping tightly to the bars is one of the most common reasons a printed barcode fails.

## Module size

A module is the narrowest bar or the smallest square. Everything scales from it. Export in this app is specified in pixels per module rather than an overall size, because a barcode only scans reliably when modules land on whole pixels; scaling an image to a target width produces fractional modules and blurred edges.

## Error correction

Matrix codes reserve part of their capacity for recovery data, so a damaged or partly covered symbol still reads. Higher correction means a larger symbol for the same payload. QR Code offers four levels; the lowest is usually fine for a clean screen or printed label, and higher levels earn their size on surfaces that get worn or dirty.

## Contrast

Readers threshold the image, so they need genuine dark-on-light contrast. Inverted symbols, low-contrast colour pairs and glossy laminates all reduce the margin. The full-screen viewer here forces maximum brightness and a true white background for exactly this reason, since reading a code off a phone screen is otherwise needlessly marginal.

> [!NOTE]
> Avoid lossy formats. JPEG compression artefacts fall on the high-contrast edges a decoder depends on. Use PNG for raster or SVG for anything that will be resized or printed.
