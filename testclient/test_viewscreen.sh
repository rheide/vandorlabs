#!/bin/bash
# Build the mod, boot a software-rendered Forge client, and verify pixels/state.
set -euo pipefail
unset VANDOR_LABS_PILOT_SEAT_CHECKS_ONLY VANDOR_LABS_FLUID_CHECKS_ONLY VANDOR_LABS_COCKPIT_CHECKS_ONLY VANDOR_LABS_GLASS_APPEARANCE_CHECKS_ONLY
unset VANDOR_LABS_CREATIVE_CATALOG_CHECKS_ONLY VANDOR_LABS_RENDER_REGRESSION_CHECKS_ONLY VANDOR_LABS_EXISTING_VEHICLE_HOVER_CHECKS_ONLY
unset VANDOR_LABS_REACTOR_GLASS_CHECKS_ONLY VANDOR_LABS_FOREIGN_BUILDING_CHECKS_ONLY VANDOR_LABS_VEHICLE_TAXI_CHECKS_ONLY VANDOR_LABS_VEHICLE_RAMP_CHECKS_ONLY VANDOR_LABS_ARM_LIVE_CHECKS
unset VANDOR_LABS_ARM_START_ONLY VANDOR_LABS_ARM_PUSH_ONLY VANDOR_LABS_SIGN_CHECKS_ONLY
unset VANDOR_LABS_TAXI_LANDING27_CHECKS_ONLY
unset VANDOR_LABS_ARTWORK27_CHECKS_ONLY
unset VANDOR_LABS_COPY_SURFACE_CHECKS_ONLY VANDOR_LABS_UTILITY26_CHECKS_ONLY VANDOR_LABS_BRIDGE_MACHINE_CHECKS_ONLY VANDOR_LABS_MACHINE_BUILD_DOCS_ONLY

unset VANDOR_LABS_CANOPY_CHECKS_ONLY VANDOR_LABS_ARMOR_DUPLIFIER_ONLY VANDOR_LABS_ARMOR_TOOLS_ONLY VANDOR_LABS_ARMOR_CHECKS_ONLY VANDOR_LABS_MOVED_DOOR_CHECKS_ONLY VANDOR_LABS_CONTROL_ICONS_ONLY VANDOR_LABS_REDSTONE_SCREEN_CHECKS_ONLY VANDOR_LABS_CHANNEL_GUI_CHECKS_ONLY VANDOR_LABS_REDSTONE_SCREEN_FOCUSED VANDOR_LABS_REPRO_SHOT_PREFIX VANDOR_LABS_TRAPDOOR_CHECKS_ONLY VANDOR_LABS_STORAGE_CHECKS_ONLY VANDOR_LABS_DIALOG_CHECKS_ONLY

