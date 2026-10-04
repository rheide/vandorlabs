#!/bin/bash
# Compare existing saves using an instrumented standard JAR. World copies stay outside Git.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
JAR=$(realpath "${1:?Usage: benchmark_world.sh JAR WORLD OUTPUT}")
WORLD=$(realpath "${2:?World directory required}")
OUTPUT=$(realpath -m "${3:?Output directory required}")
case "$OUTPUT/" in "$ROOT/"*) echo 'Use an output directory outside the repository'; exit 2;; esac
if [ -e "$OUTPUT" ]; then echo 'Output directory must be fresh'; exit 2; fi
mkdir -p "$OUTPUT/game/mods" "$OUTPUT/game/config" "$OUTPUT/results"
python3 - "$WORLD" "$OUTPUT/game/saves/benchmark-world" <<'PY'
import shutil,sys
shutil.copytree(sys.argv[1],sys.argv[2],ignore=shutil.ignore_patterns('mods','session.lock'))
PY
cp "$WORLD"/mods/*.jar "$OUTPUT/game/mods/"
cp "$JAR" "$OUTPUT/game/mods/"
cp "$ROOT/testclient/runtime/game/options.txt" "$OUTPUT/game/options.txt"
cp "$ROOT/testclient/runtime/game/config/forge.cfg" "$OUTPUT/game/config/forge.cfg"
sha256sum "$JAR" > "$OUTPUT/results/artifact.sha256"
sha256sum "$WORLD/level.dat" > "$OUTPUT/results/source-world.sha256"
VANDOR_LABS_TEST_HEAP=${VANDOR_LABS_TEST_HEAP:-6G} \
VANDOR_LABS_WORLD_BENCHMARK=true VANDOR_LABS_TEST_GAME_DIR="$OUTPUT/game" \
    VANDOR_LABS_REPRO_OUT="$OUTPUT/results" timeout "${VANDOR_LABS_WORLD_TIMEOUT:-900}" \
    "$ROOT/testclient/run.sh" > "$OUTPUT/client.log" 2>&1
rg -q 'world-benchmark PASS' "$OUTPUT/client.log"
echo "World benchmark results: $OUTPUT/results"
