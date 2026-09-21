#!/bin/bash
# Headless Forge 1.12.2 client launcher for Vandor Labs rendering tests.
set -e
REPRO_OUT=${VANDOR_LABS_REPRO_OUT:-"$(dirname "$0")/shots"}
mkdir -p "$REPRO_OUT"
REPRO_OUT=$(readlink -f "$REPRO_OUT")
cd "$(dirname "$0")/runtime"
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin/java
CP=$(paste -sd: cp.txt)
mkdir -p game
exec xvfb-run -a --server-args="-screen 0 1280x720x24 -ac +extension GLX +render -noreset" \
  env LIBGL_ALWAYS_SOFTWARE=1 \
  "$JAVA" -Xmx2G \
  -Dforge.logging.console.level=info \
  -Dvandorlabs.reprolab="$REPRO_OUT" \
  -Djava.library.path="$PWD/natives" \
  -cp "$CP" net.minecraft.launchwrapper.Launch \
  --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker \
  --gameDir "$PWD/game" --assetsDir "$PWD/assets" --assetIndex 1.12 \
  --username REPROBOT --accessToken duck0000 --version "1.12.2-Forge14.23.5.2864" \
  --fml.forgeVersion 14.23.5.2864 --fml.mcVersion 1.12.2 --fml.forgeGroup net.minecraftforge \
  --width 1280 --height 720 "$@"
