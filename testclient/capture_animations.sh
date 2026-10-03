#!/bin/bash
# Capture every motion, or comma-separated scene IDs with --filter.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
FILTER=
if [ "${1:-}" = --filter ]; then FILTER=${2:?Expected comma-separated scene IDs}; shift 2; fi
JAVA8=/usr/lib/jvm/java-8-openjdk-amd64
JAVA_HOME="$JAVA8" PATH="$JAVA8/bin:$PATH" ./gradlew build --no-daemon
VERSION=$(sed -n "s/^version = '\([^']*\)'/\1/p" build.gradle | head -1)
RUN_OUT=$(mktemp -d "$ROOT/testclient/door-animation.XXXXXX")
mkdir -p testclient/runtime/game/mods
rm -f testclient/runtime/game/mods/vandorlabs-*.jar
cp "build/libs/vandorlabs-$VERSION.jar" testclient/runtime/game/mods/
sha256sum "build/libs/vandorlabs-$VERSION.jar" > "$RUN_OUT/artifact.sha256"
git rev-parse HEAD > "$RUN_OUT/source-commit.txt"
VANDOR_LABS_REPRO_OUT="$RUN_OUT" VANDOR_LABS_DOCUMENTATION_ANIMATIONS=true \
    VANDOR_LABS_DOCUMENTATION_ANIMATION_FILTER="$FILTER" \
    VANDOR_LABS_REPRO_WIDTH=896 VANDOR_LABS_REPRO_HEIGHT=504 \
    timeout 1200 bash testclient/run.sh > "$RUN_OUT/client.log" 2>&1
python3 - "$RUN_OUT" "$FILTER" <<'PY'
import json,sys
from pathlib import Path
run, selected = Path(sys.argv[1]), set(sys.argv[2].split(','))
specs=json.loads(Path('src/main/resources/assets/vandorlabs/data/documentation_animations.json').read_text())
expected={e['id'] for e in specs if not sys.argv[2] or e['id'] in selected}
assert expected, 'No matching scenes'
assert expected=={p.parent.name for p in run.glob('*/capture.json')}, 'Incomplete capture set'
log=(run/'client.log').read_text()
for scene in expected: assert 'documentation-animation PASS '+scene+' frames=' in log, scene
assert 'Exception loading model' not in log, 'Model errors'
PY
python3 testclient/encode_animations.py "$RUN_OUT" "$@"
echo "Animation source: $RUN_OUT"
