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
mkdir -p game
exec xvfb-run -a --server-args="-screen 0 1280x720x24 -ac +extension GLX +render -noreset" \
  env LIBGL_ALWAYS_SOFTWARE=1 \
  "$JAVA" -Xmx2G \
  -Dvandorlabs.duplifierChecksOnly="${VANDOR_LABS_DUPLIFIER_CHECKS_ONLY:-false}" \
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
  --gameDir "$PWD/game" --assetsDir "$PWD/assets" --assetIndex 1.12 \
  --username REPROBOT --accessToken duck0000 --version "1.12.2-Forge14.23.5.2864" \
  --fml.forgeVersion 14.23.5.2864 --fml.mcVersion 1.12.2 --fml.forgeGroup net.minecraftforge \
  --width 1280 --height 720 "$@"
