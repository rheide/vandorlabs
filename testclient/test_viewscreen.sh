#!/bin/bash
# Build the mod, boot a software-rendered Forge client, and verify pixels/state.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JAVA8=/usr/lib/jvm/java-8-openjdk-amd64
RUN_OUT=$(mktemp -d "$ROOT/testclient/render-run.XXXXXX")

cd "$ROOT"
JAVA_HOME="$JAVA8" PATH="$JAVA8/bin:$PATH" ./gradlew build --no-daemon
mkdir -p testclient/runtime/game/mods
VERSION=$(sed -n "s/^version = '\([^']*\)'/\1/p" build.gradle | head -1)
TEST_JAR=${VANDOR_LABS_TEST_JAR:-build/libs/vandorlabs-$VERSION.jar}
rm -f testclient/runtime/game/mods/vandorlabs-*.jar
cp "$TEST_JAR" "testclient/runtime/game/mods/vandorlabs-$VERSION.jar"
cp "$HOME/MC-Forge12-2/mods/worldedit-forge-mc1.12.2-6.1.10-dist.jar" \
    testclient/runtime/game/mods/worldedit-forge-mc1.12.2-6.1.10-dist.jar
cp "$HOME/MC-Forge12-2/mods/BetterBuildersWands-1.12-0.11.1.245+69d0d70.jar" \
    testclient/runtime/game/mods/BetterBuildersWands-1.12-0.11.1.245+69d0d70.jar
VANDOR_LABS_REPRO_OUT="$RUN_OUT" timeout 600 testclient/run.sh \
    > "$RUN_OUT/client.log" 2>&1
ANALYZE_ARGS=("$RUN_OUT" --texture-variant)
if [[ "${VANDOR_LABS_TEXTURE_VARIANT:-default}" == "original" ]]; then
    ANALYZE_ARGS=("$RUN_OUT")
fi
python3 testclient/analyze_viewscreen.py "${ANALYZE_ARGS[@]}"
python3 testclient/analyze_space_doors.py "$RUN_OUT"
grep -q '\[vandorlabs\]\[reprolab\] door-runtime PASS' "$RUN_OUT/client.log"
echo "PASS: live door state, pairing, and collision contracts"
grep -q '\[vandorlabs\]\[reprolab\] space-door-settings PASS' "$RUN_OUT/client.log"
echo "PASS: Programmable Door defaults and creative pick-block settings round trips"
grep -q '\[vandorlabs\]\[reprolab\] redstone-channel-runtime PASS' "$RUN_OUT/client.log"
echo "PASS: loaded-block redstone channel propagation and persistence"
grep -q '\[vandorlabs\]\[reprolab\] chair-runtime PASS' "$RUN_OUT/client.log"
echo "PASS: live chair mounting, seat height, and cleanup contracts"
grep -q '\[vandorlabs\]\[reprolab\] controller-runtime PASS' "$RUN_OUT/client.log"
echo "PASS: controller selection, redstone, obstruction and source-restoration contracts"
grep -q '\[vandorlabs\]\[reprolab\] screen-runtime PASS' "$RUN_OUT/client.log"
echo "PASS: programmable screen persistence, packets, and facing contracts"
grep -q '\[vandorlabs\]\[reprolab\] copy-compat-runtime PASS' "$RUN_OUT/client.log"
echo "PASS: WorldEdit and Better Builder's Wands copy contracts"
grep -q '\[vandorlabs\]\[reprolab\] item-runtime PASS' "$RUN_OUT/client.log"
echo "PASS: standalone item registration, shaped crafting, and baked model"
echo "Live-client artifacts: $RUN_OUT"
