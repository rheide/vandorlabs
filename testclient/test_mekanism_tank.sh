#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
: "${VANDOR_LABS_TEST_MEKANISM:?Set the installed Mekanism test JAR}"
fixture=$(mktemp -d "$ROOT/testclient/mekanism-restart.XXXXXX")
export VANDOR_LABS_TEST_SKIP_BUILD=true
export VANDOR_LABS_TEST_GAME_DIR="$fixture/game"
export VANDOR_LABS_MEKANISM_TANK_RESTART=save
bash testclient/test_viewscreen.sh --focus mekanism-tanks > "$fixture/save.log" 2>&1
cat "$fixture/save.log"
export VANDOR_LABS_MEKANISM_TANK_RESTART=verify
bash testclient/test_viewscreen.sh --focus mekanism-tanks > "$fixture/restart.log" 2>&1
cat "$fixture/restart.log"
echo "Mekanism filled tank cold restart, vehicle reload and native parking passed: $fixture"
