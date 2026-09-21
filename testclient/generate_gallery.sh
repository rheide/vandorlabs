#!/bin/bash
# Build the mod, run the deterministic Forge client scene, and publish curated
# screenshots under docs/images/gallery for the GitHub documentation.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JAVA8=/usr/lib/jvm/java-8-openjdk-amd64
RUN_OUT=$(mktemp -d "$ROOT/testclient/gallery-run.XXXXXX")

cd "$ROOT"
JAVA_HOME="$JAVA8" PATH="$JAVA8/bin:$PATH" ./gradlew build --no-daemon
mkdir -p testclient/runtime/game/mods
cp build/libs/vandorlabs-1.0.jar testclient/runtime/game/mods/vandorlabs-1.0.jar
cp "$HOME/MC-Forge12-2/mods/worldedit-forge-mc1.12.2-6.1.10-dist.jar" \
    testclient/runtime/game/mods/worldedit-forge-mc1.12.2-6.1.10-dist.jar
cp "$HOME/MC-Forge12-2/mods/BetterBuildersWands-1.12-0.11.1.245+69d0d70.jar" \
    testclient/runtime/game/mods/BetterBuildersWands-1.12-0.11.1.245+69d0d70.jar
VANDOR_LABS_REPRO_OUT="$RUN_OUT" timeout --kill-after=15 240 testclient/run.sh \
    > "$RUN_OUT/client.log" 2>&1
grep -q '\[vandorlabs\]\[reprolab\] redstone-channel-runtime PASS' "$RUN_OUT/client.log"
grep -q '\[vandorlabs\]\[reprolab\] screen-runtime PASS' "$RUN_OUT/client.log"
python3 testclient/export_gallery.py "$RUN_OUT"
echo "Gallery source run: $RUN_OUT"
