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
sha256sum "$TEST_JAR" > "$RUN_OUT/artifact.sha256"
git rev-parse HEAD > "$RUN_OUT/source-commit.txt"
cp "$TEST_JAR" "testclient/runtime/game/mods/vandorlabs-$VERSION.jar"
cp "$HOME/MC-Forge12-2/mods/worldedit-forge-mc1.12.2-6.1.10-dist.jar" \
    testclient/runtime/game/mods/worldedit-forge-mc1.12.2-6.1.10-dist.jar
cp "$HOME/MC-Forge12-2/mods/BetterBuildersWands-1.12-0.11.1.245+69d0d70.jar" \
    testclient/runtime/game/mods/BetterBuildersWands-1.12-0.11.1.245+69d0d70.jar
cp "$HOME/MC-Forge12-2/mods/ImmersiveEngineering-0.12-98.jar" testclient/runtime/game/mods/
# Allow the full software-rendered gallery to finish on a busy host.
VANDOR_LABS_REPRO_OUT="$RUN_OUT" timeout "${VANDOR_LABS_TEST_TIMEOUT:-1200}" testclient/run.sh \
    > "$RUN_OUT/client.log" 2>&1
if grep -q 'Exception loading model' "$RUN_OUT/client.log"; then
    echo "FAIL: missing or invalid baked model; see $RUN_OUT/client.log"
    exit 1
fi
grep -q '\[vandorlabs\]\[reprolab\] followup-1.2-runtime PASS' "$RUN_OUT/client.log"
grep -q '\[vandorlabs\]\[reprolab\] version-1.2-runtime PASS' "$RUN_OUT/client.log"
grep -q '\[vandorlabs\]\[reprolab\] face-textures-runtime PASS' "$RUN_OUT/client.log"
grep -q '\[vandorlabs\]\[reprolab\] duplifier-connected-runtime PASS' "$RUN_OUT/client.log"
grep -q '\[vandorlabs\]\[reprolab\] duplifier-options-gui PASS' "$RUN_OUT/client.log"
grep -q '\[vandorlabs\]\[reprolab\] duplifier-mode-icon PASS' "$RUN_OUT/client.log"
grep -q 'trapdoor-assembly PASS flat_rotating_open' "$RUN_OUT/client.log"
grep -q 'trapdoor-assembly PASS rectangle_rotating_open' "$RUN_OUT/client.log"
grep -q 'trapdoor-assembly PASS v_sliding_open' "$RUN_OUT/client.log"
grep -q 'trapdoor-assembly PASS stagger_sliding_open' "$RUN_OUT/client.log"
python3 testclient/analyze_viewscreen.py "$RUN_OUT" --texture-variant
python3 testclient/analyze_space_doors.py "$RUN_OUT"
python3 testclient/analyze_landing_gear.py "$RUN_OUT"
python3 testclient/analyze_seat_icons.py "$RUN_OUT"
python3 testclient/analyze_light_and_glass.py "$RUN_OUT"
python3 testclient/analyze_diagonal_joins.py "$RUN_OUT"
grep -q 'trapdoor-controls-runtime PASS' "$RUN_OUT/client.log"
grep -q 'trapdoor-material-runtime PASS diagonal_custom_door_tile' "$RUN_OUT/client.log"
grep -q 'trapdoor-material-runtime PASS flat_custom_door_tile' "$RUN_OUT/client.log"
grep -q 'light-hidden-faces PASS' "$RUN_OUT/client.log"
grep -q 'placement-texture-and-distance PASS' "$RUN_OUT/client.log"
grep -q 'diagonal-lighting-data PASS' "$RUN_OUT/client.log"
grep -q 'diagonal-fill-textures PASS' "$RUN_OUT/client.log"
grep -q 'diagonal-direction-gui PASS' "$RUN_OUT/client.log"
grep -q 'ramp-texture-option PASS' "$RUN_OUT/client.log"
grep -q 'ramp-matching-gui PASS' "$RUN_OUT/client.log"
grep -q 'redstone-load-safety PASS' "$RUN_OUT/client.log"
echo "PASS: optional ramp texture matching and redstone load safety"
echo "PASS: filled diagonal faces use the main texture; slope dialog preserves the upper half"
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
grep -q '\[vandorlabs\]\[reprolab\] light-picker-runtime PASS' "$RUN_OUT/client.log"
grep -q '\[vandorlabs\]\[reprolab\] custom-materials-runtime PASS' "$RUN_OUT/client.log"
grep -q '\[vandorlabs\]\[reprolab\] screen-runtime PASS' "$RUN_OUT/client.log"
echo "PASS: programmable screen persistence, packets, and facing contracts"
grep -q '\[vandorlabs\]\[reprolab\] copy-compat-runtime PASS' "$RUN_OUT/client.log"
echo "PASS: WorldEdit and Better Builder's Wands copy contracts"
grep -q '\[vandorlabs\]\[reprolab\] item-runtime PASS' "$RUN_OUT/client.log"
echo "PASS: standalone item registration, shaped crafting, and baked model"
echo "Live-client artifacts: $RUN_OUT"
