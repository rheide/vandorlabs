#!/bin/bash
# Capture the requested scope once, validate it, and merge changed gallery images.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
RUN_LOG=$(mktemp)
trap 'rm -f "$RUN_LOG"' EXIT
bash testclient/test_viewscreen.sh "$@" | tee "$RUN_LOG"
RUN_OUT=$(sed -n 's/^Live-client artifacts: //p' "$RUN_LOG" | tail -1)
if [ "${1:-}" = --full ]; then
    python3 testclient/export_gallery.py "$RUN_OUT" --full
else
    python3 testclient/export_gallery.py "$RUN_OUT"
fi
echo "Gallery source run: $RUN_OUT"
