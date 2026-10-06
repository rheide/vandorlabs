#!/bin/bash
# Headless Forge 1.12.2 client launcher for Vandor Labs rendering tests.
set -e
REPRO_OUT=${VANDOR_LABS_REPRO_OUT:-"$(dirname "$0")/shots"}
mkdir -p "$REPRO_OUT"
REPRO_OUT=$(readlink -f "$REPRO_OUT")
cd "$(dirname "$0")/runtime"
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin/java
CP=$(paste -sd: cp.txt)
# IE uses Forge's Trove 3 collection classes, absent from the minimal vanilla classpath.
TROVE_JAR=$(rg --files "$HOME/.gradle/caches/modules-2/files-2.1/net.sf.trove4j/trove4j/3.0.3" | rg '/trove4j-3.0.3.jar$' | head -n 1)
CP="$CP:$TROVE_JAR"
BENCH_GAME_DIR=${VANDOR_LABS_TEST_GAME_DIR:-"$PWD/game"}
mkdir -p "$BENCH_GAME_DIR"
CAPTURE_JVM=()
if [ "${VANDOR_LABS_DOCUMENTATION_ANIMATIONS:-false}" = true ] || [ "${VANDOR_LABS_DOCUMENTATION_DOOR_GIF:-false}" = true ]; then
  CAPTURE_JVM=(-XX:+UseG1GC -XX:+PrintGCDetails -XX:+PrintGCTimeStamps "-Xloggc:$REPRO_OUT/gc.log")
fi
exec xvfb-run -a --server-args="-screen 0 1280x720x24 -ac +extension GLX +render -noreset" \
  env LIBGL_ALWAYS_SOFTWARE=1 \
  "$JAVA" "-Xmx${VANDOR_LABS_TEST_HEAP:-2G}" "${CAPTURE_JVM[@]}" \
  -Dvandorlabs.worldBenchmark="${VANDOR_LABS_WORLD_BENCHMARK:-false}" \
  -Dvandorlabs.worldBenchmarkReload="${VANDOR_LABS_WORLD_RELOAD:-false}" \
  -Dvandorlabs.duplifierChecksOnly="${VANDOR_LABS_DUPLIFIER_CHECKS_ONLY:-false}" \
  -Dvandorlabs.reproShotPrefix="${VANDOR_LABS_REPRO_SHOT_PREFIX:-}" \
  -Dvandorlabs.storageChecksOnly="${VANDOR_LABS_STORAGE_CHECKS_ONLY:-false}" \
  -Dvandorlabs.redstoneScreenFocused="${VANDOR_LABS_REDSTONE_SCREEN_FOCUSED:-false}" \
  -Dvandorlabs.redstoneScreenChecksOnly="${VANDOR_LABS_REDSTONE_SCREEN_CHECKS_ONLY:-false}" \
  -Dvandorlabs.controlIconsOnly="${VANDOR_LABS_CONTROL_ICONS_ONLY:-false}" \
  -Dvandorlabs.channelGuiChecksOnly="${VANDOR_LABS_CHANNEL_GUI_CHECKS_ONLY:-false}" \
  -Dvandorlabs.dialogChecksOnly="${VANDOR_LABS_DIALOG_CHECKS_ONLY:-false}" \
  -Dvandorlabs.documentationDoorGif="${VANDOR_LABS_DOCUMENTATION_DOOR_GIF:-false}" \
  -Dvandorlabs.documentationAnimations="${VANDOR_LABS_DOCUMENTATION_ANIMATIONS:-false}" \
  -Dvandorlabs.documentationAnimationFilter="${VANDOR_LABS_DOCUMENTATION_ANIMATION_FILTER:-}" \
  -Dvandorlabs.trapdoorChecksOnly="${VANDOR_LABS_TRAPDOOR_CHECKS_ONLY:-false}" \
  -Dvandorlabs.lightPickerChecksOnly="${VANDOR_LABS_LIGHT_PICKER_CHECKS_ONLY:-false}" \
  -Dvandorlabs.lightChecksOnly="${VANDOR_LABS_LIGHT_CHECKS_ONLY:-false}" \
  -Dvandorlabs.benchmarkOnly="${VANDOR_LABS_BENCHMARK_ONLY:-false}" \
  -Dvandorlabs.renderBenchmark="${VANDOR_LABS_RENDER_BENCHMARK:-false}" \
  -Dforge.logging.console.level=info \
  -Dvandorlabs.reprolab="$REPRO_OUT" \
  -Djava.library.path="$PWD/natives" \
  -cp "$CP" net.minecraft.launchwrapper.Launch \
  --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker \
  --gameDir "$BENCH_GAME_DIR" --assetsDir "$PWD/assets" --assetIndex 1.12 \
  --username REPROBOT --accessToken duck0000 --version "1.12.2-Forge14.23.5.2864" \
  --fml.forgeVersion 14.23.5.2864 --fml.mcVersion 1.12.2 --fml.forgeGroup net.minecraftforge \
  --width "${VANDOR_LABS_REPRO_WIDTH:-1280}" --height "${VANDOR_LABS_REPRO_HEIGHT:-720}" "$@"
