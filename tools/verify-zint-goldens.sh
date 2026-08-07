#!/usr/bin/env bash
#
# Verifies the JNI encode path against libzint's own CLI, on the host, with no
# Android device involved.
#
# It builds the vendored libzint twice -- once as the reference CLI and once
# behind our JNI wrapper -- then encodes the same fixture corpus through both and
# diffs the module matrices byte-for-byte.
#
# Usage: tools/verify-zint-goldens.sh
set -euo pipefail

MODULE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ZINT_MODULE="$MODULE_ROOT/barcode/zint"
ZINT_SRC="$ZINT_MODULE/third_party/zint"
OUT="$MODULE_ROOT/build/zint-verify"

CMAKE="$(ls -d "$HOME"/Android/Sdk/cmake/*/bin/cmake 2>/dev/null | head -1 || command -v cmake)"
if [ -z "$CMAKE" ]; then
    echo "cmake not found (looked in the Android SDK and on PATH)" >&2
    exit 1
fi

mkdir -p "$OUT"

echo "== 1/4 obtaining reference zint CLI"
# Only backend/ is vendored, because only backend/ ships in the app. The
# reference CLI is built from the full upstream tree at the exact revision
# recorded in PINNED_VERSION.txt, so the comparison is against the same code we
# link, not a different release.
#
# Set ZINT_UPSTREAM_DIR to an existing checkout to run this offline.
PINNED_REV="$(sed -n 's/^git-rev: //p' "$ZINT_SRC/PINNED_VERSION.txt")"
if [ -z "$PINNED_REV" ]; then
    echo "PINNED_VERSION.txt has no git-rev line" >&2
    exit 1
fi

UPSTREAM="${ZINT_UPSTREAM_DIR:-$OUT/upstream}"
if [ ! -f "$UPSTREAM/CMakeLists.txt" ]; then
    echo "   fetching zint at $PINNED_REV"
    mkdir -p "$UPSTREAM"
    curl -fL --retry 3 "https://github.com/zint/zint/archive/${PINNED_REV}.tar.gz" \
        | tar xz -C "$UPSTREAM" --strip-components=1
fi

"$CMAKE" -S "$UPSTREAM" -B "$OUT/cli" \
    -DZINT_USE_QT=OFF -DZINT_TEST=OFF -DCMAKE_BUILD_TYPE=Release >/dev/null
"$CMAKE" --build "$OUT/cli" -j"$(nproc)" >/dev/null
ZINT_CLI="$OUT/cli/frontend/zint"
"$ZINT_CLI" --version | head -1

# Guard against the vendored backend drifting from the reference: if the two
# disagree the whole comparison is meaningless.
VENDORED_VER="$(sed -n 's/^zint Zint version \([0-9.]*\).*/\1/p' "$ZINT_SRC/PINNED_VERSION.txt")"
REF_VER="$("$ZINT_CLI" --version | sed -n 's/^Zint version \([0-9.]*\).*/\1/p')"
if [ -n "$VENDORED_VER" ] && [ "$VENDORED_VER" != "$REF_VER" ]; then
    echo "FAIL: vendored backend is $VENDORED_VER but reference CLI is $REF_VER" >&2
    exit 1
fi

echo "== 2/4 building JNI wrapper for host"
"$CMAKE" -S "$ZINT_MODULE/src/main/cpp" -B "$OUT/jni" \
    -DCMAKE_BUILD_TYPE=Release >/dev/null
"$CMAKE" --build "$OUT/jni" -j"$(nproc)" >/dev/null
test -f "$OUT/jni/libbarcode_zint.so"

echo "== 3/4 running the JNI corpus"
mkdir -p "$OUT/classes"
javac -nowarn -d "$OUT/classes" \
    "$ZINT_MODULE/src/main/java/dev/barcodeworkbench/zint/"*.java \
    "$ZINT_MODULE/src/test/java/dev/barcodeworkbench/zint/"*.java
java -Djava.library.path="$OUT/jni" -cp "$OUT/classes" \
    dev.barcodeworkbench.zint.ZintSpikeVerifier > "$OUT/jni_out.txt"

echo "== 4/4 comparing against the reference"
"$ZINT_MODULE/src/test/golden/generate_goldens.sh" "$ZINT_CLI" > "$OUT/golden.txt"
# Cases run first in the verifier's output; CAPABILITIES is the next section.
sed -n '/^=== CASE/,/^=== CAPABILITIES/p' "$OUT/jni_out.txt" \
    | sed '/^=== CAPABILITIES/d; /^# warning/d' > "$OUT/jni_cases.txt"

status=0
if diff -u "$OUT/golden.txt" "$OUT/jni_cases.txt" > "$OUT/diff.txt"; then
    cases=$(grep -c '^=== CASE' "$OUT/golden.txt")
    echo "PASS: module matrices byte-identical to the reference across $cases cases"
else
    echo "FAIL: module matrices diverge from the reference" >&2
    cat "$OUT/diff.txt" >&2
    status=1
fi

if grep -q "SELF-CHECKS PASSED" "$OUT/jni_out.txt"; then
    echo "PASS: symbology ids, capabilities, binary fidelity and error surfacing"
else
    echo "FAIL: self-checks reported problems" >&2
    sed -n '/=== SUMMARY/,$p' "$OUT/jni_out.txt" >&2
    status=1
fi

exit "$status"
