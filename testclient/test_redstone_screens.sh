#!/bin/bash
# Exercise redstone-screen rows through real Forge clicks, dialogs and packets.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 ./gradlew build --no-daemon
RUN_OUT=$(mktemp -d "$ROOT/testclient/render-channel.XXXXXX")
VERSION=$(sed -n "s/^version = '\([^']*\)'/\1/p" build.gradle | head -1)
rm -f testclient/runtime/game/mods/vandorlabs-*.jar
cp "build/libs/vandorlabs-$VERSION.jar" testclient/runtime/game/mods/
VANDOR_LABS_REDSTONE_SCREEN_CHECKS_ONLY=true VANDOR_LABS_REPRO_OUT="$RUN_OUT" timeout 600 testclient/run.sh > "$RUN_OUT/client.log" 2>&1
rg -q 'redstone-screen-runtime PASS shape=2' "$RUN_OUT/client.log"
echo "Live redstone-screen artifacts: $RUN_OUT"
