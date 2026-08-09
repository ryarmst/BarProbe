# Fuzzing feature — shelved, resume notes

The fuzz feature is **built but unwired**. Everything works except the native engine
*inside an Android app process*, so the Fuzz tab is disconnected from navigation and
`:feature:fuzz` / `:barcode:radamsa` are not depended on by `:app`. The code, module,
tests and vendored engine all remain in the tree. This file is how to finish it.

## Why it was shelved

radamsa's library mode crashes under ART. Evidence, gathered on the Pixel 5 (arm64):

| Harness | 10,000 mutations |
| --- | --- |
| Standalone arm64 executable (NDK-built) | clean |
| The same code as a `.so`, loaded with `dlopen` | clean |
| Inside the ART app process (instrumented test) | **SIGSEGV** |

The crash is `SIGSEGV / SEGV_MAPERR` at a **stable low address (~0x58b000, ~5.8 MB)**,
in radamsa's generated Owl VM (`radamsa → library_call → vm`). That address is mapped in
an ordinary process (so the standalone and `dlopen` harnesses read stale data but
survive) and unmapped in an ART app process (so it faults immediately). It is a latent
bad-pointer dereference in the Owl runtime that only manifests under ART's memory layout.

Ruled out and not the cause: the `radamsa_init()` heap leak (fixed — init-once is
memory-flat), heap relocation (an `adjust_heap` no-op patch did **not** help and was
reverted), signal-handler collisions, and `.so` loading itself.

## The plan: run radamsa in a separate process

This is the recommended path precisely because **the standalone arm64 binary already
runs 10,000 mutations cleanly on the target device**. A subprocess sidesteps ART's
memory layout entirely, and as a bonus makes the `radamsa_init()` leak irrelevant — the
child can be killed and restarted to reclaim memory.

### Packaging the executable

Android forbids executing arbitrary files from app-writable storage (W^X). The one
supported route is to ship the executable **as a `lib*.so`** and run it from the
read-only `nativeLibraryDir`:

- Build radamsa as an executable (not a shared lib), output named e.g.
  `libradamsa-exec.so`, one per ABI. Do this in `:barcode:radamsa`'s CMake with a second
  `add_executable(...)` target, or a dedicated module. Confirm the build actually emits
  it into the APK's `lib/<abi>/` — CMake executable outputs are not packaged the way
  `.so` libraries are; you may need `android.packagingOptions` / a custom task, or the
  `it.jniLibs` trick, to get the executable into `lib/<abi>/`. Verify with
  `unzip -l app-*.apk | grep libradamsa-exec`.
- Set `android:extractNativeLibs="true"` (or rely on the default for older
  `minSdk`)... note the modern default is compressed/uncompressed-in-place. The file must
  be executable on disk; with uncompressed libs it runs directly from
  `applicationInfo.nativeLibraryDir`.
- At runtime the path is
  `context.applicationInfo.nativeLibraryDir + "/libradamsa-exec.so"`.

### Native side: a persistent worker, not one-shot exec

Do not `exec` once per "Next" — process spawn is far more expensive than a mutation.
Write a tiny `main()` that:

1. `radamsa_init()` once.
2. Reads requests from stdin, writes responses to stdout, in a length-prefixed binary
   framing (never newline-delimited: payloads are arbitrary bytes). For example:

   - request: `uint32 seed`, `uint32 base_len`, `base_len` bytes, `uint32 max_len`
   - response: `uint32 out_len`, `out_len` bytes

3. Loops until stdin closes.

This is essentially the existing `dev_spike.c` / `loader.c` from the spike, turned into a
stdin/stdout server. Those harnesses (in the scratch work) are the proven-good starting
point.

### Kotlin side: a new Mutator implementation

The whole feature above the engine is already engine-agnostic — it depends only on the
`Mutator` interface in `:barcode:engine-api`. So this is a drop-in:

- New `ProcessRadamsaMutator : Mutator` in `:barcode:radamsa` (replacing the in-process
  `RadamsaMutator`).
  - `ProcessBuilder(nativeLibraryDir + "/libradamsa-exec.so")`, keep it running, guard
    stdin/stdout with a `Mutex` (still single-flight).
  - `mutate(input, seed, maxLength)` frames a request, reads the response.
  - Health/lifecycle: if the child dies, restart it on the next call; expose that through
    `isAvailable()`. A watchdog timeout on read guards against a wedged child.
  - Kill the child when the ViewModel is cleared / the app backgrounds, so a fuzzing
    session's memory is reclaimed.
- Rebind in `MutatorModule` to `ProcessRadamsaMutator`.

### Gotchas to expect

- **SELinux / exec policy** varies by OEM and Android version. Executing from
  `nativeLibraryDir` is the sanctioned path and generally works, but test on real targets
  early; some devices restrict `exec()` of app libs.
- **Foreground/background**: a backgrounded app may have its child process killed by the
  OS. Restart-on-next-call handles this; also re-init cleanly.
- **`extractNativeLibs`**: if libs are stored uncompressed-in-APK (default on modern
  AGP), confirm the executable bit / that it can be exec'd from the APK-mapped path; you
  may need `useLegacyPackaging = true` to force extraction to
  `nativeLibraryDir` as real files.
- **Determinism** is unchanged from the in-process design: still stateful across calls,
  still reproducible only at the artifact level (save the bytes). The subprocess does not
  restore per-seed reproducibility.
- **Startup latency**: first mutation pays process spawn + `radamsa_init()` (~a few ms).
  Fine for a button, but warm the child when the tab opens.

## What already exists and is reusable

- `:barcode:engine-api` — `Mutator` interface. Keep as-is.
- `:barcode:radamsa` — vendored `libradamsa.c` (v0.7, pinned; `tools/regenerate-radamsa.sh`),
  `radamsa.h`, the JNI bridge (`radamsa_jni.c` / `RadamsaNative`) and in-process
  `RadamsaMutator`. The JNI path is what crashes under ART; keep it for reference but the
  new worker `main()` is what to build. `RadamsaMutatorTest` is `@Ignore`d with a pointer
  here.
- `:feature:fuzz` — `FuzzEngine` (Intent-A retry/skip, host-tested by `FuzzEngineTest`),
  `Fuzzability`, `FuzzViewModel` (session history cache, save-as-`FUZZED`), `FuzzScreen`.
  All engine-agnostic; nothing here needs to change.
- `CodeSource.FUZZED`, and the vendored `WorkbenchIcons.Shuffle` (+ its equality test).

## To re-wire when the engine works

1. `app/build.gradle.kts`: re-add `implementation(project(":barcode:radamsa"))` and
   `implementation(project(":feature:fuzz"))`.
2. `WorkbenchNavigation.kt`: restore the `Fuzz` destination and add it back to `all`.
3. `WorkbenchApp.kt`: restore the `FuzzScreen` import and the NavHost `composable` entry.
4. `strings.xml`: restore `nav_fuzz`.
5. `app/proguard-rules.pro`: restore the `RadamsaNative` keep rule (removed while shelved).
6. Remove the `@Ignore` on the radamsa instrumented tests and run them on a device.

The git history for the shelving commit is the exact diff to reverse for steps 1–5.
