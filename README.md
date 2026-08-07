# BarProbe

An Android barcode toolkit for people who work with barcodes rather than just scan them.

Most barcode apps assume you want to look up a product. This one assumes you need to
produce an exact sequence of bytes, see what a scanner actually returned, and keep both
around afterwards. It works entirely offline, has no ads, and requests no network
permission at all.

## What it does

**Generate** 26 symbologies, 1D and 2D. Payloads are bytes, not text, so you can encode
anything a format allows: escape sequences (`\x1D`, `\d029`, `é`), GS1 Application
Identifiers, Code 128 codeset directives, or raw binary. A character palette inserts the
control characters no keyboard offers, and a byte inspector shows exactly what will be
encoded before you encode it. Output goes to a full-screen viewer, PNG/SVG export, or
batch generation from a wordlist into a ZIP or printable PDF.

**Scan** the same formats with the camera. Linear results have to be seen twice
consistently before they're accepted — a 1D symbol is read along a single line, where a
fold or motion blur can produce a checksum-valid but wrong value from one frame.

**Catalogue** anything you generate or scan into named libraries, with search that
matches the escaped rendering of a payload, so looking for `\x1D` finds codes containing
a Group Separator.

**Configure** hardware using vendor programming barcodes. 546 ship for Zebra/Symbol SSI
scanners, decoded from the barcodes printed in the vendor's guide rather than
transcribed from its tables, and grouped by what a setting does to the device: recovery
first, then whether the scanner will accept further programming at all, then what it
types into the host, then how much it will decode. Parameter values vary between product
families, so check a code against the guide for your model before scanning it at
hardware you care about.

**Learn** what any of the above means. The reference section is generated from the same
symbology table the encoder uses, so it can't drift from what the app will actually
accept.

## Building

Needs JDK 17 or newer (built and tested on 21) and the Android SDK. The NDK and CMake
versions are pinned in `gradle/libs.versions.toml` and must match — libzint is compiled
from source.

```
./gradlew assembleRelease
```

Produces per-ABI APKs under `app/build/outputs/apk/release/`. They are signed with the
debug key so they install for testing; they are not distributable.

```
./gradlew test
```

## How it works

Encoding is [libzint](https://sourceforge.net/projects/zint/) 2.16, built from vendored
source through a hand-written JNI bridge that returns the raw module matrix rather than
a rendered image. One renderer then serves the screen, exports and PDF, so geometry is
identical everywhere. Decoding is [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp).

`PLAN.md` documents the design and the reasoning behind it, including the parts that
went wrong.

## Licence

Apache-2.0. See `LICENSE`, and `NOTICE` for third-party components — in particular, only
Zint's BSD-licensed backend is vendored; its GPL frontend is deliberately absent.
