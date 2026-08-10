# BarProbe

An Android barcode toolkit for functionality testing, security testing, and configuration. Largely LLM-generated. Use at your own risk, of course. 

## Features

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

**Configure** a built=in library for hardware using vendor programming barcodes.

**Learn** a reference section generated from the same symbology table the encoder uses

## Building

Needs JDK 17 or newer (built and tested on 21) and the Android SDK. The NDK and CMake
versions are pinned in `gradle/libs.versions.toml` and must match — libzint is compiled
from source. The application ID is `ca.ryarmst.barprobe`.

```
./gradlew assembleRelease
```

Produces per-ABI APKs under `app/build/outputs/apk/release/`. Without a signing key
configured they are debug-signed — installable for testing, not for distribution.

```
./gradlew test
```

## Signing and publishing

Release builds are signed with a real upload key when one is available, and fall back to
the debug key otherwise (so ordinary builds and forks still work). Key material is never
stored in the repository.

- **Locally**, put an `upload.jks` somewhere outside the repo and a gitignored
  `keystore.properties` at the repo root:

  ```
  storeFile=/absolute/path/to/upload.jks
  storePassword=…
  keyAlias=upload
  keyPassword=…
  ```

- **In CI**, the same values come from GitHub Actions secrets: `SIGNING_KEYSTORE_BASE64`
  (the `.jks`, base64-encoded), `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
  `SIGNING_KEY_PASSWORD`. Pushing a `v*` tag then builds a signed AAB
  (`app/build/outputs/bundle/release/app-release.aab`) and uploads it as a private
  workflow artifact to hand to the Play Console. The public GitHub release keeps the
  sideloadable APKs.

Google Play re-signs the uploaded AAB with its own app-signing key, so Play installs and
these sideload APKs have different signatures and cannot update each other.

`SIGNING.md` has the step-by-step: generating the key, filling in the secrets, and the
Play Console checklist.

## Libraries

Encoding is [libzint](https://sourceforge.net/projects/zint/) 2.16, built from vendored
source through a hand-written JNI bridge that returns the raw module matrix rather than
a rendered image. One renderer then serves the screen, exports and PDF, so geometry is
identical everywhere. Decoding is [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp).

`PLAN.md` documents the design and the reasoning behind it, including the parts that
went wrong.

## Licence

Apache-2.0. See `LICENSE`, and `NOTICE` for third-party components — in particular, only
Zint's BSD-licensed backend is vendored; its GPL frontend is deliberately absent.
