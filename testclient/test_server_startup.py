#!/usr/bin/env python3
"""Place canopy/fluid fixtures, tick a physical Forge server, save, and reopen its isolated world."""
import argparse
import hashlib
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--forge', required=True, type=Path, help='Forge server JAR with its installed libraries')
    parser.add_argument('--jar', required=True, type=Path, help='Built standard Vandor Labs JAR')
    parser.add_argument('--java', default='java', help='Java 8 executable')
    parser.add_argument('--mod', action='append', default=[], type=Path, help='Additional compatibility mod JAR (repeatable)')
    parser.add_argument('--vehicle-world-checks', action='store_true', help='Run carried-tile chunk isolation checks on the physical server')
    parser.add_argument('--machine-frames', action='store_true', help='Run the complete Core-frame matrix with client classes absent')
    parser.add_argument('--vehicle-world-only', action='store_true', help='Run only physical-server vehicle compatibility checks, without canopy/fluid fixtures')
    parser.add_argument('--foreign-buildings', action='store_true', help='Run focused installed building-mod checks on a physical server')
    parser.add_argument('--reactor-glass', action='store_true', help='Run standalone Mekanism reactor glass transfer checks')
    args = parser.parse_args()
    if args.reactor_glass:
        args.vehicle_world_only = True
    if args.foreign_buildings:
        args.vehicle_world_only = True
    if args.vehicle_world_only:
        args.vehicle_world_checks = True
    root = Path(__file__).resolve().parents[1]
    output = Path(tempfile.mkdtemp(prefix='server-startup-', dir=root / 'build'))
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    (output / 'mods').mkdir()
    artifact = args.jar.resolve()
    shutil.copyfile(artifact, output / 'mods' / artifact.name)
    for mod in args.mod:
        shutil.copyfile(mod, output / 'mods' / mod.name)
    (output / 'artifact.sha256').write_text(hashlib.sha256(artifact.read_bytes()).hexdigest() + '\n')
    (output / 'eula.txt').write_text('eula=true\n')
    (output / 'server.properties').write_text(
        f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nlevel-type=FLAT\n'
        'generate-structures=false\nspawn-animals=false\nspawn-monsters=false\nspawn-npcs=false\n'
        'view-distance=3\nallow-nether=false\nmax-tick-time=120000\n')
    bad = ('NoSuchMethodError', 'NoSuchFieldError', 'Encountered an unexpected exception',
           'Exception caught during firing event', 'Exception in server tick loop',
           'Cannot place block outside', 'Cannot test for block outside', 'Cannot summon the object',
           '(expected:')
    # Changing spawn does not synchronously load its chunks. Restart once so the
    # fixture chunk is part of the server's prepared start region before placement.
    for phase in (('prepare',) if args.vehicle_world_only else ('prepare', 'place', 'reload')):
        path = output / (phase + '.log')
        with path.open('w') as log:
            checks = ['-Dvandorlabs.vehicleWorldCompatibilityChecks=true'] if args.vehicle_world_checks else []
            if args.vehicle_world_checks:
                checks.append('-Dvandorlabs.legacyVehicleFixture='+str(root/'testclient/fixtures/legacy-vehicle-controller-2.6.nbt'))
            if args.reactor_glass:
                checks.append('-Dvandorlabs.reactorGlassChecks=true')
            if args.foreign_buildings:
                checks.append('-Dvandorlabs.foreignBuildingChecks=true')
            if args.machine_frames:
                checks.append('-Dvandorlabs.machineFrameChecks=true')
            proc = subprocess.Popen([args.java, '-Xmx2G', *checks, '-jar', str(args.forge.resolve()), 'nogui'],
                                    cwd=output, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
            def wait_for(marker, timeout=150):
                deadline = time.monotonic() + timeout
                while time.monotonic() < deadline:
                    text = path.read_text(errors='replace')
                    if any(message in text for message in bad):
                        raise RuntimeError(f'{phase}: server error; see {path}')
                    if marker in text:
                        return
                    if proc.poll() is not None:
                        raise RuntimeError(f'{phase}: server stopped; see {path}')
                    time.sleep(.2)
                raise TimeoutError(f'{phase}: no {marker}; see {path}')
            def command(text):
                proc.stdin.write(text + '\n')
                proc.stdin.flush()
            try:
                wait_for('Done (')
                if args.vehicle_world_checks:
                    wait_for('vehicle-world-compatibility PASS')
                if args.reactor_glass:
                    wait_for('reactor-glass PASS')
                if args.foreign_buildings:
                    wait_for('foreign-buildings PASS')
                if args.machine_frames:
                    wait_for('constructors PASS ')
                if args.vehicle_world_only:
                    command('stop')
                    proc.wait(timeout=45)
                    if proc.returncode:
                        raise RuntimeError(f'{phase}: failed shutdown; see {path}')
                    print(f'Physical-server vehicle compatibility PASS: {output}')
                    return
                if phase == 'prepare':
                    command('setworldspawn 4 12 4')
                    command('say VANDOR_SPAWN_PREPARED')
                    wait_for('VANDOR_SPAWN_PREPARED')
                    command('stop')
                    proc.wait(timeout=45)
                    if proc.returncode:
                        raise RuntimeError(f'{phase}: failed shutdown; see {path}')
                    continue
                if phase == 'place':
                    def anchor(x, y, z):
                        return ((x & 0x3ffffff) << 38) | ((y & 0xfff) << 26) | (z & 0x3ffffff)
                    # Legacy rectangular reservations are supported saved assemblies.
                    # Console placement must create every helper with the root's anchor.
                    right = ((-1, 0), (0, -1), (1, 0), (0, 1))
                    for facing in range(4):
                        y = 10 + facing * 12
                        dx, dz = right[facing]
                        x1, z1 = 6 + dx * 4, 6 + dz * 4
                        command(f'fill {min(6, x1)} {y} {min(6, z1)} {max(6, x1)} {y + 4} {max(6, z1)} '
                                f'vandorlabs:large_front_canopy_glass {facing} replace {{Anchor:{anchor(6, y, 6)}L}}')
                        command(f'summon minecraft:pig 6.5 {y + 3} 6.5')
                    command(f'fill 4 10 11 4 10 12 vandorlabs:canopy 0 replace {{Anchor:{anchor(4, 10, 12)}L}}')
                    command('setblock 12 10 12 vandorlabs:programmable_wall 0 replace {Waterlogged:1b}')
                    command('summon minecraft:pig 12.5 11 12.5')
                    command('say VANDOR_FIXTURES_PLACED')
                    wait_for('VANDOR_FIXTURES_PLACED')
                command('testforblock 6 10 6 vandorlabs:large_front_canopy_glass')
                wait_for('Successfully found the block')
                time.sleep(4)
                for facing in range(4):
                    command(f'testforblock 6 {10 + facing * 12} 6 vandorlabs:large_front_canopy_glass')
                command('testforblock 4 10 12 vandorlabs:canopy')
                command('testforblock 12 10 12 vandorlabs:programmable_wall')
                command('say VANDOR_TICKS_COMPLETE')
                wait_for('VANDOR_TICKS_COMPLETE')
                if path.read_text(errors='replace').count('Successfully found the block') != 7:
                    raise RuntimeError(f'{phase}: incomplete fixture verification; see {path}')
                command('stop')
                proc.wait(timeout=45)
                text = path.read_text(errors='replace')
                if proc.returncode or any(message in text for message in bad):
                    raise RuntimeError(f'{phase}: failed shutdown; see {path}')
                print(f'Physical-server {phase} PASS', flush=True)
            finally:
                if proc.poll() is None:
                    try:
                        command('stop')
                        proc.wait(timeout=20)
                    except (OSError, subprocess.TimeoutExpired):
                        proc.kill()
                        proc.wait()
    print(f'Physical-server startup/reload PASS: {output}', flush=True)


if __name__ == '__main__':
    main()
