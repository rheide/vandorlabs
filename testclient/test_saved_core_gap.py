#!/usr/bin/env python3
"""Run the exact saved fractional Core/socket contact journey on an isolated copied world."""
import argparse, hashlib, os, shlex, shutil, subprocess, sys, tempfile
from pathlib import Path

repo = next(p for p in Path(__file__).resolve().parents if (p / 'settings.gradle').exists())
sys.path.insert(0, str(repo / 'tools'))
from test_artifacts import ArtifactRun

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--jar', type=Path, required=True)
parser.add_argument('--world-archive', type=Path, required=True)
parser.add_argument('--template', type=Path, required=True, help='Accepted runtime containing game/mods and game/config')
parser.add_argument('--java', type=Path)
parser.add_argument('--keep', action='store_true')
args = parser.parse_args()
for path in (args.jar, args.world_archive, args.template / 'game/mods'):
    if not path.exists(): parser.error('Missing input: ' + str(path))
base = repo / 'build/test-artifacts'; base.mkdir(exist_ok=True)
out = Path(tempfile.mkdtemp(prefix='saved-core-gap-', dir=str(base)))
with ArtifactRun(out, ['game', 'unpack'], keep=args.keep) as owner:
    game = out / 'game'; results = out / 'results'; results.mkdir()
    (game / 'mods').mkdir(parents=True)
    for source in sorted((args.template / 'game/mods').glob('*.jar')):
        if not source.name.startswith('vandorlabs-'): shutil.copyfile(source, game / 'mods' / source.name)
    shutil.copyfile(args.jar, game / 'mods/vandorlabs-2.10.0.jar')
    for name in ('config',):
        source = args.template / 'game' / name
        if source.exists(): shutil.copytree(source, game / name)
    options = args.template / 'game/options.txt'
    if options.exists(): shutil.copyfile(options, game / 'options.txt')
    libs = args.template / 'compat-libs'
    if libs.exists(): shutil.copytree(libs, out / 'compat-libs')
    subprocess.run(['7z', 'x', '-y', '-o' + str(out / 'unpack'), str(args.world_archive.resolve())], check=True, stdout=subprocess.DEVNULL)
    source_world = out / 'unpack/test'
    if not (source_world / 'level.dat').is_file(): raise RuntimeError('Expected test/level.dat in the saved archive')
    (game / 'saves').mkdir(); shutil.copytree(source_world, game / 'saves/benchmark-world', ignore=shutil.ignore_patterns('session.lock'))
    launch = (repo / 'testclient/run.sh').read_text().replace('cd "$(dirname "$0")/runtime"', 'cd ' + shlex.quote(str(repo / 'testclient/runtime')))
    if args.java:
        launch = launch.replace('JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin/java', 'JAVA=' + shlex.quote(str(args.java.resolve())))
    (out / 'launch.sh').write_text(launch)
    (out / 'artifact.sha256').write_text(hashlib.sha256(args.jar.read_bytes()).hexdigest() + '  ' + str(args.jar.resolve()) + '\n')
    (out / 'source-archive.sha256').write_text(hashlib.sha256(args.world_archive.read_bytes()).hexdigest() + '  ' + args.world_archive.name + '\n')
    (out / 'mods.sha256').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in sorted((game / 'mods').glob('*.jar'))))
    env = dict(os.environ, VANDOR_LABS_WORLD_BENCHMARK='false', VANDOR_LABS_ARM_LIVE_CHECKS='true', VANDOR_LABS_ARM_CORE_ONLY='true', VANDOR_LABS_TEST_HEAP='8G', VANDOR_LABS_TEST_GAME_DIR=str(game), VANDOR_LABS_REPRO_OUT=str(results), JAVA_TOOL_OPTIONS='-Dvandorlabs.testWorldName=benchmark-world -Dvandorlabs.armCoreGapOnly=true')
    if (out / 'compat-libs').exists(): env['LD_LIBRARY_PATH'] = str(out / 'compat-libs')
    command = ['timeout', '300', 'bash', str(out / 'launch.sh')]
    (out / 'command.txt').write_text(shlex.join(command) + '\n')
    print('START saved Core gap ' + str(out), flush=True)
    with (out / 'client.log').open('wb') as log:
        process = subprocess.Popen(command, cwd=repo, env=env, stdout=log, stderr=subprocess.STDOUT)
        owner.track_process(process); result = process.wait(); owner.stop_tracking()
    print('EXIT ' + str(result) + ' ' + str(out), flush=True)
    if result: raise RuntimeError('Saved Core contact journey failed; inspect bounded retained client.log')
    if 'arm-live PASS saved-core-gap checks=' not in (out / 'client.log').read_text(errors='replace'):
        raise RuntimeError('Finite saved Core contact PASS marker absent')
