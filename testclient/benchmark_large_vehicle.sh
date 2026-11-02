#!/bin/bash
# Isolated benchmark; the input save and supplied pack remain untouched.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
JAR=$(realpath "${1:?JAR required}")
WORLD=$(realpath "${2:?Extracted save required}")
OUTPUT=$(realpath -m "${3:?Fresh output directory required}")
if [ -e "$OUTPUT" ]; then echo 'Output must be fresh'; exit 2; fi
mkdir -p "$OUTPUT/game/mods" "$OUTPUT/results"
python3 - "$WORLD" "$OUTPUT/game" <<'PY'
import pathlib,shutil,sys,nbtlib
source,game=map(pathlib.Path,sys.argv[1:]);save=game/'saves/benchmark-world'
shutil.copytree(source,save,ignore=shutil.ignore_patterns('mods','config','Flan','session.lock'))
for name in ['config','Flan']:
 if (source/name).exists():shutil.copytree(source/name,game/name)
level=nbtlib.load(save/'level.dat');data=level['Data'];data['SpawnX']=nbtlib.Int(1330);data['SpawnY']=nbtlib.Int(78);data['SpawnZ']=nbtlib.Int(-12135);data.pop('Player',None);level.save()
for jar in (source/'mods').glob('*.jar'):
 if jar.name.lower().startswith(('vandorlabs','dynmap')):continue
 shutil.copy2(jar,game/'mods'/jar.name)
PY
cp "$JAR" "$OUTPUT/game/mods/vandorlabs-2.8.jar"
cp "$ROOT/testclient/runtime/game/options.txt" "$OUTPUT/game/options.txt"
if [ -n "${VANDOR_LABS_BENCH_OPTIFINE:-}" ]; then cp "$VANDOR_LABS_BENCH_OPTIFINE" "$OUTPUT/game/mods/"; fi
if [ -n "${VANDOR_LABS_BENCH_SHADERPACK:-}" ]; then
 mkdir -p "$OUTPUT/game/shaderpacks"
 cp "$VANDOR_LABS_BENCH_SHADERPACK" "$OUTPUT/game/shaderpacks/"
 if [ -f "$VANDOR_LABS_BENCH_SHADERPACK.txt" ]; then cp "$VANDOR_LABS_BENCH_SHADERPACK.txt" "$OUTPUT/game/shaderpacks/"; fi
 printf 'shaderPack=%s\n' "$(basename "$VANDOR_LABS_BENCH_SHADERPACK")" > "$OUTPUT/game/optionsshaders.txt"
fi
sha256sum "$JAR" > "$OUTPUT/results/artifact.sha256"
VANDOR_LABS_TEST_EXTRA_LIBRARIES=${VANDOR_LABS_BENCH_LIBRARIES:-} VANDOR_LABS_TEST_HEAP=${VANDOR_LABS_TEST_HEAP:-12G} VANDOR_LABS_LARGE_VEHICLE_BENCHMARK=true VANDOR_LABS_TEST_GAME_DIR="$OUTPUT/game" VANDOR_LABS_REPRO_OUT="$OUTPUT/results" timeout "${VANDOR_LABS_BENCH_TIMEOUT:-1800}" "$ROOT/testclient/run.sh" > "$OUTPUT/client.log" 2>&1
rg -q 'large-vehicle-benchmark PASS' "$OUTPUT/client.log"
echo "Large vehicle benchmark passed: $OUTPUT/results"
