#!/usr/bin/env bash
# One owned old-source world, then the exact hook candidate in a fresh JVM.
# The read phase starts only after the coordinator supplies read-jar.txt.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WRITE_JAR=${1:?Usage: test_module_rack_upgrade.sh prehook.jar}
WRITE_JAR=$(readlink -f "$WRITE_JAR")
SUITE=$(mktemp -d "$ROOT/testclient/rack-upgrade.XXXXXX")
LEASE=$(python3 "$ROOT/tools/test_artifacts.py" begin --run-dir "$SUITE" --pid "$$" --disposable game)
finish() {
    status=$?
    trap - EXIT
    python3 "$ROOT/tools/test_artifacts.py" finish --run-dir "$SUITE" --lease "$LEASE" --exit-code "$status" || echo "Preserved Rack upgrade evidence: $SUITE" >&2
    exit "$status"
}
trap finish EXIT
echo "Rack upgrade suite: $SUITE"
export VANDOR_LABS_TEST_SKIP_BUILD=true
export VANDOR_LABS_TEST_HEAP=${VANDOR_LABS_TEST_HEAP:-8G}
export VANDOR_LABS_TEST_GAME_DIR="$SUITE/game"
export VANDOR_LABS_TEST_RUN_ROOT="$SUITE/launches"
export VANDOR_LABS_COMPAT_MODS=${VANDOR_LABS_COMPAT_MODS:?Set VANDOR_LABS_COMPAT_MODS to the local test-mod directory}
mkdir -p "$SUITE/launches"
export VANDOR_LABS_TEST_JAR="$WRITE_JAR"
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dvandorlabs.moduleRackAutopilotOnly=true -Dvandorlabs.moduleRackMigrationPhase=write" \
    python3 "$ROOT/tools/test_artifacts.py" run --run-dir "$SUITE" --lease "$LEASE" -- \
    bash "$ROOT/testclient/test_viewscreen.sh" --focus module-rack > "$SUITE/write.log" 2>&1
python3 - "$SUITE" "$WRITE_JAR" <<'PY'
import hashlib,json,sys
from pathlib import Path
suite,jar=map(Path,sys.argv[1:])
logs=list((suite/'launches').glob('*/client.log'))
assert len(logs)==1 and 'module-rack PASS migration-write' in logs[0].read_text(), 'prehook actual save did not pass'
worlds=[p.parent for p in (suite/'game/saves').glob('*/level.dat')]
assert len(worlds)==1, 'expected one explicitly owned old-source world'
expected=hashlib.sha256(jar.read_bytes()).hexdigest()
assert logs[0].with_name('artifact.sha256').read_text().split()[0]==expected, 'write child used another JAR'
raw=logs[0].with_name('module_rack_before.nbt')
assert raw.is_file(), 'original raw beforeimage diagnostic missing'
(suite/'before.nbt').write_bytes(raw.read_bytes())
record=dict(world=worlds[0].name,write_jar=str(jar),write_sha256=expected,before_nbt_sha256=hashlib.sha256(raw.read_bytes()).hexdigest())
(suite/'upgrade.json').write_text(json.dumps(record,indent=2)+'\n')
print('Rack upgrade WRITE PASS; suite='+str(suite)+'; provide authorized immutable read JAR path in '+str(suite/'read-jar.txt'),flush=True)
PY
# This parent lease owns the generated game until both JVMs finish.
for attempt in $(seq 1 3600); do
    if [ -s "$SUITE/read-jar.txt" ]; then break; fi
    sleep 1
done
if [ ! -s "$SUITE/read-jar.txt" ]; then echo "No read candidate supplied: $SUITE" >&2; exit 1; fi
READ_JAR=$(cat "$SUITE/read-jar.txt")
if [ ! -f "$READ_JAR" ]; then echo "Missing immutable read candidate: $READ_JAR" >&2; exit 1; fi
WORLD=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["world"])' "$SUITE/upgrade.json")
export VANDOR_LABS_TEST_JAR="$READ_JAR"
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dvandorlabs.moduleRackAutopilotOnly=true -Dvandorlabs.moduleRackMigrationPhase=read -Dvandorlabs.testWorldName=$WORLD" \
    python3 "$ROOT/tools/test_artifacts.py" run --run-dir "$SUITE" --lease "$LEASE" -- \
    bash "$ROOT/testclient/test_viewscreen.sh" --focus module-rack > "$SUITE/read.log" 2>&1
python3 - "$SUITE" "$READ_JAR" <<'PY'
import hashlib,json,sys
from pathlib import Path
suite,jar=map(Path,sys.argv[1:])
logs=list((suite/'launches').glob('*/client.log'))
assert len(logs)==2 and any('module-rack PASS migration-read' in p.read_text() for p in logs), 'automatic new-load migration did not pass'
record=json.loads((suite/'upgrade.json').read_text())
expected=hashlib.sha256(jar.read_bytes()).hexdigest()
readlog=next(p for p in logs if 'module-rack PASS migration-read' in p.read_text())
assert readlog.with_name('artifact.sha256').read_text().split()[0]==expected, 'read child used another JAR'
raw=readlog.with_name('module_rack_after.nbt')
assert raw.is_file(), 'raw migrated owner diagnostic missing'
(suite/'after.nbt').write_bytes(raw.read_bytes())
record.update(read_jar=str(jar),read_sha256=expected,after_nbt_sha256=hashlib.sha256(raw.read_bytes()).hexdigest(),result='PASS')
(suite/'upgrade.json').write_text(json.dumps(record,indent=2)+'\n')
print('Rack saved-world automatic upgrade FULLPASS: '+str(suite),flush=True)
PY
