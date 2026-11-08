#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
export VANDOR_LABS_TEST_SKIP_BUILD=true
export VANDOR_LABS_TEST_HEAP=${VANDOR_LABS_TEST_HEAP:-4G}
RESTART_ROOT=${VANDOR_LABS_RECOVERY_RUN_ROOT:-$ROOT/testclient}
mkdir -p "$RESTART_ROOT"
active_fixture=
active_lease=
finish_recovery_artifacts() {
    local result=$?
    trap - EXIT
    if [ -n "$active_fixture" ]; then
        python3 "$ROOT/tools/test_artifacts.py" finish --run-dir "$active_fixture" --lease "$active_lease" --exit-code "$result" || \
            echo "Recovery artifact retention failed; preserved run: $active_fixture" >&2
    fi
    exit "$result"
}
trap finish_recovery_artifacts EXIT
for phase in ${VANDOR_LABS_ARM_RECOVERY_CASES:-prepared detach detach-partial restore-partial restore-endpoint-partial detach-endpoint-partial harvest-prepared harvest-partial harvest-committed push-prepared push-partial painting-before-journal painting-assemble-partial painting-park-partial coupling-attach-partial coupling-detach-partial coupling-rollback-partial coupling-latch-committed coupling-split-partial coupling-split-rollback-partial coupling-split-committed safe-park-partial}; do
    fixture=$(mktemp -d "$RESTART_ROOT/arm-restart.XXXXXX")
    active_fixture=$fixture
    active_lease=$(python3 "$ROOT/tools/test_artifacts.py" begin --run-dir "$fixture" --pid "$$" --disposable game)
    export VANDOR_LABS_TEST_GAME_DIR="$fixture/game"
    export VANDOR_LABS_ARM_RECOVERY_PHASE="$phase"
    python3 "$ROOT/tools/test_artifacts.py" run --run-dir "$fixture" --lease "$active_lease" -- bash testclient/test_viewscreen.sh --focus arm-recovery > "$fixture/crash.log" 2>&1 &
    runner=$!
    marker="$fixture/game/saves/arm-recovery/arm-crash-ready.txt"
    for attempt in $(seq 1 180); do
        if [ -f "$marker" ]; then break; fi
        if ! kill -0 "$runner" 2>/dev/null; then break; fi
        sleep 1
    done
    if [ ! -f "$marker" ]; then
        echo "Crash fixture did not reach its durable barrier: $fixture"
        exit 1
    fi
    crash_pid=$(cat "$marker")
    case "$crash_pid" in ''|*[!0-9]*) echo "Invalid fixture PID"; exit 1;; esac
    python3 - "$crash_pid" "$fixture/game" <<'PYVERIFY'
import pathlib, sys
args = pathlib.Path('/proc', sys.argv[1], 'cmdline').read_bytes().split(b'\0')
assert sys.argv[2].encode() in args, 'Refusing to kill a process outside this disposable fixture'
assert any(b'java' in arg for arg in args[:1]), 'Expected the fixture JVM'
PYVERIFY
    kill -KILL "$crash_pid"
    set +e
    wait "$runner"
    result=$?
    set -e
    if [ "$result" -ne 137 ]; then
        echo "Expected killed fixture exit 137 for $phase, got $result: $fixture"
        exit 1
    fi
    rm "$marker"
    export VANDOR_LABS_ARM_RECOVERY_PHASE="verify-$phase"
    python3 "$ROOT/tools/test_artifacts.py" run --run-dir "$fixture" --lease "$active_lease" -- bash testclient/test_viewscreen.sh --focus arm-recovery > "$fixture/restart.log" 2>&1
    cat "$fixture/restart.log"
    if [ "$phase" = safe-park-arm-partial ]; then
        export VANDOR_LABS_ARM_RECOVERY_PHASE="verify2-$phase"
        python3 "$ROOT/tools/test_artifacts.py" run --run-dir "$fixture" --lease "$active_lease" -- bash testclient/test_viewscreen.sh --focus arm-recovery > "$fixture/restart2.log" 2>&1
        cat "$fixture/restart2.log"
    fi
    echo "Arm durable recovery $phase passed: $fixture"
    python3 "$ROOT/tools/test_artifacts.py" finish --run-dir "$fixture" --lease "$active_lease" --exit-code 0
    active_fixture=
    active_lease=
done
