#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
: "${VANDOR_LABS_TEST_MEKANISM:?Set the installed Mekanism test JAR}"
run_root=${VANDOR_LABS_TEST_RUN_ROOT:-"$ROOT/testclient"}
mkdir -p "$run_root"
fixture=$(mktemp -d "$run_root/mekanism-restart.XXXXXX")
lease=$(python3 "$ROOT/tools/test_artifacts.py" begin --run-dir "$fixture" --pid "$$" --disposable game)
finish_mekanism_artifacts() {
    local result=$?
    trap - EXIT
    python3 "$ROOT/tools/test_artifacts.py" finish --run-dir "$fixture" --lease "$lease" --exit-code "$result" || \
        echo "Mekanism artifact retention preserved run: $fixture" >&2
    exit "$result"
}
trap finish_mekanism_artifacts EXIT
export VANDOR_LABS_TEST_SKIP_BUILD=true
export VANDOR_LABS_TEST_GAME_DIR="$fixture/game"
export VANDOR_LABS_MEKANISM_TANK_RESTART=save
python3 "$ROOT/tools/test_artifacts.py" run --run-dir "$fixture" --lease "$lease" -- bash testclient/test_viewscreen.sh --focus mekanism-tanks > "$fixture/save.log" 2>&1
cat "$fixture/save.log"
export VANDOR_LABS_MEKANISM_TANK_RESTART=verify
python3 "$ROOT/tools/test_artifacts.py" run --run-dir "$fixture" --lease "$lease" -- bash testclient/test_viewscreen.sh --focus mekanism-tanks > "$fixture/restart.log" 2>&1
cat "$fixture/restart.log"
echo "Mekanism filled tank cold restart, vehicle reload and native parking passed: $fixture"
