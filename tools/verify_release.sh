#!/usr/bin/env bash
# Collect-first release validation against one frozen standard JAR.
set -Eeuo pipefail

usage() {
    cat <<'EOF'
Usage: tools/verify_release.sh [--check] [--jar PATH] [--jobs 1|2] [--skip-dynmap]

Collect the complete release failure inventory against one frozen standard JAR.
Without --jar, assemble once before testing; --jar reuses an existing candidate.
CPU checks continue after failures. Independent live checks use two isolated
lanes by default; timing benchmarks run exclusively. Logs and a merged report
are retained under build/release-checks/. The command fails if any required
phase fails. Fix in batches after this pass, then rerun only failed and affected
checks. Keep baseline and repaired-candidate hashes; do not repeat passing suites.
A clean first complete pass needs no redundant second pass.
--check validates dependencies without building or starting a test.
--skip-dynmap records the omitted optional smoke test explicitly.

Defaults use a sibling MC-Forge12-2 installation for read-only dependencies.
Override JAVA_HOME, VANDOR_LABS_RELEASE_SERVER, VANDOR_LABS_COMPAT_MODS,
VANDOR_LABS_TEST_OPTIFINE, VANDOR_LABS_TEST_SHADERPACK,
VANDOR_LABS_TEST_MEKANISM, VANDOR_LABS_TEST_FORGE,
VANDOR_LABS_TEST_DYNMAP or VANDOR_LABS_TEST_WORLDEDIT as needed.
Set VANDOR_LABS_TEST_KEEP_ARTIFACTS=1 only when full runtimes are needed.
Otherwise diagnostics and bounded failure payloads use shared retention. No deployment.
EOF
}
check_only=false
skip_dynmap=false
jar=''
jobs=${VANDOR_LABS_RELEASE_CLIENT_JOBS:-2}
while (( $# )); do
    case "$1" in
        --help|-h) usage; exit 0 ;;
        --check) check_only=true; shift ;;
        --skip-dynmap) skip_dynmap=true; shift ;;
        --jar) [[ $# -ge 2 ]] || { usage >&2; exit 2; }; jar=$2; shift 2 ;;
        --jobs) [[ $# -ge 2 ]] || { usage >&2; exit 2; }; jobs=$2; shift 2 ;;
        *) usage >&2; exit 2 ;;
    esac
done
[[ "$jobs" == 1 || "$jobs" == 2 ]] || { printf 'Use --jobs 1 or 2.\n' >&2; exit 2; }

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd -- "$repo_root"
server_dir=${VANDOR_LABS_RELEASE_SERVER:-"$(dirname -- "$repo_root")/MC-Forge12-2"}
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}
export VANDOR_LABS_COMPAT_MODS=${VANDOR_LABS_COMPAT_MODS:-"$server_dir/mods"}
export VANDOR_LABS_TEST_OPTIFINE=${VANDOR_LABS_TEST_OPTIFINE:-"$server_dir/mods-clientside/OptiFine_1.12.2_HD_U_G5.jar"}
export VANDOR_LABS_TEST_SHADERPACK=${VANDOR_LABS_TEST_SHADERPACK:-"$repo_root/testclient/runtime/shaderpacks/ComplementaryUnbound_r5.6.1.zip"}
export VANDOR_LABS_TEST_MEKANISM=${VANDOR_LABS_TEST_MEKANISM:-"$VANDOR_LABS_COMPAT_MODS/Mekanism-1.12.2-9.8.2.389.jar"}
export VANDOR_LABS_TEST_FORGE=${VANDOR_LABS_TEST_FORGE:-"$server_dir/forge-1.12.2-14.23.5.2860-universal.jar"}
export VANDOR_LABS_TEST_DYNMAP=${VANDOR_LABS_TEST_DYNMAP:-"$VANDOR_LABS_COMPAT_MODS/Dynmap-3.7-beta-6-forge-1.12.2.jar"}
export VANDOR_LABS_TEST_WORLDEDIT=${VANDOR_LABS_TEST_WORLDEDIT:-"$VANDOR_LABS_COMPAT_MODS/worldedit-forge-mc1.12.2-6.1.10-dist.jar"}
export VANDOR_LABS_TEST_HEAP=${VANDOR_LABS_TEST_HEAP:-8G}

for command in python3 xvfb-run sha256sum; do
    command -v "$command" >/dev/null || { printf 'Missing command: %s\n' "$command" >&2; exit 1; }
done
[[ -x "$JAVA_HOME/bin/java" ]] || { printf 'Set JAVA_HOME to a Java 8 JDK.\n' >&2; exit 1; }
"$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "1\.8\.' || { printf 'Java 8 is required; check JAVA_HOME.\n' >&2; exit 1; }
[[ -x "$JAVA_HOME/bin/javac" ]] || { printf 'A Java 8 JDK, including javac, is required.\n' >&2; exit 1; }
python3 -c 'import PIL' || { printf 'Install Python Pillow before running release checks.\n' >&2; exit 1; }
[[ -d "$VANDOR_LABS_COMPAT_MODS" ]] || { printf 'Set VANDOR_LABS_COMPAT_MODS to the installed compatibility-mod directory.\n' >&2; exit 1; }
required_inputs=(VANDOR_LABS_TEST_OPTIFINE VANDOR_LABS_TEST_SHADERPACK VANDOR_LABS_TEST_MEKANISM VANDOR_LABS_TEST_FORGE)
if ! $skip_dynmap; then required_inputs+=(VANDOR_LABS_TEST_DYNMAP VANDOR_LABS_TEST_WORLDEDIT); fi
for variable in "${required_inputs[@]}"; do
    [[ -f "${!variable}" ]] || { printf 'Missing %s: %s\n' "$variable" "${!variable}" >&2; exit 1; }
done
if $check_only; then
    printf 'Release dependencies OK (Java 8, shader inputs, compatibility mods; Dynmap skipped=%s).\n' "$skip_dynmap"
    exit 0
fi

mkdir -p build/release-checks
output=$(mktemp -d "$repo_root/build/release-checks/run.XXXXXXXX")
printf 'Release evidence: %s\n' "$output"
if [[ -z "$jar" ]]; then
    ./gradlew assemble --no-daemon 2>&1 | tee "$output/assemble.log"
    jar=$(python3 - <<'PYVERSION'
from pathlib import Path
import re
version = re.search(r"^version\s*=\s*['\"]([^'\"]+)['\"]", Path('build.gradle').read_text(), re.M).group(1)
print(Path('build/libs') / ('vandorlabs-' + version + '.jar'))
PYVERSION
)
fi
jar=$(python3 - "$jar" <<'PYPATH'
from pathlib import Path
import sys
p=Path(sys.argv[1]).resolve()
assert p.is_file(), 'Frozen JAR does not exist: '+str(p)
print(p)
PYPATH
)
sha256sum "$jar" | tee "$output/artifact.sha256"
export VANDOR_LABS_TEST_JAR="$jar"
export VANDOR_LABS_TEST_RUN_ROOT="$output/live"
export VANDOR_LABS_RELEASE_CLIENT_JOBS="$jobs"
: > "$output/phase-status.tsv"

# Do not stop ordinary failures from exposing independent failures in this pass.
set +e
python3 tools/generate_texture_browser.py --check 2>&1 | tee "$output/catalog.log"
catalog_status=${PIPESTATUS[0]}
./gradlew check testDiagonalJoins testNonRendering testShaderStartup testRampJit --continue -x jar -x reobfJar --no-daemon --profile 2>&1 | tee "$output/cpu.log"
cpu_status=${PIPESTATUS[0]}
set -e
printf 'catalog\t%s\ncpu\t%s\n' "$catalog_status" "$cpu_status" >> "$output/phase-status.tsv"
# Artifact mutation is infrastructure failure: do not launch tests on mixed inputs.
sha256sum -c "$output/artifact.sha256"
set +e
python3 tools/release_live_checks.py --jar "$jar" --jobs "$jobs" --report "$output/live-report.json" 2>&1 | tee "$output/live.log"
live_status=${PIPESTATUS[0]}
set -e
printf 'live\t%s\n' "$live_status" >> "$output/phase-status.tsv"
sha256sum -c "$output/artifact.sha256"
if $skip_dynmap; then
    printf 'Dynmap skipped by explicit request.\n' | tee "$output/dynmap-skipped.txt"
else
    set +e
    python3 testclient/test_dynmap_render.py --forge "$VANDOR_LABS_TEST_FORGE" --dynmap "$VANDOR_LABS_TEST_DYNMAP" --mod "$VANDOR_LABS_TEST_WORLDEDIT" --jar "$jar" --java "$JAVA_HOME/bin/java" 2>&1 | tee "$output/dynmap.log"
    dynmap_status=${PIPESTATUS[0]}
    set -e
    printf 'dynmap\t%s\n' "$dynmap_status" >> "$output/phase-status.tsv"
    sha256sum -c "$output/artifact.sha256"
    if [[ "$dynmap_status" == 0 ]]; then
        python3 - "$output" <<'PYDYNMAP'
from pathlib import Path
import sys
out=Path(sys.argv[1])
actual, = [line.removeprefix('Dynmap tested SHA-256: ') for line in (out/'dynmap.log').read_text().splitlines() if line.startswith('Dynmap tested SHA-256: ')]
expected=(out/'artifact.sha256').read_text().split()[0]
assert actual==expected, 'Dynmap fixture artifact differs from frozen candidate'
PYDYNMAP
    fi
fi
python3 - "$output" "$jar" "$jobs" "$skip_dynmap" <<'PYREPORT'
from pathlib import Path
import hashlib,json,re,sys
out,jar=Path(sys.argv[1]),Path(sys.argv[2])
phases={key:int(value) for key,value in (line.split('\t') for line in (out/'phase-status.tsv').read_text().splitlines())}
live_path=out/'live-report.json'
live=json.loads(live_path.read_text()) if live_path.is_file() else {}
coverage_ok=live.get('scope')=='full' and live.get('status')=='passed' and not live.get('not_run') and not live.get('infrastructure_error')
passed=all(code==0 for code in phases.values()) and coverage_ok
failed_tasks=re.findall(r'^> Task :(\S+) FAILED$',(out/'cpu.log').read_text(errors='replace'),re.M)
report={'schema':1,'workflow':'collect-first','status':'passed' if passed else 'failed','jar':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'jobs':int(sys.argv[3]),'phases':phases,'cpu_failed_tasks':failed_tasks,'live_report':str(live_path),'live_failures':live.get('failures',[]),'dynmap_skipped':sys.argv[4]=='true'}
(out/'report.json').write_text(json.dumps(report,indent=2,sort_keys=True)+'\n')
print('Release inventory '+('PASS' if passed else 'FAIL')+': '+str(out/'report.json'))
if failed_tasks:print('CPU failed tasks: '+', '.join(failed_tasks))
if live.get('failures'):print('Live failed focuses: '+', '.join(live['failures']))
print('Batch fixes after the inventory; rerun only failed and affected checks. Retain baseline and repaired-candidate hashes.')
sys.exit(0 if passed else 1)
PYREPORT
