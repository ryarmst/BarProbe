# Barcode Workbench — Development Plan

A professional Android barcode tool: generator, reader, catalogue, and device-configuration barcode library. No advertising, no analytics, no telemetry, no account, no network permission.

Working name: Barcode Workbench. Package: `dev.barcodeworkbench` (both easily changed before first commit).

## 1. Non-negotiable constraints

These are design constraints, not aspirations, and they are enforced structurally rather than by policy:

- The manifest declares no `INTERNET` permission. The app provably cannot phone home. This is verified by a lint rule / manifest test in CI.
- The only runtime permission requested is `CAMERA`, and only when the scanner is opened. File access uses the Storage Access Framework, so no storage permission is needed.
- No ad SDK, no analytics SDK, no crash reporter, no "rate this app" nag, no upsell, no feature gating.
- All barcode encoding and decoding happens on-device.

## 2. Library decision

Confirmed by research, not assumption.

Generation uses libzint, compiled from source via the NDK behind a thin JNI layer.

- libzint is BSD-3-Clause for v2.5+ (the GPLv3 applies to Zint's GUI and CLI, not the library), so it is safe to ship.
- Supports 50+ symbologies, which makes the "easy to extend" requirement a matter of adding a registry row rather than integrating a new library.
- Critically, it is the only realistic option that satisfies "encode ALL supported characters for each barcode type". It provides:
  - `\xNN` for any 8-bit value and `\uNNNN` for any Unicode BMP codepoint
  - `\^A`, `\^B`, `\^C`, `\^@` for manual Code 128 codeset switching
  - `\^1` to insert FNC1
  - GS1 mode, ECI selection, and a raw 8-bit data mode

Verified directly against `backend/zint.h` in the current source (copyright through 2026, `cmake_minimum_required 3.10`). The `input_mode` field is a base mode OR'd with flag bits, which is the mechanism the composer drives:

- Base: `DATA_MODE` (0, raw binary), `UNICODE_MODE` (1, UTF-8), `GS1_MODE` (2)
- Flags: `ESCAPE_MODE` (0x0008) enables `\xNN` / `\uNNNN`; `EXTRA_ESCAPE_MODE` (0x0100) is specifically what enables the Code 128 `\^A`/`\^B`/`\^C` codeset escapes; plus `GS1PARENS_MODE`, `GS1NOCHECK_MODE`, `HEIGHTPERROW_MODE`, `FAST_MODE`

The library's licence file confirms the 2013 relicence away from GPL, with the condition that original copyright attribution is retained in both sources and documentation. We comply by shipping the notice in an in-app attribution screen.

Reading uses zxing-cpp via its prebuilt Android artifact.

- `io.github.zxing-cpp:android:3.1.1` from Maven Central, Apache-2.0, no custom native build required.
- Reads DataBar (all variants), MaxiCode, Micro QR, rMQR, DotCode, DX Film Edge, Telepen, plus all the common linear and matrix types. This is a substantially wider set than ML Kit, which cannot read DataBar, DotCode, MaxiCode, Micro QR, or rMQR at all.

Rejected: pure-JVM ZXing (only 13 symbologies, no character-level or codeset control, fails both the breadth and the full-charset requirements). Rejected: ML Kit for reading (closed, narrower format set).

Note that zxing-cpp 3.x uses zint as its own writing backend internally. We still bind libzint directly, because going through the wrapper's writer API would hide the input-mode, escape, ECI, and codeset controls that feature 1 depends on.

## 3. Architecture

Multi-module Gradle. The split exists to keep the barcode engines swappable and the feature code independent.

```
:app                    Compose host, navigation, DI wiring
:core:model             pure Kotlin domain types, no Android deps
:core:database          Room entities, DAOs, versioned migrations
:core:designsystem      Material 3 theme, shared components
:barcode:engine-api     interfaces: BarcodeEncoder, BarcodeDecoder
:barcode:zint           CMake + JNI over vendored libzint  (implements encoder)
:barcode:reader         zxing-cpp wrapper                  (implements decoder)
:barcode:render         module-matrix to PNG / SVG / PDF
:feature:generator
:feature:scanner
:feature:catalogue
:feature:configpacks
```

Feature modules depend on `:barcode:engine-api`, never on `:barcode:zint` or `:barcode:reader` directly. Swapping an engine means changing one Hilt binding.

### 3.1 The symbology registry

This is the extensibility mechanism. Every symbology is one immutable row; the UI is generated from the registry, so adding a format requires no UI changes.

```kotlin
data class SymbologySpec(
    val id: SymbologyId,
    val displayName: String,
    val zintSymbolId: Int,              // libzint BARCODE_* constant
    val readerFormat: ReaderFormat?,    // null when read-unsupported
    val dimension: Dimension,           // LINEAR | MATRIX | POSTAL | COMPOSITE
    val category: Category,             // RETAIL | LOGISTICS | INDUSTRIAL | POSTAL | GENERAL
    val charsetRule: CharsetRule,
    val lengthRule: LengthRule,
    val supportsGs1: Boolean,
    val supportsEci: Boolean,
    val supportsStructuredAppend: Boolean,
    val checkDigit: CheckDigitBehaviour, // NONE | AUTO | OPTIONAL | REQUIRED
    val defaultOptions: EncodeOptions,
    val sampleValue: String,
    val notes: String,
)
```

Because libzint exposes `ZBarcode_Cap`, `ZBarcode_ValidID`, and `ZBarcode_BarcodeName`, the registry is not hand-maintained in isolation — a unit test cross-checks every row against the linked library and fails if they disagree. The capability flags map almost directly onto the spec fields:

- `ZINT_CAP_ECI` → `supportsEci`
- `ZINT_CAP_GS1` → `supportsGs1`
- `ZINT_CAP_STRUCTAPP` → `supportsStructuredAppend`
- `ZINT_CAP_HRT` → whether human-readable text is available
- `ZINT_CAP_FIXED_RATIO` → drives viewer aspect handling
- `ZINT_CAP_MASK`, `ZINT_CAP_DOTTY`, `ZINT_CAP_COMPOSITE` → expose advanced per-symbology options

This eliminates the usual failure mode where a hand-written format table silently drifts from what the encoder actually supports.

Launch set (~25 formats, all libzint-backed):

- Linear: Code 128, GS1-128, Code 39, Code 93, Codabar, ITF, ITF-14, EAN-13, EAN-8, UPC-A, UPC-E, Code 11, MSI Plessey, Telepen
- Matrix: QR Code, Micro QR, rMQR, Data Matrix, Aztec, PDF417, MicroPDF417, DotCode (generate only, see 13.2), MaxiCode
- GS1 DataBar: Omnidirectional, Limited, Expanded

Extending later to zint's remaining symbologies (Code 16K, Codablock-F, Code 49, postal codes, composites) is a registry addition.

### 3.2 Payload representation

Payloads are stored and passed as bytes, never as a lossy `String`, so binary and control-character content round-trips exactly.

```kotlin
data class Payload(
    val bytes: ByteArray,
    val mode: InputMode,     // AUTO | UNICODE | RAW_BYTES | GS1
    val eci: Int?,           // null = unset/auto
)
```

### 3.3 Encode pipeline

```
Payload + SymbologySpec + EncodeOptions
        │
        ▼
  validation (charset rule → length rule → trial encode)
        │
        ▼
  JNI ──▶ libzint ZBarcode_Encode
        │
        ▼
  ModuleMatrix (format-agnostic bit grid + HRT + quiet zone metadata)
        │
        ├──▶ PngRenderer / WebpRenderer / JpegRenderer
        ├──▶ SvgRenderer   (resolution independent)
        └──▶ PdfRenderer   (android.graphics.pdf.PdfDocument)
```

The JNI layer returns zint's raw module matrix rather than letting zint rasterize. One renderer set then serves screen, export, and PDF, guaranteeing identical geometry across all outputs. This mirrors the one genuinely good structural idea in the app we reverse-engineered.

Validation is three-stage, because regex alone cannot catch check-digit failures:

1. Charset rule — fast, drives inline field errors as the user types.
2. Length rule — per symbology, accounting for the active input mode.
3. Trial encode — call libzint and surface its actual error/warning string.

## 4. Feature 1 — Generator

### 4.1 Payload composer

The differentiating piece, and the answer to "inject characters that are challenging to input with an Android keyboard".

A text field plus an insert palette presented as a bottom sheet with tabs:

- Control characters: a tappable grid of ASCII 0–31 and 127, each labelled with mnemonic and hex (NUL, SOH, STX, ETX, EOT, ENQ, ACK, BEL, BS, HT, LF, VT, FF, CR, SO, SI, DLE, DC1–DC4, NAK, SYN, ETB, CAN, EM, SUB, ESC, FS, GS, RS, US, DEL). GS is the one people actually need constantly for GS1 data and is hard to type on Android.
- Function characters: FNC1–FNC4, plus Code 128 codeset switches `\^A` `\^B` `\^C` and `\^@`, shown only for symbologies that support them.
- GS1 Application Identifiers: common AIs with FNC1 separators inserted automatically and correctly.
- Byte entry: type a hex or decimal value, inserts the corresponding `\xNN`.
- Unicode entry: type a codepoint, inserts `\uNNNN`.
- High ASCII: a grid for 128–255.

Supporting controls:

- Field view toggle between rendered mode (control characters shown as inline chips such as `⟨GS⟩`) and raw escape mode (literal `\x1D`), so the user can work whichever way they prefer and always see exactly what is there.
- A byte inspector panel showing the precise byte sequence that will be encoded, in hex and annotated ASCII. This is the trust-building element — a professional needs to confirm what actually goes into the symbol.
- Input mode selector (Auto / Unicode / Raw bytes / GS1) and ECI selector, both filtered by what the selected symbology supports.

### 4.2 Full-screen viewer

Requirement: large barcodes must be viewable full screen with rotation.

- Full-screen, maximum-contrast presentation with system bars hidden.
- Orientation unlocked, plus a manual 90° rotate control. Long linear codes are far wider than tall, so the viewer auto-suggests landscape when the symbol's aspect ratio exceeds a threshold.
- Pinch zoom and pan, with a fit-to-screen reset.
- Screen brightness temporarily raised to maximum and screen-on held, which materially improves the odds of a hardware scanner reading off the display.
- Toggles for quiet zone and human-readable text.

### 4.3 Export

Formats: PNG, SVG, PDF, WEBP, JPEG. Options: module size in pixels, target DPI, quiet zone width, foreground/background colour, HRT on/off, rotation. Output via SAF (save anywhere) or the share sheet. No storage permission required.

### 4.4 Batch generation from a wordlist

Import a `.txt` (one payload per line) or `.csv` (optional columns for symbology and label).

A preview and validation pass runs first, listing exactly which lines will fail and why, before anything is generated. Outputs, per your selection:

- A ZIP of individual PNG and/or SVG files, named from the payload or a sequence index.
- A printable PDF sheet: a grid of symbols with human-readable captions, sized for physical label/test sheets.
- Direct save of every generated symbol into a named catalogue library.

Long batches run on a background coroutine with progress reporting and cancellation.

## 5. Feature 2 — Reader

CameraX `Preview` plus `ImageAnalysis` feeding zxing-cpp, covering the same wide symbology set as the generator.

- Per-symbology enable/disable, because restricting the format set measurably speeds up detection.
- Torch, tap-to-focus, pinch zoom.
- Single-shot and continuous/batch modes.
- A stability gate carried over from the prior-art analysis: 1D symbols are read off a single scan line and are materially more error-prone per frame than 2D, so a linear result must be seen twice consistently within a short window before it is accepted, while matrix codes are accepted immediately. Session-level duplicate suppression and a debounce window sit on top.
- Decode from an existing image file as well, with rotation and inversion retries for awkward captures.
- Results show decoded text, symbology, and a raw byte view — essential when a QR contains binary or mixed-encoding data that a naive text view would mangle.
- One tap to save a result into a chosen library.

## 6. Feature 3 — Catalogue

Multiple user-created named libraries, each holding entries from any source.

An entry records: payload bytes, input mode, ECI, symbology, encode options, label, notes, tags, source (`GENERATED` / `SCANNED` / `IMPORTED` / `CONFIG_PACK`), and creation time. Because options are stored, any entry can be re-rendered at any size or format later without loss.

- Browse: list or grid with rendered thumbnails, full-text search, filters by symbology / source / tag, and multiple sort orders.
- Detail: full-screen view, a value inspector showing both decoded text and hex bytes, re-generate with different options, export, move or copy between libraries, edit metadata.
- Library operations: create, rename, delete, reorder; export a library as JSON plus an images ZIP; import a previously exported library.

Backup format follows the versioned-envelope pattern (schema version, timestamp, app version, item count, SHA-256 checksum verified before any write, dedup-on-import by content fingerprint, cancellable) — a design worth carrying forward wholesale from the analysed app, with the fingerprint algorithm itself versioned so future field additions do not silently break deduplication.

## 7. Feature 4 — Device configuration barcodes

Programming barcodes that reconfigure real scanner hardware, organised per vendor and purpose.

### 7.1 Pack format

Bundled packs live in assets; user packs live in app storage and are imported through the same validated schema.

```
assets/configpacks/
  zebra/
    manifest.json
    defaults/            ← restore-to-factory, surfaced first
    scan-modes/
    data-formatting/
    interfaces/
    symbology-enable/
  honeywell/
  datalogic/
```

```json
{
  "name": "Enable Code 128",
  "description": "Turns on Code 128 decoding.",
  "symbology": "CODE_128",
  "data": "...",
  "provenance": "vendor manual reference",
  "warning": null,
  "destructive": false
}
```

Symbols are generated on demand by the same libzint pipeline. No pre-rendered images ship in the app, so every entry stays re-renderable at any size.

### 7.2 Search and navigation

Vendor → category folder → optional subfolder → entry, with full-text search across vendor, category, name, description, and data, backed by a Room FTS table. This matters because vendors like Zebra document a very large parameter space; hierarchy alone is not enough to find anything.

### 7.3 Safety design

These codes change hardware state, and an incorrect parameter string can leave a device misconfigured or hard to recover. Mitigations are part of the design, not an afterthought:

- Every entry carries a provenance field, displayed in the UI, so the user can cross-check against their own vendor manual before scanning.
- Entries that reset, change interface, or otherwise disrupt a device are flagged `destructive` and require an explicit confirmation tap before the scannable symbol is displayed.
- A vendor's restore-to-defaults entry is surfaced prominently, so recovery is always one step away.
- Bundled seed data is deliberately small and limited to values I can source from public documentation, each marked with its provenance. Bulk-populating unverified parameter strings would be actively harmful, so the importer exists for you to add vendor data you have verified.

## 8. Data layer

Room, with real versioned migrations from v1 and no destructive fallback.

- `libraries` — id, name, created_at, sort_order
- `entries` — id, library_id, symbology_id, payload BLOB, payload_mode, eci, options JSON, label, notes, source, created_at
- `tags`, `entry_tags`
- `config_packs`, `config_entries`, plus an FTS table over config entries

Payloads are `BLOB`, deliberately, so control characters and binary content survive storage intact.

## 9. Technology choices

- Kotlin, Jetpack Compose, Material 3
- minSdk 26, targetSdk 36. The floor is set at 26 rather than higher because rugged industrial devices — precisely the hardware feature 4 targets — frequently run older Android builds.
- Navigation Compose with type-safe routes
- Hilt for DI, coroutines and Flow for async
- Room for persistence
- CameraX for camera
- PDF via the platform's `android.graphics.pdf.PdfDocument`, avoiding a third-party PDF dependency and its licensing
- ABI splits / App Bundle to offset the native payload

## 10. Native build

- libzint vendored as a pinned source drop or git submodule, built by CMake through `externalNativeBuild`.
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64` (the last for emulator use).
- The JNI surface stays deliberately narrow:
  - encode: symbology id, payload bytes, input mode, ECI, options → module matrix, HRT string, or a structured error
  - capability query via `ZBarcode_Cap`, so the registry can be cross-checked against the linked library version at runtime
  - zint error and warning strings passed through verbatim (`errtxt`, 160 bytes) rather than collapsed into a boolean

The module matrix comes from the `zint_symbol` struct's `encoded_data[200][144]` bit-packed array together with `rows` and `width`; module (x, y) is bit `x & 7` of `encoded_data[y][x >> 3]`. That array bounds a symbol at 200 rows by 1152 modules, which comfortably covers every launch symbology at maximum capacity (QR caps at 177 modules, Data Matrix at 144, PDF417 at 90 rows). The spike will confirm this holds at max data for the widest formats; if any symbology exceeds it, that format falls back to zint's own vector output path rather than the raw matrix.

## 11. Verification strategy without a device

Since device testing is out of scope for now, correctness leans on host-side and structural tests:

- Round-trip property tests: encode a payload with libzint, decode it with zxing-cpp, assert the bytes come back identical. This validates both engines against each other and is the single highest-value test in the project. Requires host (Linux x86_64) builds of both native libraries for JVM test execution.
- Golden-matrix tests: assert our JNI encode produces module matrices identical to the reference `zint` CLI for a fixture corpus, which pins geometry and escape handling.
- Unit tests on the pure-Kotlin layers: symbology registry invariants, charset and length rules, escape parsing and byte assembly, GS1 AI insertion, fingerprinting, backup checksum and dedup logic.
- Compose UI tests under Robolectric for the composer, viewer, and catalogue screens.
- A manifest assertion in CI that no `INTERNET` permission is present, so the privacy guarantee cannot regress.

The camera capture path itself is the one area that genuinely needs hardware; it will be structured so the decode logic is independently testable from fixture images, leaving only CameraX binding unverified until you run it.

## 12. Phase 0 verification record

Toolchain installed at `~/Android/Sdk` (2.9 GB):

- NDK 28.2.13676358 (clang 19), CMake 3.31.6, build-tools 36.0.0, platform android-36, platform-tools 37.0.1
- JDK 21 already present. Gradle will come from the project wrapper, so no system Gradle is needed.
- SDK licences were accepted non-interactively as part of the authorised install.

libzint 2.16.0.9 builds cleanly from source (host x86_64 build, as a proxy for the NDK build) producing both `libzint.so` and the reference `zint` CLI. PNG and zlib are optional and were not required; the Android build will disable them along with the CLI and Qt frontends, since we render from the module matrix ourselves.

Each capability that feature 1 depends on was then exercised against the built library using `--dump`, which emits a deterministic hex representation of the symbol:

- Escape processing: `AB\x1DCD` with `--esc` produces different output from the literal string, confirming the embedded GS character is encoded rather than taken literally.
- Code 128 manual codeset switching: `\^A001\^BABC` with `--extraesc` encodes successfully.
- FNC1 insertion: `\^1` produces the same leading module pattern as native GS1-128 mode, independently confirming it emits a true FNC1.
- GS1-128 with bracketed AIs, and QR with `--eci=26`, both encode as expected.
- Raw binary mode encodes `\x00\x01\xFF\xFE`, including the null byte — the case that would be silently truncated by any `String`-based API, and the reason the payload type is `ByteArray`.

What this does and does not establish: it proves the library itself provides every required capability and that the escape and input-mode semantics behave as documented. It does not yet prove the JNI binding or the Android cross-compile, both of which remain the Phase 1 spike. The JNI layer passes the same `input_mode` flag combinations, so the risk carried into Phase 1 is build and binding mechanics rather than library capability.

`--dump` also settles the golden-test approach: reference matrices can be captured as hex text from the CLI and compared byte-for-byte against JNI output, with no image comparison involved.

## 13. Phase 1 spike results

Both spikes are complete. Code lives in `workbench/barcode-zint/`.

### 13.1 libzint JNI encode — verified

The JNI layer (`src/main/cpp/zint_jni.c`, ~250 lines) encodes a byte payload and returns a module matrix, per-row heights, HRT, and zint's verbatim diagnostics. libzint's backend is compiled as a PIC static library and linked into one shared object per ABI.

Build outcomes, all three ABIs, `android-26`:

- arm64-v8a 869 KB stripped, armeabi-v7a 687 KB, x86_64 949 KB
- Dependencies are platform-only (`liblog`, `libm`, `libdl`, `libc`); no PNG or zlib linkage, confirming the trim worked
- All five JNI symbols exported

This corrects the earlier estimate in section 9: the native payload is roughly 870 KB per ABI, not 3–6 MB. With ABI splits a user downloads one.

Correctness was established by reproducing libzint's own `--dump` format from the returned matrix and diffing against the reference CLI. All 16 fixture cases are byte-identical across 165 lines, covering Code 128 (baseline, embedded GS, `\^A`/`\^B` codeset switching, `\^1` FNC1), GS1-128 with bracketed AIs, QR with ECI 26, Data Matrix in binary mode with NUL and high bytes, EAN-13, PDF417, Aztec, MaxiCode, DotCode, Micro QR, rMQR, and DataBar Omnidirectional.

Additional self-checks that now pass: every symbology constant is asserted against `ZBarcode_BarcodeName`; capability flags are asserted against `ZBarcode_Cap`; a payload containing a NUL byte is confirmed to encode distinctly from its truncation; and an invalid EAN-13 returns a structured error with zint's own message text.

Two real defects surfaced, which is what the spike was for:

- `BARCODE_EAN13` is 15, not 11. The value 11 is `BARCODE_EAN_2ADDON`, so a "12 digits too long, maximum 2" error appeared instead of an EAN-13. This is exactly the registry drift section 3.1 predicts, and it is now caught automatically by the name assertion rather than by inspection.
- `ZBarcode_Create` callocs the symbol struct, so zint's "auto" default for `option_1/2/3` is 0. Passing −1 as an "unset" sentinel was taken literally and rejected by PDF417, Aztec, and rMQR as an invalid row count or version. The JNI layer now treats a negative option as "leave zint's default untouched".

### 13.2 zxing-cpp reader — verified, with one asymmetry

`io.github.zxing-cpp:android:3.1.1` from Maven Central: 2.9 MB, prebuilt `.so` for arm64-v8a, armeabi-v7a, x86, and x86_64, `minSdkVersion 21`.

The AAR exposes `BarcodeReader` only. There is no writer class of any kind. This settles the architecture question definitively: the alternative of using zxing-cpp for both directions was not viable, and the libzint JNI path is required rather than merely preferable.

Read formats confirmed present: DataBar (omni, stacked, stacked-omni, limited, expanded, expanded-stacked), DX Film Edge, Telepen (alpha and numeric), PDF417 with compact and micro variants, Aztec including Rune, QR with model 1/2, Micro QR, rMQR, Data Matrix, MaxiCode, Code 32, PZN, plus the usual linear set and convenience groups such as `ALL_LINEAR`, `ALL_MATRIX`, `ALL_GS1`, `ALL_RETAIL`.

One gap worth planning around: DotCode is absent from the reader's format list. The app can generate DotCode but cannot scan it. Any symbology in that position must be marked read-unsupported in the registry (the `readerFormat` field already models this as nullable) and the scanner UI must not offer it as a toggle.

The reader API materially simplifies two things in the plan:

- `Options` includes `tryHarder`, `tryRotate`, `tryInvert`, `tryDownscale`, `tryDenoise`, `binarizer`, `minLineCount`, `maxNumberOfSymbols`, and `validateOptionalChecksum`. The rotation and inversion retry loop described in section 5 for file decoding does not need hand-rolling; it is configuration. The cross-frame stability gate is still worth keeping, since `minLineCount` constrains scanlines within a single image rather than agreement across frames.
- `Result` exposes `bytes` alongside `text`, plus `contentType` (TEXT/BINARY/MIXED/GS1/ISO15434/UNKNOWN_ECI), `symbologyIdentifier`, `ecLevel`, `position`, `orientation`, `lineCount`, `readerInit`, and structured-append `sequenceId`/`sequenceIndex`/`sequenceSize`. A `TextMode` of `HEX` or `ESCAPED` is available directly. That is the entire professional result inspector from section 5, available without extra work, and `readerInit` is directly relevant to feature 4's programming barcodes.

Entry points are `read(ImageProxy)` for the CameraX path and `read(Bitmap, Rect, Int)` for file decoding with a crop region and rotation.

### 13.3 What remains unproven

Neither spike executed on Android. The encode path is verified against the reference implementation on the host JVM, and the Android artifacts are confirmed to build with correct architectures and dependencies, but actual on-device execution of both the JNI binding and CameraX capture is still untested. That is unchanged from the section 11 strategy and is the one area genuinely needing hardware.

## 14. Phase 2 toolchain decision record

The scaffold started on AGP 8.13.2 with Gradle 8.14.5, chosen deliberately as the mature, battle-tested option over AGP 9. That turned out to be the wrong trade, and the reversal is worth recording because the reasoning generalises.

Three independent dependencies rejected AGP 8:

- Hilt 2.60.1 refuses to apply below AGP 9.0.0 outright.
- `androidx.core:core-ktx:1.19.0` requires compiling against API 37 and AGP 9.1.0 or higher.
- `androidx.lifecycle:lifecycle-runtime-compose:2.11.0` likewise requires AGP 9.1.0 or higher.

The options were to downgrade Hilt plus roughly five AndroidX libraries to accommodate an older AGP, or to move the build forward. Staying behind would have meant every dependency bump fighting the toolchain, and AGP 8 was already emitting upgrade advisories against compileSdk 36. The pinned combination is now:

- AGP 9.3.1, Gradle 9.6.1, Kotlin 2.2.21 with KSP 2.2.21-2.0.5 (a matched pair)
- compileSdk 37, targetSdk 37, minSdk 26
- Hilt 2.60.1, Room 2.8.4, Compose BOM 2026.06.01

minSdk stays at 26 regardless of the AGP move, for the reason given in section 9: rugged industrial scanners are exactly the hardware feature 4 exists to serve, and they lag on Android versions.

The general lesson: "pick the older, safer version" is not automatically the conservative choice. When the surrounding ecosystem has already moved, lagging behind is itself the risk, and the cost shows up as version-conflict archaeology rather than as a clean failure.

A second, smaller correction: several library versions were initially written from memory and did not exist (`truth:1.4.6`, `robolectric:4.16`, `hilt-navigation-compose:1.3.0`). Every version in the catalogue is now taken from the published Maven metadata, filtered to exclude prereleases. This is the same discipline the `BARCODE_EAN13` defect taught in Phase 1, applied to build configuration.

## 15. Phase 2 outcome

The scaffold builds. Project root is `workbench/`.

Verified state:

- `./gradlew assembleDebug` produces three per-ABI debug APKs (arm64-v8a 23.6 MB, armeabi-v7a 22.8 MB, x86_64 23.7 MB, unminified debug builds).
- Both engines are present in the APK: `libbarcode_zint.so` (1.0 MB, our JNI encoder) and `libzxingcpp_android.so` (1.7 MB, the reader).
- `./gradlew test` runs 21 unit tests across three modules with no failures: 13 registry invariants in `core:model`, 5 native-constant checks in `barcode:zint`, 3 manifest assertions in `app`.
- The merged manifest declares exactly two permissions, `CAMERA` and `VIBRATE`. No `INTERNET` survives manifest merging from any dependency, so the no-network guarantee holds in the built artifact and not merely in our own manifest.
- `tools/verify-zint-goldens.sh` passes end to end, still reporting all 16 module matrices byte-identical to the reference and all native self-checks green.

Twelve modules are in place: `app`, `core:model`, `core:database`, `core:designsystem`, `barcode:engine-api`, `barcode:zint`, `barcode:reader`, `barcode:render`, and the four feature modules. Feature modules depend on `barcode:engine-api` only; the two concrete engines are named in exactly one Hilt binding each (`EncoderModule`, `ReaderModule`), so replacing either is a one-file change.

`core:model` carries the real domain layer rather than placeholders: the 26-entry symbology registry, `CharsetRule` and `LengthRule` with per-code-point membership testing so the composer palette can grey out characters a symbology cannot encode, `Payload` as bytes with hex and escaped-ASCII renderings, and `ModuleMatrix` including the reference-dump method that makes byte-level comparison possible.

Room schema v1 covers libraries, entries and tags, with the payload stored as a `BLOB`. Config-pack tables are deliberately deferred to Phase 6 so that path exercises a genuine versioned migration rather than arriving pre-baked.

### Defects found and fixed during scaffolding

- The zint module's Kotlin sat in `dev.barcodeworkbench.barcode.zint` while its Java sat in `dev.barcodeworkbench.zint`, so the encoder could not see `ZintNative`. The Java package cannot move, because JNI symbol names encode it, so the Kotlin moved instead.
- `BarcodeReader.Options.formats` is a Kotlin property, not a `setFormats` method, and `read` requires a non-null crop `Rect`. Both were written from assumption and corrected against the decompiled AAR signatures.
- The window theme referenced `android:Theme.Material.DayNight.NoActionBar`, which does not exist. Framework DayNight themes arrived in API 29 and minSdk is 26, so light and dark are split across `values/` and `values-night/`.

### Deferred

`barcode:render` exists as a module but is still empty; the renderers land in Phase 3 alongside the generator that needs them. `feature:*` screens are placeholders that make the navigation graph complete and traversable.

## 16. Phase 3 progress

Domain, render and the generator screen are in. 93 unit tests pass, up from 21.

### Render layer

`barcode:render` is built around `SymbologyGeometry`, computed once and consumed by all three renderers, which is what guarantees a PNG, an SVG and a PDF of the same symbol share identical geometry instead of three near-miss implementations drifting apart.

Two decisions worth noting:

- `RenderSpec` takes pixels per module rather than an overall target size. A barcode is only reliably scannable when modules land on whole pixels, and accepting "400px wide" invites fractional module widths that blur edges and defeat decoders.
- Row heights accumulate in float space and round only at row boundaries. Rounding each row independently lets error accumulate, leaving visible seams between rows of a stacked symbology or a symbol taller than its canvas. A test asserts rows are contiguous and sum exactly to the total.

Anti-aliasing is off for modules deliberately; softened edges are precisely what makes thresholding harder for a scanner. SVG merges horizontal runs into single rects, collapsing a linear barcode from hundreds of elements to a few dozen. PDF uses the platform's own `PdfDocument`, avoiding a third-party PDF dependency and its licensing.

### The character-insert mechanism

This is the part that answers the requirement to encode every character a symbology supports, which no soft keyboard can reach.

`EscapeCodec` expands escape sequences independently of libzint, supporting the same set the encoder does: hex, decimal, octal and Unicode byte escapes, the simple control escapes, Code 128 codeset directives and FNC markers. Implementing expansion here rather than delegating to the encoder is deliberate: the byte inspector must show which bytes the input will become *before* encoding is attempted, including while the payload is still invalid, and round-tripping through the encoder to find that out would be both slower and useless in the failure case.

The parser distinguishes data bytes from instructions. A codeset switch or FNC1 changes how the encoder reads what follows but contributes no byte, so the inspector lists them separately rather than folding them into the hex dump and misrepresenting the data stream. Errors are collected rather than stopping at the first, so all problems surface at once.

`CharacterPalette` gives every unreachable value a labelled key: all 33 ASCII control characters with mnemonics and names, the codeset and function directives, common GS1 Application Identifiers with their fixed-or-variable-length rules, and bytes 0x80 to 0xFF. Categories the selected symbology cannot use are omitted entirely rather than shown disabled, since offering an FNC1 key on a QR code implies it does something. Individual keys whose byte the format cannot encode are dimmed, so the palette teaches the format's limits rather than silently allowing an invalid payload.

Tests assert that every palette key produces valid escape source and yields exactly the byte it advertises, and that all 256 byte values survive a `toEscapeSource` round trip.

### Validation

`PayloadValidator` covers the two checks that can run on every keystroke, and deliberately does not try to be authoritative: only a trial encode settles check digits and mode-dependent capacity. A fast, specific message per keystroke is worth more than a slow complete one, and the generator runs the trial encode before allowing a save.

Two subtleties the tests pin down: in Unicode mode the UTF-8 bytes are decoded back to code points before checking the charset rule, because testing raw bytes would reject every non-ASCII character; and a malformed escape suppresses the charset and length messages, since reporting "unsupported character" on top of "malformed escape" buries the real problem.

### Generator screen

Validation and encoding are debounced at 180 ms rather than run per keystroke, and stale results are discarded by comparing inputs on completion. The symbology dropdown groups 26 formats by category and marks generate-only ones. The preview sits on a fixed white field regardless of theme, because a barcode needs true black-on-white contrast and tinting it for dark mode works against the scanner.

### Full-screen viewer

Lives in `core:designsystem` because the catalogue needs the same component in Phase 5. Its behaviour is shaped by the actual use case, which is a hardware scanner or second phone reading the symbol off the glass: brightness is forced to maximum and restored on exit, the screen is kept awake, and the background is true white regardless of theme, since a dimmed or dark-themed barcode is measurably harder to decode.

A symbol whose aspect ratio exceeds 2.5:1 opens already rotated, because portrait screens are the common case and a long linear barcode otherwise shrinks to illegibility. Manual quarter-turn rotation, pinch zoom to 8x with pan, and toggles for quiet zone and caption are all available; panning snaps back at 1x so the symbol cannot be stranded off-screen.

### Export

`SymbolExporter` covers PNG, SVG, PDF, WebP and JPEG, all routed through the same geometry so changing format changes the container and not the barcode. WebP uses the lossless encoder where the platform supports it. JPEG is offered because some systems demand it but is explicitly flagged in the UI as lossy, since its artefacts land on exactly the high-contrast module edges a decoder depends on; selecting it also forces a white background, because JPEG cannot store transparency and would otherwise render the symbol inverted.

Writing goes through the Storage Access Framework, so the user picks the destination and the app still needs no storage permission.

### Batch generation

The flow is import, then preview, then produce. Every row is encoded before anything is written, and failures are itemised with their source line numbers. Discovering on row 900 of 1000 that a payload was invalid, after a file already exists, is the outcome that ordering prevents.

`WordlistParser` accepts plain text (one payload per line) or CSV with optional symbology and label columns, which allows a mixed-format test set from one file. Blank lines and `#` comments are skipped, line numbers track the source file rather than the entry index, and an unrecognised symbology name is reported rather than silently defaulted, since quietly falling back would produce a batch of wrong barcodes with no indication anything went wrong. Escape sequences pass through verbatim for the encoder to expand.

All three outputs the requirement asked for are implemented: a ZIP of individual PNG or SVG files, named with zero-padded line numbers so the archive sorts in wordlist order and de-duplicated where distinct payloads normalise to the same safe filename; a paginated printable PDF sheet with captions; and direct save into a named catalogue library, creating it if absent. Long batches report progress and honour cancellation between rows.

The library writer talks to the DAOs directly for now and will move behind the catalogue repository in Phase 5. It stores the escape source rather than the expanded bytes, so entries remain editable and re-render exactly as authored, meaning no data migration is needed when that move happens.

### Phase 3 verified state

- `./gradlew assembleDebug test` green; 109 unit tests, no failures (57 in `core:model`, 28 in `barcode:render`, 16 in `feature:generator`, 5 in `barcode:zint`, 3 in `app`).
- `tools/verify-zint-goldens.sh` still passes: 16 matrices byte-identical to the reference, all native self-checks green.

Deferred deliberately: ECI selection is modelled end to end but has no UI control yet, and the raw-versus-rendered escape view toggle exists in state but is not yet surfaced. Both are small additions best made alongside the catalogue's entry editor, which needs the same controls.

## 17. Phase 4 outcome

The scanner is built. 133 unit tests pass, 22 of them covering the stability gate.

### Stability gate

`ScanStabilityGate` carries forward the one genuinely good idea from the app we reverse-engineered, and the reasoning is worth restating because it is not obvious: a 2D symbol spreads error correction across an area, so one clean decode is already strong evidence, whereas a 1D symbol is read along a single scan line where a smudge, fold or motion blur can yield a checksum-valid but wrong value from a single frame. Linear results therefore need two agreeing sightings inside an 800 ms window; matrix results are accepted immediately. An unrecognised format is treated as linear, the cautious side, because requiring confirmation costs a moment while accepting a misread gives no signal at all.

Keys are built from raw bytes rather than decoded text, so two payloads that render alike but differ in their bytes are never conflated.

Writing the tests exposed a real design flaw. With session de-duplication always on, the debounce filter was unreachable: any repeat was caught by the session check first, and `resetSession` cleared the debounce state too. Rather than delete it, the redundancy pointed at a missing capability — a counting workflow where the same barcode is deliberately scanned repeatedly. `allowDuplicates` now switches session de-duplication off and the debounce becomes load-bearing, which makes both filters meaningful and gave the tests a clean way to exercise the debounce without the reflection hack the first draft needed.

### Camera pipeline

`CameraFrameDecoder` is a second interface alongside `BarcodeDecoder`, and adding it meant putting CameraX on `barcode:engine-api`, revising the position taken in section 5. The justification: letting zxing-cpp read an `ImageProxy` natively avoids a YUV-to-bitmap conversion on every preview frame, and the abstraction that actually matters is which decode engine is in use, not which camera library. Features still depend on interfaces only.

The split also lets the two paths carry appropriate defaults. `forLiveFrames` disables every retry, because a fresh frame arrives sooner than a retry would pay off; `forStillImage` enables all of them, because there is no next frame and it is worth working hard on the one there is. Frame ownership is explicit: the decoder never closes the `ImageProxy`, and the analyser closes it exactly once, since failing to would starve the buffer pool and manifest as the preview silently freezing.

Torch is applied through camera control on the existing binding rather than by rebinding use cases, which would restart the capture session and visibly stutter the preview.

### Result inspector

The sheet offers text, escaped and hex views of the payload, and defaults to hex when the engine reports binary content, because rendering binary as text misrepresents it from the first glance. It surfaces the metadata zxing-cpp provides for free: content type, error-correction level, AIM symbology identifier, orientation, and structured-append part numbering.

It also calls out the reader-initialisation flag explicitly. A symbol carrying that flag is a device programming barcode, and knowing that before acting on it matters, which ties directly into feature 4.

### A permission regression the tests nearly missed

Adding CameraX pulled in `androidx.media3-common`, which declares `ACCESS_NETWORK_STATE` for streaming features this app does not use. It appeared in the merged manifest and the existing test did not catch it, because that test checked the merged manifest for `INTERNET` alone.

Both halves are fixed. The permission is stripped with `tools:node="remove"`, and the test now asserts against the full list of network permissions in the merged manifest plus an exact-match assertion that the app declares nothing beyond `CAMERA` and `VIBRATE` — so any future dependency introducing any permission fails the build rather than shipping quietly. A companion test asserts the removal directive itself stays in place.

The source-manifest check also had to learn to distinguish a declaration from a removal, since the manifest now names the permission precisely in order to strip it and a naive string search read that as a declaration.

This is the clearest evidence so far that the no-network guarantee needed to be structural. Policy alone would have missed it.

### Deferred

Saving a scan into a catalogue library is wired as far as the ViewModel but has no destination picker yet; it lands with the catalogue in Phase 5, which is also where library selection belongs. Per-symbology enable toggles exist in state and are honoured by the decoder but have no settings UI yet.

## 18. Phase 5 outcome

The catalogue is built. 172 unit tests pass, up from 133.

### Repository

`CodeRepository` lives in `core:model` as an interface; `RoomCodeRepository` implements it in `core:database`, bound in one Hilt module. Reads return `Flow` from live queries, so no screen has to remember to refresh after a write.

One point needed care, because it is exactly the flaw criticised in section 2 of the reverse-engineering write-up. Library scoping and time ordering are pushed into SQL. Text search runs in memory over the scoped rows, and that is a deliberate, bounded compromise rather than the prior app's global cache: the payload is a `BLOB` and search deliberately matches its *escaped* rendering, so looking for `\x1D` finds codes containing a Group Separator. No `LIKE` can do that. The cost is one library rather than the whole database, and if libraries ever grow large enough to matter the fix is a stored searchable projection with an FTS index, not a wider cache.

Tag loading avoids the obvious N+1: a listing joins entry-to-tag pairs in a second query and combines the two flows, so it stays at two queries regardless of entry count. Library counts come from a single correlated subquery for the same reason.

Rows whose stored symbology no longer resolves are dropped from listings rather than crashing the screen, and the underlying data is left untouched so a later release can still read it.

### Backup format

`BackupCodec` sits in `core:model` with no IO, which keeps the interesting rules host-testable — 22 of the new tests cover it. `BackupManager` in the feature module adds gzip framing and database work.

Payloads are base64 in the file, not JSON strings. That is not incidental: control characters, embedded NUL and invalid UTF-8 sequences are all legitimate payload content and would be corrupted or make the file unparseable as text. A test round-trips all 256 byte values plus a deliberately awkward sequence.

The envelope versions the schema and the fingerprint algorithm separately. If a later release adds a field to the de-duplication fingerprint, old backups must still de-duplicate against the algorithm they were written with; bumping only the schema version would silently change what counts as a duplicate.

Import is two steps. The file is parsed, checksum-verified and planned before anything is written, so a damaged file cannot half-populate a library and the user sees the counts first. Fingerprints accumulate while planning, so duplicates *within* one file are caught as well as against existing data. An entry naming a symbology this build does not have is skipped rather than failing the whole import, since a backup from a newer release should still be mostly usable.

Fingerprints use a length-prefixed encoding so fields cannot bleed together: label "a" with notes "bc" must not hash the same as label "ab" with notes "c". There is a test for precisely that.

### Deferrals from earlier phases, now closed

- `BatchLibraryWriter` moved off the DAOs and onto the repository. No data migration was needed, because Phase 3 stored the authored escape source rather than expanded bytes.
- Saving a scan to a library is wired end to end. The payload is stored as raw bytes in binary mode, because a scanned symbol's content is whatever bytes it carried and re-interpreting it as text on the way in would corrupt binary and mixed-encoding payloads.
- Entry detail re-encodes the stored payload rather than loading an image, which is what makes an entry re-renderable at any size later. It reports a missing preview rather than hiding the entry if the payload no longer encodes.

### Still open

Editing an entry's label, notes and tags is modelled and supported by the repository but has no form yet. Per-symbology scanner toggles and ECI selection remain without UI. Library reordering is in the schema but not exposed.

## 19. Phase 6 outcome

Device configuration is built. 190 unit tests pass, up from 172.

### What is deliberately not shipped

The vendor packs for Zebra, Honeywell and Datalogic ship with zero parameter entries, and this is the most important decision in the phase.

Programming barcodes reconfigure real hardware. The correct parameter string differs between vendors, product families and sometimes firmware revisions, and a wrong one can leave a scanner misconfigured or awkward to recover. I do not have vendor parameter strings I can verify to that standard, and producing plausible-looking ones would have been worse than shipping nothing: they would look exactly as authoritative as correct data while being untrustworthy, and the user would have no way to tell the difference.

So what ships is the format, the hierarchy, the search, the safety handling and an importer, plus documentation in `assets/configpacks/README.md` explaining how to author a pack from the reference guide for a specific model. The empty-vendor screen states the reason plainly rather than looking like a loading failure.

A test asserts vendor packs contain no entry that is not verified, so this position cannot erode quietly.

### What is shipped

A `selftest` pack of 18 ordinary data barcodes, which carry no commands and cannot change any device setting. It is genuinely useful to a technician checking what a scanner decodes, and two entries earn their place beyond simple coverage:

- Code 128 containing a Group Separator, for checking whether a scanner transmits GS or silently drops it, which is the usual cause of GS1 parsing failures downstream.
- QR containing bytes 0x00 to 0x04, for checking whether binary content passes through intact or truncates at the NUL.

Every payload is validated by test against its own symbology's rules, so a bundled entry that could not render is caught at build time rather than failing silently at runtime.

### Safety design

Verification status is a required field on every entry, not optional metadata, with four values: verified against primary documentation, community reported, example only, unspecified. It is shown on the entry and marked on the browse row, so caution is visible while scrolling rather than only after tapping through.

Anything destructive *or* not verified against primary documentation is gated: the symbol is not rendered until the user acknowledges a specific explanation of why. Gating unverified entries alongside destructive ones is deliberate, because a wrong parameter string and a disruptive one leave the user in the same place.

Provenance is mandatory and shown above the symbol rather than tucked away, because cross-checking against the manual is the only real defence against a wrong value. A pack with a blank provenance field is rejected on import.

Restore-to-defaults entries are surfaced in a Recovery section ahead of the folder tree, so getting back to a known state is never buried.

### Migration

Schema v2 adds `config_entries` and an FTS4 index. The migration SQL is copied verbatim from Room's exported `2.json` rather than written by hand, because Room validates the resulting schema against an identity hash at runtime and a hand-written approximation of a virtual table is exactly the kind of thing that looks right and is not.

The migration also creates the three FTS synchronisation triggers explicitly. Room generates those automatically for a fresh database but not for a migration, and without them search returns stale results with no error. An instrumented test asserts a v1 database carrying a payload with NUL and GS bytes survives the upgrade byte-for-byte, and that FTS search works afterwards.

### Search

Free text is sanitised into an FTS4 prefix expression, with everything outside letters, digits and underscore treated as a separator. This is not cosmetic: FTS4 reads quotes, hyphens, asterisks and parentheses as syntax, and an unbalanced one makes SQLite throw. Since the query runs on every keystroke, an unsanitised character would surface as the screen crashing mid-word. Seven tests cover it.

Bundled packs are re-read from assets on every launch rather than seeded once, so correcting an entry in a release actually reaches existing installs instead of being shadowed by whatever was written on first run.

## 20. UI gap closure

The deferrals accumulated across phases 3 to 6 are now closed. 197 unit tests pass.

### Entry metadata editing

Label, notes and tags are editable in the detail sheet. The payload and symbology are deliberately not: changing them would make it a different code, the generator is the place to author one, and silently mutating a saved payload would invalidate any label or note describing it.

Tags are entered as comma-separated text rather than through a chip-adding widget. For a handful of short tags that is faster to type and to correct, and it makes a bulk edit one gesture.

### Library management

Rename, delete and reorder are reachable by tapping an already-selected library chip a second time, which avoids introducing a long-press gesture the app uses nowhere else.

Reordering persists the full ordered list rather than a single move, so the resulting order is always internally consistent; applying moves individually can leave duplicate or gapped sort values if a write fails partway.

Deleting a library asks for confirmation only when it holds entries. An empty one goes immediately, because there is nothing to lose and a dialog would be friction for the common case of fixing a mistyped name. The confirmation names the entry count and suggests exporting a backup first.

### Scanner symbology toggles

Grouped by category so the user reasons about "retail" or "GS1" rather than scanning a flat list of 23 names. Only readable formats appear, so a toggle that could never match anything is impossible by construction. The scanner screen already reports per-frame decode time, which makes the speed trade visible rather than theoretical.

### ECI selection

Exposed for symbologies that support it, with the common assignments named rather than presented as bare numbers. This matters because without an ECI a payload containing non-ASCII text is ambiguous: the same bytes decode to different characters under different encodings and the reader has to guess. Automatic remains the default, which is right for plain ASCII and wrong as soon as the content is not.

A test asserts every registry entry claiming ECI support is a matrix format, since ECI is carried in encoding modes that linear symbologies do not have.

### Escape preview

The generator can now render its field with escapes resolved, so `AB\x1DCD` displays as `AB⟨GS⟩CD`. A control character has no visible glyph, so without this there is no quick confirmation that an escape was understood — and reading the hex inspector to check is slower than the glance this affords. The toggle only appears when the payload actually contains escapes.

### Final verified state

- `./gradlew assembleDebug test` green. 197 tests, no failures.
- Merged manifest declares `CAMERA` and `VIBRATE` only.
- `tools/verify-zint-goldens.sh` still passes byte-for-byte against the reference.
- Debug APKs: 27.0 MB arm64, 26.2 MB armeabi-v7a, 27.1 MB x86_64. These are unminified debug builds carrying both native engines; release builds with R8 and resource shrinking will be substantially smaller.

## 21. Release build

`./gradlew assembleRelease` produces three per-ABI APKs with R8 and resource shrinking enabled.

| | debug | release |
|---|---|---|
| arm64-v8a | 27.0 MB | 4.7 MB |
| armeabi-v7a | 26.2 MB | 4.1 MB |
| x86_64 | 27.1 MB | 4.9 MB |
| classes in dex | 37,932 | 4,417 |

An 82 percent size reduction, almost all of it R8 removing unused Compose and AndroidX code. The native libraries account for most of what remains: `libzxingcpp_android.so` at 1.7 MB and `libbarcode_zint.so` at 801 KB, the latter having also been symbol-stripped from its 1.0 MB debug size.

### The keep rules that matter

The JNI boundary is the one place R8 will break the app while compiling perfectly. The native layer resolves things by name at runtime that are invisible in bytecode:

- JNI binds native methods to C symbols such as `Java_dev_barcodeworkbench_zint_ZintNative_nativeEncode`, so those names must survive verbatim.
- `zint_jni.c` calls `FindClass("dev/barcodeworkbench/zint/ZintResult")`, then `GetMethodID` for its no-arg constructor and `GetFieldID` for each field by literal name.

From bytecode alone `ZintResult`'s fields look write-only and its constructor unused, so R8 would happily rename or remove them. The failure would appear only when a barcode was first generated.

The rules are deliberately narrow, and the dex confirms they landed correctly: `ZintResult` keeps its name, its `<init>()` and all eight fields, and all five native methods keep their names, while R8 still renamed the public `encode` helper to `a` and the private `loaded`/`loadFailure` fields to `a`/`b`. Exactly the intended split.

A second, less obvious category is enums whose names are persisted rather than merely displayed. `SymbologyId`, `InputMode`, `CodeSource` and `VerificationStatus` are all written to the database and to backup files by name and read back with `valueOf`. Obfuscating them would make every existing row and every backup unreadable, and the failure would be quiet: `valueOf` throws, the mapper catches it, and entries simply vanish from listings. All four are confirmed present under their original names.

R8 reported no missing-class warnings.

### Signing

Signed with the standard debug key, deliberately. That makes the release build installable so R8, resource shrinking and native packaging can all be validated, and it introduces no credential to manage. It is confirmed V2-signed under `CN=Android Debug`.

This build is not distributable. Play Store upload and updating any real install both require a genuine signing key, which is a decision to take when there is something to ship — and one that has to be got right first time, since losing the key blocks all future updates.

### Retain the mapping file

`app/build/outputs/mapping/release/mapping.txt` is 50 MB and is what turns an obfuscated release stack trace back into something readable. It lives under `build/`, which is gitignored, so it must be archived alongside any release artifact that is distributed. Without it a crash report from that build is unusable.

### What this does and does not establish

The release build is structurally verified: it compiles, shrinks, signs, and the dex demonstrably retains everything the native layer and the persistence layer look up by name. It has not been executed. Confirming that the JNI call actually resolves at runtime still needs the APK installed on a device, and that remains the single largest untested area in the project.

## 22. On-device verification

Installed the arm64 release APK on a Pixel 5 (Android 14, API 34) over adb. This closes the largest gap in the project: everything below had previously been asserted from compilation and host tests only.

### The JNI layer works through R8

The generator produced a symbol on the first attempt. That single outcome confirms the whole chain: `System.loadLibrary` resolved, `nativeEncode` bound to its C symbol despite obfuscation, `ZintResult` was constructed from C via its no-arg constructor and populated by field name, and libzint encoded on arm64. No `UnsatisfiedLinkError`, no `NoSuchFieldError`. The keep rules held in practice, not just in the dex listing.

Escape handling was then verified end to end with a Code 128 payload of `AB\x1DCD`:

```
Decoded          AB⟨GS⟩CD
Bytes to encode  5 bytes
0000             41 42 1D 43 44        AB·CD
```

`41 42 1D 43 44` — the Group Separator is one byte at the right offset, not four literal characters. The escape codec, validator, encoder and byte inspector are all correct on real hardware.

The registry-driven UI also behaves: switching QR Code to Code 128 swapped the capability chips from `2D / GS1 / ECI` to `1D` alone and replaced the charset guidance text, all from the registry with no per-screen logic.

### Camera pipeline works

`Preview` and `ImageAnalysis` both bound, camera reached OPEN, and the preview reported `STREAMING` with `First frame done`. `ImageAnalysis` resolved to 1280x960 rather than the requested 1280x720, which is `FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER` correctly choosing the nearest supported 4:3 sensor mode.

The on-screen diagnostic reports **decode in 12 to 14 ms with all 23 symbologies enabled**. That figure can only come from the frame decoder, so it is direct evidence that frames are reaching zxing-cpp's native `ImageProxy` path. It also validates the frame-ownership contract: no `maxImages` exceptions and no acquire failures appeared, which is what a leaked or double-closed `ImageProxy` would have produced within seconds.

### A layout bug that only a device could find

The scanner shipped with every control unreachable. The preview `Box` used `heightIn(min = 260.dp)`, and a minimum is only a floor — with `CameraPreview` filling whatever it is given, the Box expanded to the full screen height and pushed the mode chips, torch, image picker and symbology button off the bottom of a `Column` that was not scrollable.

The fix is `weight(1f)` on the preview instead: in a `Column`, non-weighted children are measured at their natural height first and the weighted child takes what remains, so the controls always get their space. The control column is now also scrollable so a narrow screen cannot recreate the problem, and the capture list became a plain `Column` because nesting a `LazyColumn` inside a vertically scrollable parent is an unbounded-height crash.

Worth recording plainly: a clean compile and 197 passing unit tests could not have caught this. It was invisible to every check available before the APK ran, and obvious within seconds of looking at a screenshot. Device verification is not a formality at the end of a project; it tests a category of defect nothing else reaches.

### Decoding confirmed by hand

Scanning was then confirmed on the device against several symbologies. That closes the decode path: real optics, autofocus and ambient lighting are precisely the conditions no host test can stand in for, and they are where a scanner either works or does not.

It also means the stability gate is not blocking legitimate reads. Its whole purpose is to withhold a linear result until a second frame agrees, and a gate tuned too tightly would present as a scanner that simply never fires. It fired.

### Migration verified on device

`./gradlew :core:database:connectedDebugAndroidTest` now passes both migration tests on the Pixel 5:

```
migrate1To2_preservesExistingCodes        pass
migrate1To2_ftsSearchWorksAfterUpgrade    pass
```

This is the one piece of hand-written SQL in the project, validated against Room's identity hash on a real device. It establishes two things concretely: a v1 database holding a payload of `41 00 1D 42` survives the upgrade byte-for-byte, including the NUL and the Group Separator; and inserting into `config_entries` after the migration populates the FTS index, which proves the three synchronisation triggers were created correctly. Without those triggers search would have returned nothing and failed silently.

The first run failed, and the reason is worth recording because it was misleading. `MigrationTestHelper` loads the exported schema JSON from the *test APK's own assets*, and although the schemas were being exported to `core/database/schemas/`, that directory was never wired into the `androidTest` source set. The test therefore threw `FileNotFoundException: Missing file: WorkbenchDatabase/1.json` before reaching the migration at all — presenting as a schema problem when it was a missing test input. Fixed by adding `assets.srcDirs("$projectDir/schemas")` to the `androidTest` source set.

The lesson generalises: a migration test that has never been executed proves nothing, and this one had been sitting in the repository since Phase 6 looking like coverage while being incapable of running.

### Config packs verified, and a design flaw found

The loader reads its packs from assets correctly and FTS search works against real SQLite, including the hostile case the sanitiser exists for: `"code-128*(` was rewritten to `code* 128*` and returned two matches rather than throwing. That is the assertion the seven unit tests could only approximate.

But the vendor packs were invisible. `observeVendors` used `SELECT DISTINCT vendor FROM config_entries`, so a pack with no entries produced no rows and never appeared — which meant the `EmptyVendor` composable, written specifically to explain why vendor packs ship empty, was unreachable dead code. The Phase 6 write-up claimed that screen "states the reason plainly"; it could never render.

Fixed by schema v3, adding a `config_packs` table so pack identity exists independently of its entries. Two details in the migration matter: it back-fills pack rows from existing entries, because an imported pack lives only in the database and would otherwise be orphaned by the upgrade; and it preserves the `bundled` flag from those entries so a user's pack is not relabelled as one shipping with the app. The explanation text also moved out of the composable and into the pack's own `description`, so the reasoning lives with the data.

Five instrumented tests now pass on device, including `migrate1To3_walksTheWholeChain`, which covers the path an install that skipped a release actually takes. The fix was then confirmed by installing over the existing v2 install so the real migration ran against live data: all four vendors now appear with entry counts, and selecting Zebra renders its full explanation.

### Catalogue verified, and a missing feature found

Creating a library, saving into it, browsing and inspecting all work, and the entry survived a cold start. The detail sheet re-encodes from the stored payload rather than loading an image, and the hex view returned `68 74 74 70 73 3A 2F 2F 65 78 61 6D 70 6C 65 2E 63 6F 6D` — nineteen bytes, exactly matching the source string through the BLOB column and back.

The gap: there was no way to save a *single* generated code. Requirement 3 asks for a catalogue holding "generated and scanned barcodes". Scanned worked from Phase 5, and batch generation could write to a library, but the generator itself offered only Full screen and Export. A user generating one barcode had no route into the catalogue at all.

Fixed by adding a Save action to the generator, backed by the same library picker the scanner uses — extracted to `core:designsystem` so the two share one implementation rather than diverging. Verified end to end: saved to an existing library by chip, confirmed with "Saved to 'Field'", and the library's count updated live, which also demonstrates the Flow-backed query is genuinely reactive.

### Export and batch verified

Single-code export through SAF writes correctly. The suggested filename sanitises the payload (`QR-Code-https___example_com.svg`), and the resulting SVG is well-formed with correct geometry: a 330px canvas for a 25-module QR at 10px with a 4-module quiet zone, the top-left finder pattern 70px wide at offset 40. Run-merging is working, 166 rects rather than one per module. Selecting JPEG shows the lossy warning; selecting SVG hides the module-size slider, since pixel size is meaningless for vector output.

Batch generation handled the fixture wordlist exactly as designed:

```
4 ready, 1 rejected
line 6: Cannot encode 'T', 'O', 'L', 'N', 'G' and 4 more. Allowed: ...
```

The header row was skipped, the `#` comment ignored, per-row symbologies applied, and the invalid row rejected before anything was written, cited by its true source line number rather than its index among entries. The ZIP contained `2-Code-128-Plain_128.png`, `3-EAN-13-Retail_item.png`, `4-Code-128-With_GS.png` and `7-QR-Code-Website.png` — line-numbered so they map back to the file the user would edit.

### Full round trip through both engines

Feeding a generated PNG back through the app's own image decoder closed the loop: `AB\x1DCD` was parsed to bytes, encoded by libzint, rendered to PNG, decoded by zxing-cpp, and returned as `41 42 1D 43 44`. The Group Separator survived every stage.

### A correctness bug the round trip exposed

That decode reported the symbol as **GS1-128** while its AIM identifier was `]C0`, which means plain Code 128. The two disagreed.

The cause: Code 128 and GS1-128 are the same symbology distinguished only by a leading FNC1, so the registry correctly gives both `readerFormat = "CODE_128"`. The reverse map was built with `associate`, which is last-wins, so `GS1_128` silently overwrote `CODE_128` and every Code 128 scan was mislabelled.

This is worse than a display error. Saving such a scan persists the wrong symbology, and re-encoding it later would prepend an FNC1 and change the data.

Fixed by choosing the non-GS1 reading as the base and disambiguating with the AIM identifier, which is the only thing in a decoded result that distinguishes them: `]C1` is GS1-128, `]C0` and `]C2` are Code 128. A test asserts the set of ambiguous format names is exactly what `refine` knows how to resolve, so a future registry entry introducing another many-to-one mapping fails the build rather than silently collapsing.

Host tests now number 206. The fix was confirmed on device: the same PNG now decodes as Code 128 with `]C0`.

### Backup verified, including the failure paths

All four paths were exercised on device.

Export produced a 418-byte gzip of 890 bytes of JSON, with the envelope as designed: schema and fingerprint versions recorded separately, UTC timestamp, app version, SHA-256 checksum, and the payload base64-encoded — `aHR0cHM6Ly9leGFtcGxlLmNvbQ==` decoding to `https://example.com`.

A deliberately tampered copy, with a label edited and the checksum left stale, was rejected with "Backup checksum does not match; the file is damaged". Nothing was written and the app did not crash, which is the behaviour the two-phase design exists to guarantee: the plan step never ran, so there was no opportunity to half-populate a library.

The real test was destructive. The library was deleted through the UI, confirming that the delete dialog states the entry count and suggests exporting first, and the catalogue returned to its empty state. Importing the valid backup then restored the library, its name, the entry, its symbology and its source.

Re-importing the same file afterwards reported "0 codes will be added, 1 already present". That result is stronger than it looks: the restored entry was recreated by the import and therefore carries a different database id from the original, yet the fingerprint still matched. De-duplication is genuinely content-based rather than identity-based, which is what makes it survive a restore.

Two rough edges were fixed along the way. Counted nouns rendered as "1 codes" throughout, which reads as a bug even though nothing was broken, so a small `counted` helper now handles it with tests covering the zero case that is easy to miss. And the backup sheet retained a message from a previous attempt when reopened, since its ViewModel outlives the sheet; it now clears on open.

### Final state

- 210 host tests, no failures.
- 5 instrumented migration tests passing on a Pixel 5.
- Every feature exercised on hardware: generate, scan, catalogue, device configuration, export, batch, backup and restore.
- Merged manifest declares `CAMERA` and `VIBRATE` only.
- Release APKs signed with the debug key: 4.7 MB arm64, 4.1 MB armeabi-v7a, 4.9 MB x86_64.

### Device-only defects, final count

Six defects reached a fully-tested build and were caught only by running it:

1. Scanner controls pushed off-screen by `heightIn(min = …)`.
2. The migration test structurally incapable of executing.
3. Vendor packs invisible, making the empty-pack explanation dead code.
4. No way to save a single generated code, against an explicit requirement.
5. Plain Code 128 mislabelled as GS1-128 by a last-wins `associate` over a many-to-one map.
6. Pluralisation and a stale message, both cosmetic.

Three of those were features described in this document as working. The fifth was a genuine data-correctness bug that would have persisted the wrong symbology and altered payloads on re-encode; it surfaced only because a round trip placed two independent facts side by side — the symbology name and the AIM identifier — and they disagreed.

The generalisable lesson is not that the tests were inadequate. They were good tests, and they still pass. It is that unit tests verify components behave, and say almost nothing about whether those components are wired to anything a user can reach. Every one of these defects lived in the wiring.

## 23. Learn section and input guidance

The generator exposed input modes and symbologies as bare names. Both are choices the
user cannot make correctly without knowing what they mean, and nothing on screen said.

Added:

- `core:model/InputModeGuide` — per-mode label, one-line summary, worked examples and a
  longer detail string. Placed in `core:model` because both the generator and the
  reference page render it, and two copies would drift.
- `CheckDigitBehaviour.description` — moved onto the enum for the same reason, replacing
  a `when` block that had been local to the reference page.
- `feature:learn` — a fifth top-level destination with three tabs: Guide (six long-form
  articles), Formats (per-symbology reference), Reference (escape and control-character
  cheat sheet).
- Generator: inline expandable hint under the input-mode chips, and a "Rules" panel on
  the symbology row showing character set, length, check-digit behaviour and an example.

Articles are typed `Block` data rather than markdown, so content can be asserted. It is:
36 tests now cover it, including one that runs every example claiming to produce
specific bytes through the real `EscapeCodec` and compares the result. Documentation
that states `AB\x1DCD` yields `41 42 1D 43 44` is now a test, not a claim.

The Formats tab and the generator's Rules panel are both generated from
`SymbologyRegistry`, which is itself verified against libzint. Hand-writing that
reference would have created a third description of rules the encoder already defines.

Two things were found by reading the running app rather than the code:

1. The quick reference documented the `\^` escapes twice — a hand-written table and a
   `Directive.entries`-driven one, with different wording for the same escapes. The
   hand-written table was the drift risk and was deleted; `\^^` moved to the general
   escape table, where it belongs, since it is not a directive.
2. Expansion state was keyed on the selected mode and symbology, so it collapsed on
   every switch — exactly the moment someone comparing options wants it to stay open.

Also corrected: a hardcoded "All 33" control characters, now derived from the palette;
an article example whose `input` field contained prose, which made it unverifiable; and
payload helper text that joined two fragments with a full stop, producing "…(via ECI).
up to 4296 characters".

Verified on the Pixel 5: all three tabs, article drill-down and back navigation, the
category filter and card expansion in Formats, the merged directive section, and the
generator hint persisting across a mode switch while swapping its content. 224 host
tests pass; release build is warning-free apart from two pre-existing AndroidX
deprecations unrelated to this work.

## 24. Android best-practice compliance pass

A sweep for current-platform compliance, prompted by two deprecation warnings. Lint went
from 8 findings to 0 and the build from four deprecation warnings to none.

### Deprecated APIs cleared

- `hiltViewModel()` moved package in Hilt 1.3; all seven call sites updated. The new
  home, `hilt-lifecycle-viewmodel-compose`, arrived transitively but is now declared
  directly in the five modules that import it.
- `MenuAnchorType` renamed to `ExposedDropdownMenuAnchorType`, and the fully-qualified
  inline reference replaced with an import.
- `LocalLifecycleOwner` moved from `compose.ui.platform` to `lifecycle.compose`.
- `@ApplicationContext` on a constructor parameter now needs an explicit `@param:`
  use-site target; Kotlin warned that the default is changing.
- Two build-script deprecations: `java.setSrcDirs(emptyList())` and `assets.srcDirs(…)`
  replaced with the `directories` API. The second is load-bearing for the migration
  test, so that test was re-run on the device rather than assumed — 5 instrumented
  tests still pass.

### Platform requirements verified, not assumed

- **16 KB page size.** Required by Play for apps targeting Android 15+, and this app
  ships native code. All six shared objects report `LOAD align: 0x4000`, and all three
  ABI APKs pass `zipalign -c -P 16 -v 4`. NDK 28 produces this by default; the point is
  that it was checked rather than trusted.
- **Edge-to-edge**, enforced from targetSdk 35: `enableEdgeToEdge()` is called and
  Scaffold insets are applied to the NavHost.
- **Predictive back**, default-on at targetSdk 36+: the Learn tab's `BackHandler` was
  exercised on the device.

### material-icons-extended removed

The app pulled in `androidx.compose.material:material-icons-extended` — roughly four
thousand icons — to use seven. It is also frozen: last released 1.7.8 in February 2025,
and the current Compose BOM still pins it there while material3 has moved to 1.4.0. It
was additionally the only path by which `material-icons-core` entered the build.

The seven icons are now vendored in `core:designsystem/WorkbenchIcons.kt`. The path data
was extracted programmatically from the library rather than retyped, and the library is
retained as a **test-only** dependency so `WorkbenchIconsTest` can assert each vendored
icon `isEqualTo` the original. That test was negative-controlled: perturbing one
coordinate by 0.01 fails it.

### Toolchain

Kotlin 2.2.10 → 2.4.10, Gradle 9.6.1 → 9.7.0. Two things worth recording:

- The catalog comment claimed Kotlin and KSP "must stay a matched pair" using the
  `<kotlin>-<ksp>` scheme. That has not been true since KSP2, which versions
  independently; the comment was describing a constraint that no longer exists.
- 2.3.21 and 2.4.10 were both built and tested before choosing. 2.4.10 was taken because
  `kotlin-stdlib` then resolves to 2.4.10 consistently across the runtime classpath,
  KSP 2.3.11 emits no mismatch warning, and Room/Hilt codegen re-runs clean under
  `--rerun-tasks`. The newer compiler also found a genuine dead safe-call in
  `ZxingFormatMapping`.

The Gradle wrapper now pins `distributionSha256Sum`, which it did not before — the
wrapper previously accepted whatever the URL returned.

### Smaller items

Launcher icon: added the `<monochrome>` layer for themed icons, deleted a byte-identical
and unreferenced `ic_launcher_round`, and dropped the redundant `-v26` qualifier since
minSdk is 26. Removed a stale `tools:targetApi="34"` and a dead `kotlin-android` plugin
alias left over from the AGP 9 migration.

One process note: a `zipalign -c -P 16` check appeared to fail until I noticed the
command was malformed — the alignment argument was missing, so the APK path was being
parsed as the alignment. The APK was fine. Worth recording because the failure looked
exactly like a real one.

## 25. Roadmap

Phase 0 — Environment. Android SDK, platform 36, build-tools, NDK, CMake, Gradle wrapper. In progress.

Phase 1 — De-risking spikes, before any UI work:
- Build libzint for Android via CMake, wire the JNI encode call, verify a known payload produces the expected module matrix.
- Pull the zxing-cpp AAR, decode a fixture image, confirm the format coverage claims.

Phase 2 — Scaffold: module graph, Hilt, Room schema v1, Material 3 theme, navigation shell.

Phase 3 — Generator: symbology registry, validation, payload composer with the insert palette and byte inspector, full-screen viewer, export.

Phase 4 — Reader: CameraX binding, stability gate, result inspector, decode-from-file.

Phase 5 — Catalogue: libraries, browse, search, detail, import/export.

Phase 6 — Config packs: schema, loader, FTS search, seed data, safety UI, importer.

Phase 7 — Batch generation: wordlist import, validation preview, ZIP / PDF / library outputs.

Phase 8 — Hardening: test coverage, accessibility pass, large-payload and long-symbol edge cases, APK size review.

Phase 1 is deliberately first and deliberately small. Both engines are external native dependencies, and if either fails to build or behave, the entire plan changes — so that gets proven before a line of UI is written.
