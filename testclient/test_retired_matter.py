#!/usr/bin/env python3
"""Cold-upgrade a previous Forge world, then load an untouched retired Matter chunk."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from test_artifacts import ArtifactRun


def phase(args, artifact, game, log, commands, run):
    shutil.copyfile(artifact, game / 'mods' / 'vandorlabs.jar')
    with log.open('w') as output:
        options = {'creationflags': subprocess.CREATE_NEW_PROCESS_GROUP} if os.name == 'nt' else {'start_new_session': True}
        process = run.track_process(subprocess.Popen(
            [args.java, '-Xmx2G', '-jar', str(args.forge.resolve()), 'nogui'],
            cwd=game, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
            text=True, **options))

        def send(command):
            process.stdin.write(command + '\n')
            process.stdin.flush()

        def wait_for(marker, start=0):
            deadline = time.monotonic() + 150
            errors = ('Exception in server tick loop', 'Cannot place block outside', '(expected:')
            while time.monotonic() < deadline:
                tail = log.read_text(errors='replace')[start:]
                if process.poll() is not None or any(error in tail for error in errors):
                    raise RuntimeError(f'{log.stem} failed waiting for {marker}. See {log}')
                if marker in tail:
                    return
                time.sleep(.2)
            raise TimeoutError(f'{log.stem} timed out waiting for {marker}. See {log}')

        try:
            wait_for('Done (')
            for command, marker in commands:
                start = len(log.read_text(errors='replace'))
                send(command)
                wait_for(marker, start)
            send('stop')
            if process.wait(timeout=45):
                raise RuntimeError(f'{log.stem} shutdown failed. See {log}')
        finally:
            if process.poll() is None:
                try:
                    send('stop')
                    process.wait(timeout=30)
                except (BrokenPipeError, subprocess.TimeoutExpired):
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
    print(f'{log.stem} PASS {log}', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--forge', type=Path, required=True)
    parser.add_argument('--old-jar', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--java', default='java')
    args = parser.parse_args()
    parent = ROOT / 'build' / 'test-artifacts'
    parent.mkdir(parents=True, exist_ok=True)
    out = Path(tempfile.mkdtemp(prefix='forge-matter-upgrade-', dir=parent))
    run = ArtifactRun(out, disposable_paths=['game'])
    game = out / 'game'
    (game / 'mods').mkdir(parents=True)
    (game / 'eula.txt').write_text('eula=true\n')
    (game / 'server.properties').write_text(
        'online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nlevel-type=FLAT\n'
        'generate-structures=false\nallow-nether=false\nview-distance=3\nmax-tick-time=120000\n')
    for name, path in [('previous', args.old_jar), ('candidate', args.jar)]:
        (out / f'{name}.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest() + '\n')

    old = 'vandorlabs:programmable_matter_block'
    current = 'vandorlabs:programmable_block'
    chest = 'minecraft:chest'
    part = 'vandorlabs:constructor_part'
    placed, found, spawned = 'Block placed', 'Successfully found the block', 'Set the world spawn point'
    success = False
    try:
        phase(args, args.old_jar, game, out / 'previous-prepare.log', [
            ('setworldspawn 8 71 8', spawned),
        ], run)
        phase(args, args.old_jar, game, out / 'previous-near.log', [
            (f'setblock 8 71 8 {old}', placed),
            (f'setblock 9 71 8 {chest} 0 replace {{Items:[{{Slot:0b,id:"{old}",Count:19b,Damage:0s}}]}}', placed),
            ('setblock 7 71 8 vandorlabs:assembly_press_core 6', placed),
            (f'setblock 7 71 9 {part} 0 replace {{ConstructorOwner:67108863L,ConstructorMatter:1b}}', placed),
            ('setworldspawn 520 71 520', spawned),
        ], run)
        phase(args, args.old_jar, game, out / 'previous-far.log', [
            (f'setblock 520 71 520 {old}', placed),
            ('setblock 521 71 520 minecraft:diamond_block', placed),
            ('setworldspawn 8 71 8', spawned),
        ], run)
        phase(args, args.jar, game, out / 'upgrade-near.log', [
            (f'testforblock 8 71 8 {current}', found),
            (f'testforblock 9 71 8 {chest} -1 {{Items:[{{Slot:0b,id:"{current}",Count:19b,Damage:0s}}]}}', found),
            (f'testforblock 7 71 9 {part}', found),
            ('setblock 7 71 8 minecraft:air', placed),
            (f'testforblock 7 71 9 {current}', found),
            ('setworldspawn 520 71 520', spawned),
        ], run)
        phase(args, args.jar, game, out / 'upgrade-far-restart.log', [
            (f'testforblock 520 71 520 {current}', found),
            ('testforblock 521 71 520 minecraft:diamond_block', found),
        ], run)
        success = True
    finally:
        print(f'Forge Matter migration retention {run.finish(success)}', flush=True)


if __name__ == '__main__':
    main()
