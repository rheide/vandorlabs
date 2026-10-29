#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
export VANDOR_LABS_TEST_SKIP_BUILD=true
export VANDOR_LABS_TEST_HEAP=${VANDOR_LABS_TEST_HEAP:-4G}
for phase in ${VANDOR_LABS_ARM_RECOVERY_CASES:-prepared detach detach-partial restore-partial restore-endpoint-partial detach-endpoint-partial harvest-prepared harvest-partial harvest-committed push-prepared push-partial painting-before-journal painting-assemble-partial painting-park-partial}; do
    fixture=$(mktemp -d "$ROOT/testclient/arm-restart.XXXXXX")
    export VANDOR_LABS_TEST_GAME_DIR="$fixture/game"
    export VANDOR_LABS_ARM_RECOVERY_PHASE="$phase"
    bash testclient/test_viewscreen.sh --focus arm-recovery > "$fixture/crash.log" 2>&1 &
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
    bash testclient/test_viewscreen.sh --focus arm-recovery > "$fixture/restart.log" 2>&1
    cat "$fixture/restart.log"
    echo "Arm durable recovery $phase passed: $fixture"
done
