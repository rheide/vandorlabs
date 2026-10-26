#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
fixture=$(mktemp -d "$ROOT/testclient/arm-restart.XXXXXX")
export VANDOR_LABS_TEST_GAME_DIR="$fixture/game" VANDOR_LABS_TEST_SKIP_BUILD=true
export VANDOR_LABS_TEST_HEAP=${VANDOR_LABS_TEST_HEAP:-4G}
for phase in authorization-save authorization-verify; do
    export VANDOR_LABS_ARM_RECOVERY_PHASE="$phase"
    bash testclient/test_viewscreen.sh --focus arm-recovery > "$fixture/$phase.log" 2>&1
    cat "$fixture/$phase.log"
done
echo "Arm and Copy Controller reconnect passed: $fixture"
