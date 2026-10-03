#!/bin/bash
# Build the standard JAR, capture a real door cycle, then validate and encode it.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
JAVA8=/usr/lib/jvm/java-8-openjdk-amd64
JAVA_HOME="$JAVA8" PATH="$JAVA8/bin:$PATH" ./gradlew build --no-daemon
VERSION=$(sed -n "s/^version = '\([^']*\)'/\1/p" build.gradle | head -1)
RUN_OUT=$(mktemp -d "$ROOT/testclient/door-animation.XXXXXX")
mkdir -p testclient/runtime/game/mods
rm -f testclient/runtime/game/mods/vandorlabs-*.jar
cp "build/libs/vandorlabs-$VERSION.jar" testclient/runtime/game/mods/
sha256sum "build/libs/vandorlabs-$VERSION.jar" > "$RUN_OUT/artifact.sha256"
git rev-parse HEAD > "$RUN_OUT/source-commit.txt"
VANDOR_LABS_REPRO_OUT="$RUN_OUT" VANDOR_LABS_DOCUMENTATION_DOOR_GIF=true \
    timeout 300 bash testclient/run.sh > "$RUN_OUT/client.log" 2>&1
rg -q 'documentation-door-capture PASS' "$RUN_OUT/client.log"
if rg -q 'Exception loading model' "$RUN_OUT/client.log"; then
    echo "FAIL: model loading errors; see $RUN_OUT/client.log"
    exit 1
fi
python3 testclient/encode_door_gif.py "$RUN_OUT" "$@"
echo "Door animation source: $RUN_OUT"
