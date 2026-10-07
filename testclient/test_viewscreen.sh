#!/bin/bash
# Build the mod, boot a software-rendered Forge client, and verify pixels/state.
set -euo pipefail

unset VANDOR_LABS_ARMOR_CHECKS_ONLY VANDOR_LABS_MOVED_DOOR_CHECKS_ONLY VANDOR_LABS_CONTROL_ICONS_ONLY VANDOR_LABS_REDSTONE_SCREEN_CHECKS_ONLY VANDOR_LABS_CHANNEL_GUI_CHECKS_ONLY VANDOR_LABS_REDSTONE_SCREEN_FOCUSED VANDOR_LABS_REPRO_SHOT_PREFIX VANDOR_LABS_TRAPDOOR_CHECKS_ONLY VANDOR_LABS_STORAGE_CHECKS_ONLY VANDOR_LABS_DIALOG_CHECKS_ONLY

# Capture scope is explicit: routine fixes use --focus, full regressions use --full.
case "${1:-}" in
    --full) MODE=full; PREFIX= ;;
    --focus)
        MODE=focus
        TARGET=${2:?Usage: test_viewscreen.sh --focus door-selection/trapdoors/dialogs/storage/scene-prefix}
        if [ "$TARGET" = armor ]; then
            PREFIX=armor_
            export VANDOR_LABS_ARMOR_CHECKS_ONLY=true
        elif [ "$TARGET" = trapdoors ]; then
            PREFIX=gallery_trapdoor_followup_
            export VANDOR_LABS_TRAPDOOR_CHECKS_ONLY=true
        elif [ "$TARGET" = door-selection ]; then
            PREFIX=large_door_selection_
            export VANDOR_LABS_MOVED_DOOR_CHECKS_ONLY=true
        elif [ "$TARGET" = signals ]; then
            PREFIX=redstone_screen_
            export VANDOR_LABS_REDSTONE_SCREEN_CHECKS_ONLY=true VANDOR_LABS_REDSTONE_SCREEN_FOCUSED=true
        elif [ "$TARGET" = control-icons ]; then
            PREFIX=controls_
            export VANDOR_LABS_CHANNEL_GUI_CHECKS_ONLY=true VANDOR_LABS_CONTROL_ICONS_ONLY=true
        elif [ "$TARGET" = redstone-dialogs ]; then
            PREFIX=channels_
            export VANDOR_LABS_CHANNEL_GUI_CHECKS_ONLY=true
        elif [ "$TARGET" = dialogs ]; then
            PREFIX=console_gui
            export VANDOR_LABS_DIALOG_CHECKS_ONLY=true
        elif [ "$TARGET" = storage ]; then
            PREFIX=gallery_storage_
            export VANDOR_LABS_STORAGE_CHECKS_ONLY=true
        else
            PREFIX=$TARGET
        fi
        if [ "$TARGET" != dialogs ]; then export VANDOR_LABS_REPRO_SHOT_PREFIX=$PREFIX; fi
        ;;
    *) echo "Usage: test_viewscreen.sh --focus door-selection/trapdoors/dialogs/storage/scene-prefix | --full"; exit 2 ;;
esac

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JAVA8=/usr/lib/jvm/java-8-openjdk-amd64
RUN_OUT=$(mktemp -d "$ROOT/testclient/render-run.XXXXXX")

cd "$ROOT"
BUILD_ARGS=(build --no-daemon)
if [ "${VANDOR_LABS_TEST_OFFLINE:-false}" = true ]; then BUILD_ARGS+=(--offline); fi
JAVA_HOME="$JAVA8" PATH="$JAVA8/bin:$PATH" ./gradlew "${BUILD_ARGS[@]}"
TEST_RUNTIME_GAME=${VANDOR_LABS_TEST_GAME_DIR:-"$ROOT/testclient/runtime/game"}
mkdir -p "$TEST_RUNTIME_GAME/mods"
VERSION=$(sed -n "s/^version = '\([^']*\)'/\1/p" build.gradle | head -1)
TEST_JAR=${VANDOR_LABS_TEST_JAR:-build/libs/vandorlabs-$VERSION.jar}
rm -f "$TEST_RUNTIME_GAME"/mods/vandorlabs-*.jar
sha256sum "$TEST_JAR" > "$RUN_OUT/artifact.sha256"
git rev-parse HEAD > "$RUN_OUT/source-commit.txt"
cp "$TEST_JAR" "$TEST_RUNTIME_GAME/mods/vandorlabs-$VERSION.jar"
# Set VANDOR_LABS_COMPAT_MODS to the external test-mod directory when needed.
COMPAT_MODS=${VANDOR_LABS_COMPAT_MODS:-"$HOME/.minecraft/mods"}
cp "$COMPAT_MODS/worldedit-forge-mc1.12.2-6.1.10-dist.jar" \
    "$TEST_RUNTIME_GAME/mods/worldedit-forge-mc1.12.2-6.1.10-dist.jar"