# Capture scope is explicit: routine fixes use --focus, full regressions use --full.
case "${1:-}" in
    --full) MODE=full; PREFIX= ;;
    --focus)
        MODE=focus
        TARGET=${2:?Usage: test_viewscreen.sh --focus door-selection/trapdoors/dialogs/storage/scene-prefix}
        if [ "$TARGET" = vehicle-signs ]; then
            PREFIX=vehicle_sign_
            export VANDOR_LABS_SIGN_CHECKS_ONLY=true
        elif [ "$TARGET" = reactor-glass ]; then
            PREFIX=reactor_glass_
            export VANDOR_LABS_REACTOR_GLASS_CHECKS_ONLY=true
        elif [ "$TARGET" = foreign-buildings ]; then
            PREFIX=foreign_building_
            export VANDOR_LABS_FOREIGN_BUILDING_CHECKS_ONLY=true
        elif [ "$TARGET" = vehicle-taxi ]; then
            PREFIX=vehicle_taxi_
            export VANDOR_LABS_VEHICLE_TAXI_CHECKS_ONLY=true
        elif [ "$TARGET" = vehicle-ramps ]; then
            PREFIX=vehicle_ramp_
            export VANDOR_LABS_VEHICLE_RAMP_CHECKS_ONLY=true
        elif [ "$TARGET" = copy-surfaces ]; then
            PREFIX=copy_surface_
            export VANDOR_LABS_COPY_SURFACE_CHECKS_ONLY=true
        elif [ "$TARGET" = arm-docs ]; then
            PREFIX=joined_arm_
            export VANDOR_LABS_JOINED_ARM_DOCS=true
        elif [ "$TARGET" = mekanism-tanks ]; then
            PREFIX=mekanism_tank_
            export VANDOR_LABS_ARM_LIVE_CHECKS=true VANDOR_LABS_MEKANISM_TANK_ONLY=true
        elif [ "$TARGET" = arm-start ]; then
            PREFIX=arm_start_
            export VANDOR_LABS_ARM_LIVE_CHECKS=true VANDOR_LABS_ARM_START_ONLY=true
        elif [ "$TARGET" = arm-push ]; then
            PREFIX=arm_push_
            export VANDOR_LABS_ARM_LIVE_CHECKS=true VANDOR_LABS_ARM_PUSH_ONLY=true
        elif [ "$TARGET" = arm-improvements ]; then
            PREFIX=arm_improvements_
            export VANDOR_LABS_ARM_LIVE_CHECKS=true VANDOR_LABS_ARM_IMPROVEMENTS_ONLY=true
        elif [ "$TARGET" = arm-wall ]; then
            PREFIX=arm_wall_
            export VANDOR_LABS_ARM_LIVE_CHECKS=true VANDOR_LABS_ARM_WALL_ONLY=true
        elif [ "$TARGET" = arm-placement ]; then
            PREFIX=arm_placement_
            export VANDOR_LABS_ARM_LIVE_CHECKS=true VANDOR_LABS_ARM_PLACEMENT_ONLY=true
        elif [ "$TARGET" = arm-riders ]; then
            PREFIX=arm_riders_
            export VANDOR_LABS_ARM_LIVE_CHECKS=true VANDOR_LABS_ARM_RIDER_ONLY=true
        elif [ "$TARGET" = arm-endpoints ]; then
            PREFIX=arm_endpoint_
            export VANDOR_LABS_ARM_LIVE_CHECKS=true VANDOR_LABS_ARM_JOINED_ONLY=true VANDOR_LABS_ARM_ENDPOINT_ONLY=true
        elif [ "$TARGET" = arm-joined ]; then
            PREFIX=arm_joined_
            export VANDOR_LABS_ARM_LIVE_CHECKS=true VANDOR_LABS_ARM_JOINED_ONLY=true
        elif [ "$TARGET" = arm-mining ]; then
            PREFIX=arm_mining_
            export VANDOR_LABS_ARM_LIVE_CHECKS=true VANDOR_LABS_ARM_MINING_ONLY=true
        elif [ "$TARGET" = arm-live ]; then
            PREFIX=arm_live_
            export VANDOR_LABS_ARM_LIVE_CHECKS=true
        elif [ "$TARGET" = arm-recovery ]; then
            PREFIX=arm_recovery_
            : "${VANDOR_LABS_ARM_RECOVERY_PHASE:?Set the isolated arm recovery phase}"
        elif [ "$TARGET" = taxi-landing27 ]; then
            PREFIX=taxi_landing27_
            export VANDOR_LABS_TAXI_LANDING27_CHECKS_ONLY=true
        elif [ "$TARGET" = artwork27 ]; then
            PREFIX=artwork27_
            export VANDOR_LABS_ARTWORK27_CHECKS_ONLY=true
        elif [ "$TARGET" = utility26 ]; then
            PREFIX=utility26_
            export VANDOR_LABS_UTILITY26_CHECKS_ONLY=true
        elif [ "$TARGET" = bridge-machines ]; then
            PREFIX=bridge_machine_
            export VANDOR_LABS_BRIDGE_MACHINE_CHECKS_ONLY=true
        elif [ "$TARGET" = machine-build-docs ]; then
            PREFIX=machine_build_
            export VANDOR_LABS_MACHINE_BUILD_DOCS_ONLY=true
        elif [ "$TARGET" = render-regressions ]; then
            PREFIX=regression_
            export VANDOR_LABS_RENDER_REGRESSION_CHECKS_ONLY=true
        elif [ "$TARGET" = actual-plate-hover ]; then
            PREFIX=actual_plate_
            export VANDOR_LABS_EXISTING_VEHICLE_HOVER_CHECKS_ONLY=true
        elif [ "$TARGET" = creative-catalog ]; then
            PREFIX=catalog_
            export VANDOR_LABS_CREATIVE_CATALOG_CHECKS_ONLY=true
        elif [ "$TARGET" = fluids ]; then
            PREFIX=fluid_
            export VANDOR_LABS_FLUID_CHECKS_ONLY=true
        elif [ "$TARGET" = cockpit ]; then
            PREFIX=cockpit_
            export VANDOR_LABS_COCKPIT_CHECKS_ONLY=true
        elif [ "$TARGET" = glass-appearance ]; then
            PREFIX=glass_
            export VANDOR_LABS_GLASS_APPEARANCE_CHECKS_ONLY=true
        elif [ "$TARGET" = pilot-seat ]; then
            PREFIX=pilot_seat_
            export VANDOR_LABS_PILOT_SEAT_CHECKS_ONLY=true
        elif [ "$TARGET" = canopies ]; then
            PREFIX=canopy_
            export VANDOR_LABS_CANOPY_CHECKS_ONLY=true
        elif [ "$TARGET" = armor ] || [ "$TARGET" = armor-tools ] || [ "$TARGET" = armor-duplifier ]; then
            PREFIX=armor_
            export VANDOR_LABS_ARMOR_CHECKS_ONLY=true
            if [ "$TARGET" = armor-duplifier ]; then export VANDOR_LABS_ARMOR_DUPLIFIER_ONLY=true; fi
            if [ "$TARGET" = armor-tools ]; then export VANDOR_LABS_ARMOR_TOOLS_ONLY=true; fi
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
if [ "${TARGET:-}" = arm-live ]; then export VANDOR_LABS_ARM_SECOND_FIXTURE=${VANDOR_LABS_ARM_SECOND_FIXTURE:-"$ROOT/testclient/fixtures/arm-rotating-replacement-2.6.nbt"}; export VANDOR_LABS_ARM_SAVED_FIXTURE=${VANDOR_LABS_ARM_SAVED_FIXTURE:-"$ROOT/testclient/fixtures/arm-pending-detach-2.6.nbt"}; fi
BUILD_ARGS=(build --no-daemon)
if [ "${VANDOR_LABS_TEST_OFFLINE:-false}" = true ]; then BUILD_ARGS+=(--offline); fi
if [ "${VANDOR_LABS_TEST_SKIP_BUILD:-false}" != true ]; then
    JAVA_HOME="$JAVA8" PATH="$JAVA8/bin:$PATH" ./gradlew "${BUILD_ARGS[@]}"
