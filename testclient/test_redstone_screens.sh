#!/bin/bash
# Exercise redstone-screen rows through real Forge clicks, dialogs and packets.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 ./gradlew build --no-daemon
RUN_OUT=$(mktemp -d "$ROOT/testclient/render-channel.XXXXXX")
VERSION=$(sed -n "s/^version = '\([^']*\)'/\1/p" build.gradle | head -1)
SCREEN_GAME_DIR="$ROOT/testclient/runtime/game"
if [ -n "${VANDOR_LABS_OPTIFINE_JAR:-}" ]; then
    SCREEN_GAME_DIR="$RUN_OUT/game"
    mkdir -p "$SCREEN_GAME_DIR/mods" "$SCREEN_GAME_DIR/config"
    cp testclient/runtime/game/mods/*.jar "$SCREEN_GAME_DIR/mods/"
    cp "$VANDOR_LABS_OPTIFINE_JAR" "$SCREEN_GAME_DIR/mods/"
    cp testclient/runtime/game/options.txt "$SCREEN_GAME_DIR/options.txt"
    cp testclient/runtime/game/config/forge.cfg "$SCREEN_GAME_DIR/config/forge.cfg"
    echo 'shaderPack=OFF' > "$SCREEN_GAME_DIR/optionsshaders.txt"
fi
rm -f "$SCREEN_GAME_DIR"/mods/vandorlabs-*.jar
cp "build/libs/vandorlabs-$VERSION.jar" "$SCREEN_GAME_DIR/mods/"
VANDOR_LABS_REDSTONE_SCREEN_CHECKS_ONLY=true VANDOR_LABS_TEST_GAME_DIR="$SCREEN_GAME_DIR" VANDOR_LABS_REPRO_OUT="$RUN_OUT" timeout 600 testclient/run.sh > "$RUN_OUT/client.log" 2>&1
rg -q 'redstone-screen-runtime PASS shape=27' "$RUN_OUT/client.log"
rg -q 'integrated-screen-duplifier-runtime PASS' "$RUN_OUT/client.log"
if [ "${VANDOR_LABS_REDSTONE_SCREEN_FOCUSED:-false}" != true ]; then rg -q 'propulsion-particle-gui-runtime PASS' "$RUN_OUT/client.log"; fi
echo "Live redstone-screen artifacts: $RUN_OUT"