cp "$COMPAT_MODS/BetterBuildersWands-1.12-0.11.1.245+69d0d70.jar" \
    "$TEST_RUNTIME_GAME/mods/BetterBuildersWands-1.12-0.11.1.245+69d0d70.jar"
cp "$COMPAT_MODS/ImmersiveEngineering-0.12-98.jar" "$TEST_RUNTIME_GAME/mods/"
# Allow the full software-rendered gallery to finish on a busy host.
VANDOR_LABS_REPRO_OUT="$RUN_OUT" timeout "${VANDOR_LABS_TEST_TIMEOUT:-1200}" testclient/run.sh \
    > "$RUN_OUT/client.log" 2>&1
if grep -q 'Exception loading model' "$RUN_OUT/client.log"; then
    echo "FAIL: missing or invalid baked model; see $RUN_OUT/client.log"
    exit 1
fi
if [ "$MODE" = focus ]; then
    if [ "$TARGET" = armor ]; then
        grep -q 'armor-material-centering PASS' "$RUN_OUT/client.log"
        grep -q 'programmable-armor PASS' "$RUN_OUT/client.log"
        grep -q 'role-armor PASS choices=28 icons=28 sets=7' "$RUN_OUT/client.log"
        test -s "$RUN_OUT/shot_armor_picker.png"
        test -s "$RUN_OUT/shot_armor_worn_and_icons.png"
        for role in bioengineer scientist hazmat repairman pilot civilian_staff spaceship_staff; do
            test -s "$RUN_OUT/shot_armor_stand_set_$role.png"
        done
        echo "Live armor checks passed: $RUN_OUT"
        echo "Live-client artifacts: $RUN_OUT"
        exit 0
    fi
    python3 testclient/validate_focused_gallery.py "$RUN_OUT" "$PREFIX" "$TARGET"
    if [ "$TARGET" = redstone-dialogs ] || [ "$TARGET" = control-icons ]; then python3 testclient/analyze_signal_control_icons.py "$RUN_OUT"; fi
    echo "Live-client artifacts: $RUN_OUT"
    exit 0
fi
grep -q '\[vandorlabs\]\[reprolab\] followup-1.2-runtime PASS' "$RUN_OUT/client.log"
grep -q '\[vandorlabs\]\[reprolab\] distant-geometry-runtime PASS' "$RUN_OUT/client.log"
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
python3 testclient/analyze_ramp_rendering.py "$RUN_OUT"
for shape in halfwidth shallow; do
    for layout in horizontal stagger; do
        for motion in rotating sliding inset_sliding; do
            grep -q "diagonal-partial-patch-runtime PASS client patch_${shape}_${layout}_${motion}_open" "$RUN_OUT/client.log"
        done
    done
done
for family in block slab door trapdoor; do
    grep -q "custom-picker-reopen-runtime PASS $family" "$RUN_OUT/client.log"
done
grep -q 'diagonal-sliding-style-gui PASS into wall' "$RUN_OUT/client.log"
grep -q 'diagonal-sliding-style-gui PASS over wall' "$RUN_OUT/client.log"
grep -q 'opposing-next-block-runtime PASS client' "$RUN_OUT/client.log"
grep -q 'next-block-open-placement-runtime PASS' "$RUN_OUT/client.log"
grep -q 'diagonal-staggered-mode-runtime PASS gallery_trapdoor_stagger_halfwidth_sliding_open' "$RUN_OUT/client.log"
grep -q 'diagonal-staggered-mode-runtime PASS gallery_trapdoor_stagger_shallow_rotating_open' "$RUN_OUT/client.log"
grep -q 'diagonal-outward-runtime PASS client' "$RUN_OUT/client.log"
grep -q 'diagonal-trapdoor-hotbar-runtime PASS' "$RUN_OUT/client.log"
grep -q 'duplifier-dialog-layout PASS' "$RUN_OUT/client.log"
grep -q 'trapdoor-side-placement-runtime PASS' "$RUN_OUT/client.log"
grep -q 'trapdoor-offset-copy-neighbor-runtime PASS' "$RUN_OUT/client.log"
grep -q 'offset-trapdoor-hitbox-runtime PASS' "$RUN_OUT/client.log"
grep -q 'trapdoor-group-offset-guard PASS' "$RUN_OUT/client.log"
grep -q 'trapdoor-controls-runtime PASS' "$RUN_OUT/client.log"
grep -q 'sliding-next-mount-overlap-runtime PASS client closed' "$RUN_OUT/client.log"
grep -q 'sliding-next-mount-overlap-runtime PASS client open' "$RUN_OUT/client.log"
grep -q 'vanilla-trapdoor-alignment-runtime PASS client' "$RUN_OUT/client.log"
grep -q 'imported-materials-runtime PASS' "$RUN_OUT/client.log"
grep -q 'retired-texture-fallback PASS' "$RUN_OUT/client.log"
grep -q 'batched-lighting-inputs PASS' "$RUN_OUT/client.log"
grep -q 'trapdoor-movement-hinge-gui PASS' "$RUN_OUT/client.log"
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