fi
TEST_RUNTIME_GAME=${VANDOR_LABS_TEST_GAME_DIR:-"$RUN_OUT/game"}
export VANDOR_LABS_TEST_GAME_DIR="$TEST_RUNTIME_GAME"
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
# Optional Mekanism fixtures for supported block rotation, enabled explicitly.
if [ -n "${VANDOR_LABS_TEST_MEKANISM:-}" ]; then
    cp "$VANDOR_LABS_TEST_MEKANISM" "$TEST_RUNTIME_GAME/mods/"
fi
# Optional renderer compatibility run, kept separate from the baseline client.
if [ -n "${VANDOR_LABS_TEST_JEI:-}" ]; then
    cp "$VANDOR_LABS_TEST_JEI" "$TEST_RUNTIME_GAME/mods/"
fi
if [ -n "${VANDOR_LABS_TEST_OPTIFINE:-}" ]; then
    cp "$VANDOR_LABS_TEST_OPTIFINE" "$TEST_RUNTIME_GAME/mods/"
fi
# Allow the full software-rendered gallery to finish on a busy host.
VANDOR_LABS_REPRO_OUT="$RUN_OUT" timeout "${VANDOR_LABS_TEST_TIMEOUT:-1200}" testclient/run.sh \
    > "$RUN_OUT/client.log" 2>&1
