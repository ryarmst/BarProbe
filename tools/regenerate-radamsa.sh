#!/usr/bin/env bash
#
# Regenerates the vendored libradamsa.c from radamsa's Scheme sources.
#
# This is an offline, occasional step, run by a human when bumping the pinned
# radamsa version -- never by the app build or CI. It needs a C compiler and network
# access (to fetch the Owl Lisp bootstrap and the hex library); the app build itself
# needs neither, because it compiles the committed libradamsa.c directly.
#
# The generated file and its provenance land in barcode/radamsa/third_party/radamsa/.
# Review the diff, rebuild, and re-run the instrumented RadamsaMutator tests before
# committing: the determinism and memory-stability guarantees are properties of the
# generated code, so a new revision has to re-earn them.
set -euo pipefail

# Pin both radamsa and the hex library it depends on, so a regeneration is
# reproducible rather than "whatever develop looked like today".
RADAMSA_REV="${RADAMSA_REV:-40d5dec416fb5277dbbd72c04b82ba2ae039778a}"  # v0.7

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/barcode/radamsa/third_party/radamsa"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "== cloning radamsa @ $RADAMSA_REV"
git clone --quiet https://gitlab.com/akihe/radamsa.git "$WORK/radamsa"
git -C "$WORK/radamsa" checkout --quiet "$RADAMSA_REV"

cd "$WORK/radamsa"

# The library target forgets to declare lib/hex as a prerequisite (only the binary
# target does), so fetch it explicitly before generating.
echo "== fetching hex library"
make lib/hex >/dev/null

echo "== bootstrapping Owl and generating libradamsa.c"
make c/libradamsa.c >/dev/null

HEX_REV="$(git -C lib/hex rev-parse HEAD)"

echo "== vendoring into $DEST"
cp c/libradamsa.c "$DEST/libradamsa.c"
cp c/radamsa.h    "$DEST/radamsa.h"
cp LICENCE        "$DEST/LICENCE"
chmod 644 "$DEST/libradamsa.c"

cat > "$DEST/PINNED_VERSION.txt" <<EOF
radamsa v0.7
  repo:    https://gitlab.com/akihe/radamsa
  git-rev: $RADAMSA_REV

The library C source (libradamsa.c) is generated, not hand-written: Owl Lisp
compiles the Scheme sources to a single dependency-free C file, then the Makefile
appends c/lib.c (the radamsa_init / radamsa wrappers). Only that generated file and
its header are vendored, so the Android build needs a plain C compiler and no Owl
toolchain. Regenerate with tools/regenerate-radamsa.sh.

Generated from:
  radamsa   $RADAMSA_REV (v0.7)
  hex lib   https://gitlab.com/owl-lisp/hex  $HEX_REV
  owl       ol-0.2.2 (bootstrap, https://haltp.org/files/ol-0.2.2.c.gz)
EOF

echo "== done. Review the diff and re-run the RadamsaMutator instrumented tests."
