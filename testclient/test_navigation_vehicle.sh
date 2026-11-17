#!/usr/bin/env bash
# One retained fixture owns capture, flight, cold reload and native parking.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
fixture=$(mktemp -d "$ROOT/testclient/navigation-vehicle.XXXXXX")
lease=$(python3 tools/test_artifacts.py begin --run-dir "$fixture" --pid "$$" --disposable game)
finish() {
    result=$?
    trap - EXIT
    python3 tools/test_artifacts.py finish --run-dir "$fixture" --lease "$lease" --exit-code "$result" || echo "Retention failed: $fixture" >&2
    exit "$result"
}
trap finish EXIT
export VANDOR_LABS_TEST_SKIP_BUILD=true
export VANDOR_LABS_TEST_GAME_DIR="$fixture/game"
for phase in write read; do
    export VANDOR_LABS_NAVIGATION_VEHICLE_PHASE=$phase
    python3 tools/test_artifacts.py run --run-dir "$fixture" --lease "$lease" -- bash testclient/test_viewscreen.sh --focus navigation-vehicle > "$fixture/$phase.log" 2>&1
    tail -8 "$fixture/$phase.log"
done
echo "Vehicle navigation cold roundtrip passed: $fixture"
