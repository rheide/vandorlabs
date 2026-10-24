#!/usr/bin/env python3
"""Run an isolated, muted Forge client in the background using installed libraries.

Requires the ignored build/tooling Java 8 runtime, Forge profile and compatibility fixtures.
The built mod stays in build/libs; only external test fixtures are copied.
"""
import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import zipfile


def validate_completion(client_log, marker, duplifier_only=False):
    markers = ('armor-duplifier-matrix PASS', 'duplifier-dialog-layout PASS',
               'armor-duplifier-interactions PASS', 'armor-custom-picker PASS') if duplifier_only else (marker,)
    for required in markers:
        if required not in client_log:
            raise SystemExit('Missing completion marker: '+required)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server')
    parser.add_argument('--role', choices=['driver','observer'])
    parser.add_argument('--java', default=str(Path(__file__).resolve().parents[1]/'build/tooling/jdk8u504-b01/bin/java.exe'))
    parser.add_argument('--minecraft-dir', type=Path,
                        default=Path(__file__).resolve().parents[1]/'build/tooling/minecraft')
    parser.add_argument('--forge-profile', default='1.12.2-forge-14.23.5.2860')
    parser.add_argument('--compat-mods', type=Path)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--world-copy', type=Path, help='Test a workspace copy of an existing save')
    parser.add_argument('--turntable-position', help='Exercise a controller at x,y,z in a disposable --world-copy save')
    parser.add_argument('--client-options', type=Path, help='Use supplied graphics and input settings in the isolated client')
    parser.add_argument('--max-heap', default='3G', help='Maximum heap for the isolated client, such as 3G or 4096M')
    parser.add_argument('--all-mods', type=Path, help='Use all installed mod JARs for compatibility reproduction')
    parser.add_argument('--vehicle-preservation-mods', type=Path, help='Additional Mekanism and Malisis fixtures for mandatory preservation checks')
    parser.add_argument('--profile-turntables', action='store_true', help='Log turntable render and server collision CPU timings every ten seconds')
    parser.add_argument('--trace-chunks', action='store_true', help='Trace tile-map mutations during chunk packets')
    parser.add_argument('--focus', choices=['bridge-machines', 'render-regressions', 'canopy-glass-order', 'gear-animations', 'canopy-animations', 'connection-acceptance', 'large-canopy', 'vehicle-controller', 'regular-dialog', 'pilot-inspection', 'armor', 'pilot-seat', 'vehicles', 'vehicle-render', 'vehicle-taxi', 'hover-controls', 'wheel-motion', 'moat', 'pulse-screen', 'followups', 'vehicle-status', 'vehicle-motion', 'vehicle-network', 'canopies', 'diagonal-joins', 'ramps', 'redstone-inversion', 'doors', 'block-categories', 'machinery', 'glass-appearance', 'fluids'])
    parser.add_argument('--disable-categories', help='Comma-separated category keys for isolated availability checks')
    parser.add_argument('--shot-prefix', help='Restrict visual scenes to one named family or arrangement')
    parser.add_argument('--start-shot', help='Resume visual scenes at this exact name, retaining later runtime checks')
    parser.add_argument('--shot-shards', type=int, default=1, help='Partition the visual matrix across independent clients')
    parser.add_argument('--shot-shard', type=int, default=0, help='Zero-based partition to capture')
    parser.add_argument('--shot-limit', type=int, default=0, help='Limit scenes for a capture smoke check')
    parser.add_argument('--acceptance-pilot', action='store_true', help='Capture only the junction layout pilot')
    parser.add_argument('--connection-topology', choices=['l', 't', 'x'], default='l', help='Regular-wall acceptance page to capture')
    parser.add_argument('--connection-family', choices=['regular', 'diagonal'], default='regular', help='Regular topology or sampled diagonal size page')
    parser.add_argument('--connection-mode', type=int, choices=[0, 1, 2], default=0,
                        help='Diagonal size: 0 half-width/full-height; 1 full-width/full-height; 2 full-width/half-height')
    parser.add_argument('--connection-baseline', type=Path, help='Accepted screenshot baseline (defaults to the regular-wall baseline)')
    parser.add_argument('--connection-opposite-feedback', type=Path, help='Capture only flagged Wrong diagonal cases from this feedback at the opposite diagnostic angle')
    parser.add_argument('--animation-filter', help='Comma-separated animation scene IDs')
    parser.add_argument('--timeout', type=int, default=1200)
    args = parser.parse_args()
    if not re.fullmatch(r'[1-9][0-9]*[mMgG]', args.max_heap):
        parser.error('--max-heap must be a positive size in M or G')
    if args.connection_family == 'regular' and args.connection_mode != 0:
        parser.error('--connection-mode requires --connection-family diagonal')
    diagonal_scopes = ['diagonal-half-width-full-height', 'diagonal-full-width-full-height', 'diagonal-full-width-half-height']
    connection_scope = (diagonal_scopes[args.connection_mode] if args.connection_family == 'diagonal'
                        else 'regular-'+args.connection_topology)
    opposite_names = []
    if args.connection_opposite_feedback:
        if args.focus != 'connection-acceptance' or args.connection_family != 'diagonal' or args.acceptance_pilot or args.connection_baseline or args.shot_prefix or args.start_shot or args.shot_limit or args.shot_shards != 1 or args.shot_shard != 0:
            parser.error('Opposite capture requires unfiltered diagonal connection acceptance without a baseline override')
        from review_connections import secondary_scene_names
        try:
            opposite_names = secondary_scene_names(args.connection_opposite_feedback, connection_scope)
        except (OSError, ValueError, TypeError) as error:
            parser.error(str(error))
    if os.name != 'nt':
        parser.error('Use run.sh on Linux; this launcher hides native Windows client windows.')
    root = Path(__file__).resolve().parents[1]
    canopy_scenes={scene['id'] for scene in json.loads((root/'src/main/resources/assets/vandorlabs/data/documentation_animations.json').read_text()) if scene['kind']==('gear' if args.focus=='gear-animations' else 'canopy')}
    selected_animations=set(args.animation_filter.split(',')) if args.animation_filter else canopy_scenes
    if args.animation_filter and (args.focus not in ('canopy-animations','gear-animations') or not selected_animations or not selected_animations<=canopy_scenes):
        parser.error('--animation-filter requires an animation focus and known scene IDs')
    version = re.search(r"^version = '([^']+)'", (root/'build.gradle').read_text(), re.M)[1]
    jar = (args.jar or root/'build/libs'/('vandorlabs-'+version+'.jar')).resolve()
    if not jar.is_file():
        parser.error('Build the standard mod JAR first: '+str(jar))
    output = root/'testclient'/('render-run.'+time.strftime('%Y%m%d-%H%M%S')+'-'+str(os.getpid())+('-'+args.role if args.role else ''))
    game = output/'game'
    natives = output/'natives'
    game.mkdir(parents=True)
    if args.focus == 'connection-acceptance':
        (output/'capture-metadata.json').write_text(json.dumps({
            'schema': 'vandorlabs-connection-capture-v1', 'jar': jar.name,
            'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
            'topology': 'l-t-x' if args.connection_family == 'diagonal' else args.connection_topology,
            'image_size': [1920, 1080],
            'family': args.connection_family, 'mode': args.connection_mode, 'scope': connection_scope,
            'view': 'opposite lower oblique' if opposite_names else 'primary', 'selected_cases': opposite_names,
        }, indent=2), encoding='utf-8')
    if args.disable_categories:
        categories=args.disable_categories.split(',')
        if any(not re.fullmatch('[a-z_]+', key) for key in categories):
            parser.error('Invalid category key')
        (game/'config').mkdir()
        (game/'config/vandorlabs-blocks.cfg').write_text('blocks {\n'+''.join(' B:'+key+'=false\n' for key in categories)+'}\n')
    natives.mkdir()
    options = args.client_options.read_text() if args.client_options else ''
    overrides = {'soundCategory_master': '0.0', 'pauseOnLostFocus': 'false', 'fullscreen': 'false'}
    if not args.client_options and args.focus in ('ramps', 'canopy-animations', 'gear-animations'):
        overrides.update(enableVsync='false', maxFps='120')
    for key, value in overrides.items():
        pattern = r'(?m)^'+re.escape(key)+r':[^\r\n]*$'
        options = re.sub(pattern, key+':'+value, options) if re.search(pattern, options) else options.rstrip()+'\n'+key+':'+value+'\n'
    (game/'options.txt').write_text(options)
    if args.client_options:
        optifine_options = args.client_options.with_name('optionsof.txt')
        if optifine_options.is_file():
            shutil.copyfile(optifine_options, game/'optionsof.txt')
    if args.world_copy:
        shutil.copytree(args.world_copy, game/'saves'/('repro-'+output.name))
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
    compat = args.compat_mods or Path(os.environ.get('VANDOR_LABS_COMPAT_MODS', str(root/'build/tooling/compat-mods')))
    (game/'mods').mkdir()
    if not args.all_mods:
        for name in ['worldedit-forge-mc1.12.2-6.1.10-dist.jar',
                     'BetterBuildersWands-1.12-0.11.1.245+69d0d70.jar', 'ImmersiveEngineering-0.12-98.jar']:
            shutil.copyfile(compat/name, game/'mods'/name)
    if args.vehicle_preservation_mods:
        for name in ['Mekanism-1.12.2-9.8.2.389.jar','malisiscore-1.12.2-6.5.1.jar','malisisdoors-1.12.2-7.3.0.jar']:
            shutil.copyfile(args.vehicle_preservation_mods/name,game/'mods'/name)
    if args.all_mods:
        for source in args.all_mods.glob('*.jar'):
            if not source.name.startswith('vandorlabs-'):
                shutil.copyfile(source,game/'mods'/source.name)
    properties = ['-Dvandorlabs.reprolab='+str(output), '-Djava.library.path='+str(natives)]
    properties.append('-Dvandorlabs.connectionAcceptanceTopology='+args.connection_topology)
    properties.append('-Dvandorlabs.connectionAcceptanceFamily='+args.connection_family)
    properties.append('-Dvandorlabs.connectionAcceptanceMode='+str(args.connection_mode))
    if opposite_names:
        properties += ['-Dvandorlabs.connectionAcceptanceOpposite=true', '-Dvandorlabs.connectionAcceptanceShotNames='+','.join(opposite_names)]
    properties += ['-Dvandorlabs.reproShotShards='+str(args.shot_shards), '-Dvandorlabs.reproShotShard='+str(args.shot_shard), '-Dvandorlabs.reproShotLimit='+str(args.shot_limit)]
    if args.acceptance_pilot:
        properties.append('-Dvandorlabs.connectionAcceptancePilot=true')
    if args.shot_prefix:
        properties.append('-Dvandorlabs.reproShotPrefix='+args.shot_prefix)
    if args.start_shot:
        properties.append('-Dvandorlabs.reproStartShot='+args.start_shot)
    if args.vehicle_preservation_mods:
        properties.append('-Dvandorlabs.foreignVehicleChecks=true')
    if args.world_copy:
        properties.append('-Dvandorlabs.existingWorldChecks=true')
    if args.profile_turntables:
        properties += ['-Dvandorlabs.profileTurntables=true']
    if args.turntable_position:
        if not args.world_copy or not re.fullmatch(r'-?\d+,-?\d+,-?\d+',args.turntable_position):
            parser.error('--turntable-position requires --world-copy and x,y,z coordinates')
        properties += ['-Dvandorlabs.checkTurntableRotation=true','-Dvandorlabs.turntableTestPosition='+args.turntable_position]
    if args.trace_chunks:
        properties.append('-Dvandorlabs.traceChunkTiles=true')
    if args.focus:
        properties.append('-Dvandorlabs.'+{'bridge-machines':'bridgeMachineChecksOnly', 'render-regressions':'renderRegressionChecksOnly', 'canopy-glass-order':'canopyGlassOrderChecksOnly', 'gear-animations':'gearAnimationChecksOnly', 'canopy-animations':'canopyAnimationChecksOnly', 'connection-acceptance':'connectionAcceptanceOnly', 'large-canopy':'largeCanopyChecksOnly', 'armor':'armorChecksOnly', 'regular-dialog':'regularDialogChecksOnly', 'vehicle-controller':'vehicleControllerChecksOnly', 'pilot-inspection':'pilotInspectionChecksOnly', 'pilot-seat': 'pilotSeatChecksOnly', 'vehicles': 'vehicleChecksOnly', 'vehicle-render': 'vehicleChecksOnly', 'vehicle-taxi': 'vehicleTaxiChecksOnly', 'followups': 'followupChecksOnly', 'pulse-screen': 'pulseScreenChecksOnly', 'moat': 'moatChecksOnly', 'wheel-motion': 'wheelMotionChecksOnly', 'hover-controls': 'hoverControlsChecksOnly', 'vehicle-status': 'vehicleStatusChecksOnly', 'vehicle-motion': 'vehicleMotionChecksOnly', 'vehicle-network': 'vehicleNetworkChecksOnly', 'canopies': 'canopyChecksOnly', 'diagonal-joins': 'diagonalJoinChecksOnly', 'glass-appearance':'glassAppearanceChecksOnly', 'fluids':'fluidChecksOnly', 'machinery':'machineryChecksOnly', 'block-categories':'blockCategoryChecksOnly', 'doors':'doorChecksOnly', 'redstone-inversion':'redstoneInversionChecksOnly', 'ramps': 'rampChecksOnly'}[args.focus]+'=true')
        if args.focus=='vehicle-render':properties.append('-Dvandorlabs.vehicleRenderChecksOnly=true')
        properties.append('-Dvandorlabs.vehicleDiagnostics=true')
    if args.focus in ('canopy-animations','gear-animations'):
        properties += ['-Dvandorlabs.documentationAnimations=true', '-Dvandorlabs.documentationAnimationFilter='+','.join(sorted(selected_animations))]
    if args.focus == 'ramps':
        properties += ['-Dvandorlabs.documentationAnimations=true', '-Dvandorlabs.documentationAnimationFilter=ramp-escalator-up,ramp-escalator-down,ramp-escalator-walkway,ramp-escalator-curve-in']
    if args.role:
        properties.append('-Dvandorlabs.networkRole='+args.role)
        properties.append('-Dvandorlabs.vehicleDiagnostics=true')
    command = [args.java, '-Xmx'+args.max_heap]+(['-XX:+UseG1GC', '-XX:MaxGCPauseMillis=20'] if args.focus in ('ramps','canopy-animations','gear-animations') else [])+properties+['-cp', ';'.join(classpath),
        'net.minecraft.launchwrapper.Launch', '--tweakClass', 'net.minecraftforge.fml.common.launcher.FMLTweaker',
        '--gameDir', str(game), '--assetsDir', str(base/'assets'), '--assetIndex', '1.12',
        '--username', ('VDriver' if args.role=='driver' else 'VObserver') if args.role else 'REPROBOT', '--accessToken', 'test', '--version', args.forge_profile,
        '--width', '1920' if args.focus == 'connection-acceptance' else '896' if args.focus in ('ramps','canopy-animations','gear-animations') else '1280', '--height', '1080' if args.focus == 'connection-acceptance' else '504' if args.focus in ('ramps','canopy-animations','gear-animations') else '720']
    if args.server:
        host,port=args.server.split(':')
        command+=['--server',host,'--port',port]
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
    client_log=(output/'client.log').read_text(errors='replace')
    if 'domain vandorlabs is missing' in client_log or 'Exception loading model' in client_log or 'Failed to load texture: vandorlabs:' in client_log:
        raise SystemExit('Invalid Vandor asset; inspect '+str(output/'client.log'))
    if 'Exception caught during firing event' in client_log or 'A severe problem occurred during the spawning of an entity' in client_log:
        raise SystemExit('Asynchronous gameplay exception; inspect '+str(output/'client.log'))
    if args.world_copy and 'existing-world-load PASS' not in (output/'client.log').read_text(errors='replace'):
        raise SystemExit('Existing world did not complete its load check: '+str(output/'client.log'))
    if args.turntable_position and 'existing-turntable PASS' not in client_log:
        raise SystemExit('Existing turntable did not complete its rotation/return check: '+str(output/'client.log'))
    if args.focus:
        marker = {'bridge-machines':'bridge-machines PASS', 'render-regressions':'render-regressions PASS', 'canopy-glass-order':'canopy-glass-order PASS', 'gear-animations':'documentation-animation PASS', 'canopy-animations':'documentation-animation PASS', 'connection-acceptance':'connection-acceptance PASS', 'large-canopy':'large-canopy-live PASS', 'armor':'role-armor PASS', 'regular-dialog':'regular-dialog-client PASS', 'vehicle-controller':'vehicle-controller-live PASS', 'pilot-inspection':'pilot-inspection PASS', 'pilot-seat':'pilot-seat-live PASS', 'vehicles':'vehicle-live PASS', 'vehicle-render':'vehicle-render-live PASS', 'vehicle-taxi':'vehicle-taxi PASS', 'followups':'followups-client PASS', 'pulse-screen':'screen-mode-client PASS', 'moat':'moat-client PASS', 'wheel-motion':'wheel-motion-client PASS', 'hover-controls':'hover-client-controls PASS', 'vehicle-status':'vehicle-status-client PASS', 'vehicle-motion':'vehicle-motion PASS', 'vehicle-network':'vehicle-network PASS', 'canopies':'canopy-live PASS', 'diagonal-joins':'diagonal-joins-live PASS', 'glass-appearance':'glass-render-live PASS', 'fluids':'fluid-focused PASS', 'machinery':'machinery-render-live PASS', 'block-categories':'block-categories-live PASS', 'doors':'programmable-doors-live PASS', 'redstone-inversion':'redstone-inversion-live PASS', 'ramps':'escalator-live PASS'}[args.focus]
        validate_completion(client_log, marker, args.focus == 'armor'
                            and os.environ.get('VANDOR_LABS_ARMOR_DUPLIFIER_ONLY', '').lower() == 'true')
        if args.focus == 'fluids':
            for required in ('fluid-render-bounds PASS cases=132 cameraViews=10', 'fluid-lightmap PASS cases=256', 'fluid-boundary-matrix PASS', 'submerged-contact-views PASS', 'fluid-pool PASS'):
                if required not in client_log:
                    raise SystemExit('Missing fluid check: '+required)
        if args.focus == 'vehicles':
            for required in ('hover-client-controls PASS', 'hover-world PASS', 'captured-controls PASS'):
                if required not in client_log:
                    raise SystemExit('Missing vehicle control check: '+required)
        if args.focus in ('canopy-animations','gear-animations'):
            if {path.parent.name for path in output.glob('*/capture.json')} != selected_animations:
                raise SystemExit('Incomplete or out-of-scope animation set')
            for scene in selected_animations:
                if 'documentation-animation PASS '+scene+' frames=' not in client_log:
                    raise SystemExit('Missing animation: '+scene)
        if args.focus == 'ramps':
            for mode in (1, 2, 3, 4, 5):
                expected = 'escalator-player-live PASS real client/server rider transport and redstone stop, mode='+str(mode)
                if expected not in client_log:
                    raise SystemExit('Missing player ride check: '+expected)
            expected_scenes = {'ramp-escalator-up', 'ramp-escalator-down', 'ramp-escalator-walkway', 'ramp-escalator-curve-in'}
            if {path.parent.name for path in output.glob('*/capture.json')} != expected_scenes:
                raise SystemExit('Incomplete or out-of-scope ramp animation set')
            for scene in expected_scenes:
                if 'documentation-animation PASS '+scene+' frames=' not in client_log:
                    raise SystemExit('Missing escalator animation: '+scene)
        if args.focus == 'glass-appearance':
            subprocess.run([sys.executable, str(root/'testclient/analyze_diagonal_frame_edges.py'), str(output)], check=True)
        if args.focus == 'pilot-seat':
            subprocess.run([sys.executable, str(root/'testclient/analyze_pilot_seat.py'), str(output)], check=True)
        if args.focus == 'connection-acceptance':
            if args.connection_family == 'diagonal':
                marker = 'acceptance fixture coverage PASS 60 diagonal mode='+str(args.connection_mode)+' L18 T24 X18'
                if marker not in client_log:
                    raise SystemExit('Missing diagonal sample coverage checks.')
            if 'flat-panel-material-L PASS 72 ordered fixtures' not in client_log:
                raise SystemExit('Missing regular wall material-join runtime checks.')
            if 'flat-panel-material-T PASS 864 material/direction/hand/tail fixtures' not in client_log:
                raise SystemExit('Missing regular T material-ownership runtime checks.')
            if 'flat-glass-cross-frame PASS 1296 material/facing fixtures' not in client_log:
                raise SystemExit('Missing glass cross-junction frame runtime checks.')
            scope = connection_scope
            if opposite_names:
                from connection_baseline import read_manifest
                from review_connections import secondary_png_digest
                if set(read_manifest(output)) != set(opposite_names):
                    raise SystemExit('Diagnostic opposite capture case IDs differ from requested '+str(len(opposite_names)))
                for name in opposite_names:
                    secondary_png_digest(output/('shot_'+name+'.png'))
                print('Diagnostic opposite capture PASS '+str(len(opposite_names))+' flagged cases')
            if not args.acceptance_pilot and not opposite_names:
                subprocess.run([sys.executable, str(root/'testclient/review_connections.py'), str(output), '--scope', scope, '--no-contact-sheet'] + (['--compact'] if args.connection_family == 'regular' and args.connection_topology in ('t', 'x') else []), check=True)
            from connection_baseline import default_baseline
            baseline = args.connection_baseline or default_baseline(scope if args.connection_family == 'diagonal' else args.connection_topology)
            complete_capture = (not opposite_names and not args.acceptance_pilot and not args.shot_prefix and not args.start_shot
                                and not args.shot_limit and args.shot_shards == 1)
            if complete_capture and (baseline/'baseline.json').is_file():
                checked = subprocess.run([sys.executable, str(root/'testclient/connection_baseline.py'), 'compare',
                                          '--baseline', str(baseline), '--capture', str(output),
                                          '--output', str(output/'connection-regression')])
                if checked.returncode:
                    raise SystemExit('Accepted connection screenshots need review: '+str(output/'connection-regression/index.html'))
            elif args.connection_baseline:
                raise SystemExit('Baseline comparison requires an existing baseline and a complete capture.')
    print('Client completed; validate the screenshots and full-suite assertions before reporting success.')


if __name__ == '__main__':
    main()
