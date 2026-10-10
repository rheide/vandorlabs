#!/usr/bin/env python3
"""Run an isolated, muted Forge client in the background using installed libraries.

Requires a HotSpot Java 8 runtime and an installed Forge 1.12.2 profile.
The built mod stays in build/libs; only external test fixtures are copied.
"""
import argparse
import ctypes
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default='java')
    parser.add_argument('--minecraft-dir', type=Path,
                        default=Path(os.environ.get('APPDATA', '.')) / '.minecraft')
    parser.add_argument('--forge-profile', default='1.12.2-forge-14.23.5.2860')
    parser.add_argument('--compat-mods', type=Path)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--focus', choices=['pilot-seat', 'vehicles'])
    parser.add_argument('--timeout', type=int, default=1200)
    args = parser.parse_args()
    if os.name != 'nt':
        parser.error('Use run.sh on Linux; this launcher hides native Windows client windows.')
    root = Path(__file__).resolve().parents[1]
    version = re.search(r"^version = '([^']+)'", (root/'build.gradle').read_text(), re.M)[1]
    jar = (args.jar or root/'build/libs'/('vandorlabs-'+version+'.jar')).resolve()
    if not jar.is_file():
        parser.error('Build the standard mod JAR first: '+str(jar))
    output = root/'testclient'/('render-run.'+time.strftime('%Y%m%d-%H%M%S'))
    game = output/'game'
    natives = output/'natives'
    game.mkdir(parents=True)
    natives.mkdir()
    (game/'options.txt').write_text('soundCategory_master:0.0\npauseOnLostFocus:false\n')
    base = args.minecraft_dir.resolve()
    profile = json.loads((base/'versions'/args.forge_profile/(args.forge_profile+'.json')).read_text())
    profiles = [profile]
    if profile.get('inheritsFrom'):
        parent = profile['inheritsFrom']
        profiles.append(json.loads((base/'versions'/parent/(parent+'.json')).read_text()))
    classpath = []
    libraries = {}
    for source in profiles:
        for lib in source['libraries']:
            libraries.setdefault(lib['name'], lib)
    for lib in libraries.values():
        allowed = not lib.get('rules')
        for rule in lib.get('rules', []):
            if not rule.get('os') or rule['os'].get('name') == 'windows':
                allowed = rule['action'] == 'allow'
        if not allowed:
            continue
        group, artifact, ver = lib['name'].split(':')[:3]
        relative = '/'.join([group.replace('.', '/'), artifact, ver, artifact+'-'+ver+'.jar'])
        data = lib.get('downloads', {}).get('artifact', lib.get('artifact', {}))
        path = base/'libraries'/data.get('path', relative)
        if path.is_file():
            classpath.append(str(path))
        elif not lib.get('natives'):
            raise FileNotFoundError(path)
        if 'windows' in lib.get('natives', {}):
            classifier = lib['natives']['windows'].replace('${arch}', '64')
            data = lib.get('downloads', {}).get('classifiers', {}).get(classifier, {})
            path = base/'libraries'/data.get('path', relative[:-4]+'-'+classifier+'.jar')
            with zipfile.ZipFile(path) as archive:
                for name in archive.namelist():
                    if name.lower().endswith('.dll'):
                        (natives/Path(name).name).write_bytes(archive.read(name))
    classpath += [str(base/'versions/1.12.2/1.12.2.jar'), str(jar)]
    compat = args.compat_mods or Path(os.environ.get('VANDOR_LABS_COMPAT_MODS', str(base/'mods')))
    (game/'mods').mkdir()
    for name in ['worldedit-forge-mc1.12.2-6.1.10-dist.jar',
                 'BetterBuildersWands-1.12-0.11.1.245+69d0d70.jar', 'ImmersiveEngineering-0.12-98.jar']:
        shutil.copyfile(compat/name, game/'mods'/name)
    properties = ['-Dvandorlabs.reprolab='+str(output), '-Djava.library.path='+str(natives)]
    if args.focus:
        properties.append('-Dvandorlabs.'+{'pilot-seat': 'pilotSeatChecksOnly', 'vehicles': 'vehicleChecksOnly'}[args.focus]+'=true')
    command = [args.java, '-Xmx3G']+properties+['-cp', ';'.join(classpath),
        'net.minecraft.launchwrapper.Launch', '--tweakClass', 'net.minecraftforge.fml.common.launcher.FMLTweaker',
        '--gameDir', str(game), '--assetsDir', str(base/'assets'), '--assetIndex', '1.12',
        '--username', 'REPROBOT', '--accessToken', 'test', '--version', args.forge_profile,
        '--width', '1280', '--height', '720']
    # The child has no console; hide only windows belonging to its exact PID.
    user32 = ctypes.windll.user32
    user32.ShowWindow.argtypes = [ctypes.c_void_p, ctypes.c_int]
    user32.IsWindowVisible.argtypes = [ctypes.c_void_p]
    user32.GetWindowThreadProcessId.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_ulong)]
    callback_type = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
    startup = subprocess.STARTUPINFO()
    startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    startup.wShowWindow = 0
    print('Live-client output:', output, flush=True)
    with (output/'client.log').open('w') as log:
        proc = subprocess.Popen(command, cwd=game, stdout=log, stderr=subprocess.STDOUT,
                                creationflags=subprocess.CREATE_NO_WINDOW, startupinfo=startup)
        @callback_type
        def hide_window(hwnd, unused):
            pid = ctypes.c_ulong()
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            if pid.value == proc.pid and user32.IsWindowVisible(hwnd):
                user32.ShowWindow(hwnd, 0)
            return True
        try:
            deadline = time.monotonic()+args.timeout
            while proc.poll() is None:
                user32.EnumWindows(hide_window, 0)
                if time.monotonic() > deadline:
                    raise TimeoutError('Live client exceeded timeout; inspect '+str(output/'client.log'))
                time.sleep(.05)
        finally:
            if proc.poll() is None:
                proc.terminate()
                proc.wait(timeout=15)
    if proc.returncode:
        raise SystemExit('Client failed; inspect '+str(output/'client.log'))
    if args.focus:
        marker = 'pilot-seat-live PASS' if args.focus == 'pilot-seat' else 'vehicle-live PASS'
        if marker not in (output/'client.log').read_text(errors='replace'):
            raise SystemExit('Missing completion marker: '+marker)
    print('Client completed; validate the screenshots and full-suite assertions before reporting success.')


if __name__ == '__main__':
    main()
