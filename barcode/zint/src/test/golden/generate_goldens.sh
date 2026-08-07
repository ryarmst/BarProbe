#!/usr/bin/env bash
# Produces reference module dumps using libzint's own CLI, in the same
# "=== CASE <name>" framing the JNI verifier emits, so the two can be diffed.
#
# Usage: generate_goldens.sh <path-to-zint-cli>
set -euo pipefail

ZINT="${1:?path to zint CLI required}"

emit() {
    local name="$1"; shift
    echo "=== CASE ${name}"
    "$ZINT" --dump "$@"
}

emit code128-baseline        -b 20 -d 'ABC123'
emit code128-embedded-gs     -b 20 --esc -d 'AB\x1DCD'
emit code128-codeset-switch  -b 20 --esc --extraesc -d '\^A001\^BABC'
emit code128-fnc1            -b 20 --esc --extraesc -d '\^1010123456789'
emit gs1-128-ai              -b 16 -d '[01]09501101530003'
emit qr-eci26-unicode        -b 58 --esc --eci=26 -d 'café'
emit datamatrix-binary-nul   -b 71 --binary --esc -d '\x00\x01\xFF\xFE'
emit ean13                   -b 15 -d '012345678901'
emit pdf417                  -b 55 -d 'PDF417 payload'
emit aztec                   -b 92 -d 'Aztec payload'
emit datamatrix-text         -b 71 -d 'DM payload'
emit maxicode                -b 57 -d 'MaxiCode payload'
emit dotcode                 -b 115 -d 'DotCode'
emit microqr                 -b 97 -d 'MQR'
emit rmqr                    -b 145 -d 'rMQR payload'
emit dbar-omn                -b 29 -d '0950110153000'
