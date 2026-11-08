#!/usr/bin/env bash
# Standard Linux release gate, followed by the isolated exact-JAR Dynmap check.
set -Eeuo pipefail

usage() {
    cat <<'EOF'
Usage: tools/verify_release.sh [--check | --help]

Run the Java 8 release gate and exact-JAR Dynmap smoke test, retaining logs,
captures, worlds and artifact hashes under build/release-checks/.
--check validates local dependencies without building or starting a test.

Defaults use a sibling MC-Forge12-2 installation for read-only dependencies.
Override JAVA_HOME, VANDOR_LABS_RELEASE_SERVER, VANDOR_LABS_COMPAT_MODS,
VANDOR_LABS_TEST_OPTIFINE, VANDOR_LABS_TEST_SHADERPACK,
VANDOR_LABS_TEST_MEKANISM, VANDOR_LABS_TEST_FORGE,
VANDOR_LABS_TEST_DYNMAP or VANDOR_LABS_TEST_WORLDEDIT as needed.
Set VANDOR_LABS_KEEP_TEST_INSTALLS=1 to retain copied mods/caches after success.
Failures retain their full test installations. This script does not deploy.
EOF
}
case "${1:-}" in
    --help|-h) usage; exit 0 ;;
    --check|'') ;;
    *) usage >&2; exit 2 ;;
esac
[[ $# -le 1 ]] || { usage >&2; exit 2; }

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
for variable in VANDOR_LABS_TEST_OPTIFINE VANDOR_LABS_TEST_SHADERPACK VANDOR_LABS_TEST_MEKANISM VANDOR_LABS_TEST_FORGE VANDOR_LABS_TEST_DYNMAP VANDOR_LABS_TEST_WORLDEDIT; do
    [[ -f "${!variable}" ]] || { printf 'Missing %s: %s\n' "$variable" "${!variable}" >&2; exit 1; }
done
if [[ ${1:-} == --check ]]; then
    printf 'Release dependencies OK (Java 8, shader inputs, compatibility mods, Forge, Dynmap, WorldEdit).\n'
    exit 0
fi

mkdir -p build/release-checks
output=$(mktemp -d "$repo_root/build/release-checks/run.XXXXXXXX")
export VANDOR_LABS_TEST_RUN_ROOT="$output/live"
trap 'printf "Release checks failed; full evidence retained in %s\n" "$output" >&2' ERR
printf 'Release evidence: %s\n' "$output"
python3 tools/generate_texture_browser.py --check 2>&1 | tee "$output/catalog.log"
./gradlew verifyRelease --no-daemon 2>&1 | tee "$output/verifyRelease.log"

jar=$(python3 - <<'PY'
from pathlib import Path
import re
version = re.search(r"^version\s*=\s*['\"]([^'\"]+)['\"]", Path('build.gradle').read_text(), re.M).group(1)
print(Path('build/libs') / ('vandorlabs-' + version + '.jar'))
PY
)
sha256sum "$jar" | tee "$output/artifact.sha256"
digest=$(cut -d ' ' -f 1 "$output/artifact.sha256")
grep -Fq "Release JAR verified SHA-256: $digest" "$output/verifyRelease.log"
python3 testclient/test_dynmap_render.py --forge "$VANDOR_LABS_TEST_FORGE" \
    --dynmap "$VANDOR_LABS_TEST_DYNMAP" --mod "$VANDOR_LABS_TEST_WORLDEDIT" \
    --jar "$jar" --java "$JAVA_HOME/bin/java" 2>&1 | tee "$output/dynmap.log"
sha256sum -c "$output/artifact.sha256"

python3 - "$output" "$digest" <<'PY'
from pathlib import Path
import os, shutil, sys
output = Path(sys.argv[1])
line = next(s for s in (output / 'dynmap.log').read_text().splitlines() if s.startswith('Isolated Dynmap fixture: '))
fixture = Path(line.split(': ', 1)[1]).resolve()
assert fixture.parent == Path('build').resolve() and fixture.name.startswith('dynmap-render-')
assert (fixture / 'artifact.sha256').read_text().strip() == sys.argv[2], 'Dynmap artifact hash differs'
(output / 'dynmap-fixture.txt').write_text(str(fixture) + '\n')
if os.environ.get('VANDOR_LABS_KEEP_TEST_INSTALLS') != '1':
    for game in (output / 'live').glob('render-run.*/game'):
        for name in ('mods', 'cache'):
            path = game / name
            if path.is_dir() and not path.is_symlink():
                shutil.rmtree(path)
    # The shared retention helper may already have pruned this installation.
    mods = fixture / 'mods'
    if mods.is_dir() and not mods.is_symlink():
        shutil.rmtree(mods)
PY
printf 'Release checks PASS. Logs, captures and hashes: %s\n' "$output"