if grep -Eq 'Exception loading model|Problem: broken aspect ratio' "$RUN_OUT/client.log"; then
    echo "FAIL: missing or invalid baked model; see $RUN_OUT/client.log"
    exit 1
fi
if [ "$MODE" = focus ]; then
    if [ "$TARGET" = arm-docs ]; then
        grep -q "joined-arm-docs PASS" "$RUN_OUT/client.log"
        echo "Joined arm documentation captured: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = mekanism-tanks ]; then
        if [ "${VANDOR_LABS_MEKANISM_TANK_RESTART:-}" = save ]; then grep -q "mekanism-tank-save PASS" "$RUN_OUT/client.log"; else grep -q "mekanism-tank PASS" "$RUN_OUT/client.log"; fi
        echo "Native Mekanism tank vehicle checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = reactor-glass ]; then
        grep -q "reactor-glass PASS" "$RUN_OUT/client.log"
        echo "Standalone reactor glass checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = foreign-buildings ]; then
        grep -q "foreign-building-live PASS" "$RUN_OUT/client.log"
        echo "Foreign building live checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = vehicle-taxi ]; then
        grep -q "vehicle-taxi PASS" "$RUN_OUT/client.log"
        echo "Vehicle Taxi checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = vehicle-ramps ]; then
        grep -q "vehicle-ramps PASS" "$RUN_OUT/client.log"
        echo "Saved vehicle ramp checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = copy-surfaces ]; then
        grep -q "copy-surfaces PASS" "$RUN_OUT/client.log"
        echo "Copy surface checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = arm-improvements ]; then
        grep -q "arm-improvements PASS" "$RUN_OUT/client.log"
        echo "Arm improvements passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = arm-wall ]; then
        grep -q "arm-wall PASS" "$RUN_OUT/client.log"
        echo "Saved wall arm checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = arm-placement ]; then
        grep -q "arm-placement PASS" "$RUN_OUT/client.log"
        echo "Arm placement and joining checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = arm-riders ]; then
        grep -q "arm-rider PASS" "$RUN_OUT/client.log"
        echo "Arm rider checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = vehicle-signs ]; then
        grep -q "vehicle-sign-render PASS" "$RUN_OUT/client.log"
        grep -q "vehicle-painting-render PASS" "$RUN_OUT/client.log"
        echo "Carried sign checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = arm-start ]; then
        grep -q "arm-start PASS" "$RUN_OUT/client.log"
        echo "Arm starting-angle checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = arm-push ]; then
        grep -q "arm-push PASS" "$RUN_OUT/client.log"
        echo "Incremental arm push checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = arm-endpoints ]; then
        grep -q "joined-arm PASS arm-endpoint" "$RUN_OUT/client.log"
        echo "Arm endpoint checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = arm-joined ]; then
        grep -q "joined-arm PASS" "$RUN_OUT/client.log"
        echo "Joined arm checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = arm-mining ]; then
        grep -q "arm-payload-mining PASS" "$RUN_OUT/client.log"
        echo "Arm payload mining passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = arm-live ]; then
        grep -q "arm-live PASS" "$RUN_OUT/client.log"
        grep -q "arm-saved PASS" "$RUN_OUT/client.log"
        grep -q "arm-replacement PASS" "$RUN_OUT/client.log"
        grep -q "arm-payload-mining PASS" "$RUN_OUT/client.log"
        echo "Live arm checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = arm-recovery ]; then
        grep -q "arm-durable-recovery PASS" "$RUN_OUT/client.log"
        echo "Arm restart recovery passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = taxi-landing27 ]; then
        grep -q "taxi-landing27 PASS" "$RUN_OUT/client.log"
        echo "Taxi landing gear checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = artwork27 ]; then
        grep -q "artwork27-focused PASS" "$RUN_OUT/client.log"
        grep -q "shared-screen-transmitter PASS" "$RUN_OUT/client.log"
        echo "Live artwork 2.7 checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = utility26 ]; then
        grep -q "utility26-focused PASS" "$RUN_OUT/client.log"
        echo "Live utility 2.6 checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = bridge-machines ]; then
        grep -q 'bridge-machines PASS' "$RUN_OUT/client.log"
        grep -q 'integrated-chip-mounts PASS' "$RUN_OUT/client.log"
        grep -q 'navigation-tables PASS' "$RUN_OUT/client.log"
        grep -q 'navigation-display-inputs PASS' "$RUN_OUT/client.log"
        grep -q 'navigation-display-client PASS' "$RUN_OUT/client.log"
        grep -q 'constructor-render-lifetime PASS' "$RUN_OUT/client.log"
        grep -q 'machine-display-inputs PASS' "$RUN_OUT/client.log"
        grep -q 'machine-display-pixels PASS' "$RUN_OUT/client.log"
        grep -q 'machine-display-client PASS' "$RUN_OUT/client.log"
        grep -q 'reverse-hover-steering PASS' "$RUN_OUT/client.log"
        echo "Live bridge machine regressions passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = machine-build-docs ]; then
        grep -q 'machine-build-docs PASS' "$RUN_OUT/client.log"
        echo "Machine build documentation captured: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = render-regressions ]; then
        grep -q 'render-regressions PASS' "$RUN_OUT/client.log"
        grep -q 'vehicle-interior-visibility PASS' "$RUN_OUT/client.log"
        if [ -n "${VANDOR_LABS_TEST_OPTIFINE:-}" ]; then grep -q 'OptiFine shadow flag=true' "$RUN_OUT/client.log"; fi
        grep -q 'vehicle-vertex-format-live PASS' "$RUN_OUT/client.log"
        grep -q 'diagonal-filled-caps PASS' "$RUN_OUT/client.log"
        grep -q 'ramp-active-face-models PASS' "$RUN_OUT/client.log"
        grep -q 'vehicle-status-controller-live PASS' "$RUN_OUT/client.log"
        echo "Live render regressions passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = actual-plate-hover ]; then
        grep -q 'actual-plate-hover PASS' "$RUN_OUT/client.log"
        echo "Actual plate Hover checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = creative-catalog ]; then
        grep -q 'creative-catalog PASS' "$RUN_OUT/client.log"
        if [ -n "${VANDOR_LABS_TEST_JEI:-}" ]; then
            grep -q 'creative-catalog-JEI-live PASS' "$RUN_OUT/client.log"
        fi
        echo "Live creative catalog checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = fluids ]; then
        grep -q 'mount-company-filter PASS' "$RUN_OUT/client.log"
        grep -q 'mount-filter-live PASS' "$RUN_OUT/client.log"
        grep -q 'stair-appearance PASS' "$RUN_OUT/client.log"
        grep -q 'diagonal-trapdoor-direction-dialog PASS' "$RUN_OUT/client.log"
        grep -q 'fluid-render-bounds PASS cases=132 cameraViews=10' "$RUN_OUT/client.log"
        grep -q 'fluid-lightmap PASS cases=256' "$RUN_OUT/client.log"
        grep -q 'fluid-boundary-matrix PASS' "$RUN_OUT/client.log"
        grep -q 'submerged-contact-views PASS' "$RUN_OUT/client.log"
        grep -q 'fluid-pool PASS' "$RUN_OUT/client.log"
        grep -q 'fluid-focused PASS' "$RUN_OUT/client.log"
        echo "Live fluid checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = cockpit ]; then
        grep -q 'cockpit-controls PASS' "$RUN_OUT/client.log"
        grep -q 'cockpit-linked-screens PASS' "$RUN_OUT/client.log"
        grep -q 'cockpit-redstone-render PASS' "$RUN_OUT/client.log"
        grep -q 'cockpit-control-dialog PASS' "$RUN_OUT/client.log"
        grep -q 'vehicle-status-autopilot-live PASS' "$RUN_OUT/client.log"
        grep -q 'cockpit-network-clock-live PASS' "$RUN_OUT/client.log"
        grep -q 'plain-canopy-glass-pilot PASS cases=16' "$RUN_OUT/client.log"
        grep -q 'cockpit-wall-canopy PASS cases=6' "$RUN_OUT/client.log"
        grep -q 'cockpit-focused PASS' "$RUN_OUT/client.log"
        echo "Live cockpit checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = glass-appearance ]; then
        python3 testclient/analyze_diagonal_frame_edges.py "$RUN_OUT"
        grep -q 'glass-appearance-live PASS' "$RUN_OUT/client.log"
        echo "Live glass checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = pilot-seat ]; then
        grep -q "pilot-seat-world PASS" "$RUN_OUT/client.log"
        grep -q "pilot-seat-model PASS" "$RUN_OUT/client.log"
        grep -q "pilot-seat-live PASS" "$RUN_OUT/client.log"
        for name in icons facings mounted; do test -s "$RUN_OUT/shot_pilot_seat_$name.png"; done
        python3 testclient/analyze_pilot_seat.py "$RUN_OUT"
        echo "Live Pilot Seat checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = canopies ]; then
        grep -q "canopy-world PASS" "$RUN_OUT/client.log"
        grep -q "canopy-saved-collision PASS" "$RUN_OUT/client.log"
        grep -q "canopy-three-wide PASS" "$RUN_OUT/client.log"
        grep -q "canopy-surfaces PASS" "$RUN_OUT/client.log"
        grep -q "canopy-harvest-clicks PASS" "$RUN_OUT/client.log"
        grep -q "canopy-icons PASS" "$RUN_OUT/client.log"
        grep -q "canopy-live PASS" "$RUN_OUT/client.log"
        for name in closed open designs icons side_trim side_trim_shifted; do test -s "$RUN_OUT/shot_canopy_$name.png"; done
        echo "Live canopy checks passed: $RUN_OUT"
        exit 0
    fi
    if [ "$TARGET" = armor ] || [ "$TARGET" = armor-tools ] || [ "$TARGET" = armor-duplifier ]; then
        grep -q 'armor-duplifier-matrix PASS native=192' "$RUN_OUT/client.log"
        grep -q 'armor-custom-picker PASS cases=4' "$RUN_OUT/client.log"
        grep -q 'armor-duplifier-interactions PASS cases=6' "$RUN_OUT/client.log"
        if [ "$TARGET" = armor-duplifier ]; then
            echo "Live armor Duplifier checks passed: $RUN_OUT"
            exit 0
        fi
        grep -q 'armor-world-sampling PASS cases=25' "$RUN_OUT/client.log"
        grep -q 'armor-stand-configuration PASS cases=8' "$RUN_OUT/client.log"
        if [ "$TARGET" = armor-tools ]; then
            echo "Live armor tool checks passed: $RUN_OUT"
            exit 0
        fi
        grep -q 'armor-material-centering PASS' "$RUN_OUT/client.log"
        grep -q 'programmable-armor PASS' "$RUN_OUT/client.log"
        grep -q 'role-armor PASS choices=48 icons=48 sets=16' "$RUN_OUT/client.log"
        test -s "$RUN_OUT/shot_armor_picker.png"
        test -s "$RUN_OUT/shot_armor_worn_and_icons.png"
        for role in bioengineer scientist hazmat repairman pilot civilian_staff spaceship_staff security_rescue; do
            test -s "$RUN_OUT/shot_armor_stand_set_$role.png"
            test -s "$RUN_OUT/shot_armor_stand_set_${role}_open.png"
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
grep -q '\[vandorlabs\]\[reprolab\] pilot-seat-world PASS' "$RUN_OUT/client.log"
grep -q '\[vandorlabs\]\[reprolab\] pilot-seat-model PASS' "$RUN_OUT/client.log"
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
