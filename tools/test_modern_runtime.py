#!/usr/bin/env python3
"""Run bounded disposable NeoForge smoke phases and require explicit runtime evidence."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time
from build_all import ROOT, java_home

PLATFORM=ROOT/'platforms/neoforge-1.21.1'
MARKERS={
    'write':['shape-chunk-write PASS', 'shape-write PASS', 'contained-fluids-write PASS', 'trigger-write PASS', 'joined-cube-write PASS', 'controls-write PASS', 'basic-housing-server PASS', 'screen-families-server PASS', 'filesystem-write PASS', 'wireless-write PASS', 'redstone-overlay-write PASS', 'screen-server PASS', 'server-write PASS', 'table-write PASS', 'expanded-controls-write PASS'],
    'read':['shape-chunk-read PASS', 'shape-read PASS', 'contained-fluids-read PASS', 'trigger-read PASS', 'joined-cube-read PASS', 'controls-read PASS', 'basic-housing-read PASS', 'screen-families-read PASS', 'filesystem-read PASS', 'wireless-read PASS', 'redstone-overlay-read PASS', 'screen-read PASS', 'server-read PASS', 'table-read PASS', 'expanded-controls-read PASS'],
    'client':['shape-chunks-client PASS', 'shape-model-parity PASS', 'housing-lighting PASS', 'shape-client PASS', 'shape-items-model PASS', 'shape-items-client PASS', 'contained-fluids-models PASS', 'contained-fluid-neighbors PASS', 'contained-fluid-light PASS', 'contained-fluids-client PASS', 'trigger-client PASS', 'joined-cube-client PASS', 'controls-client PASS', 'screen-filesystem-client PASS', 'screen-families-client PASS', 'filesystem-client PASS', 'block-client PASS', 'screen-client PASS', 'redstone-client PASS', 'wireless-client PASS', 'wireless-glyph-captures PASS', 'client-smoke PASS', 'expanded-controls-icons PASS', 'expanded-controls-models PASS', 'extended-controls-client PASS'],
}
CLIENT_IMAGES=['modern-filesystem-world.png','modern-filesystem-picker-portrait.png','modern-filesystem-picker-missing.png','modern-filesystem-picker-wide.png','modern-world.png','modern-programmable-block.png','modern-programmable-block-gui2.png',
               'modern-screen-scale3.png','modern-screen-scale2.png','modern-screen-world-a.png','modern-screen-world-b.png',
               'modern-redstone-overlay-on.png','modern-redstone-editor.png','modern-redstone-editor-scale3.png','modern-redstone-editor-scale2.png','modern-redstone-overlay-eight.png',
               'modern-wireless-world.png','modern-wireless-receiver-dialog.png','modern-wireless-transmitter-dialog.png']
CLIENT_IMAGES += [f'modern-wireless-on{level}-scale{scale}-{mode}.png' for level in (8,9) for scale in (2,3) for mode in ('actual','reference','negative','vanilla')]
CLIENT_IMAGES += [f'modern-family-{family}-{suffix}.png' for family in range(7) for suffix in ('scale3','scale2','surface-dialog','art-world','editor-scale3','editor-scale2','overlay-world')]
CLIENT_IMAGES += ['modern-family-1-art-off-target.png']
CLIENT_IMAGES += [f'modern-screen-filesystem-{suffix}.png' for suffix in ('picker-portrait','picker-missing','picker-wide','world-portrait','world-wide')]
CLIENT_IMAGES += [f'modern-joined-{mode}.png' for mode in range(3)]
CLIENT_IMAGES += [f'modern-controls-{suffix}.png' for suffix in ('button-on','button-off','rocker-on','dialog-scale3','dialog-scale2','dialog-edited','tilted','icons')]
CLIENT_IMAGES += [f'modern-trigger-{suffix}.png' for suffix in ('gui3','gui2','bands-exact','band-0','band-3','band-7','band-13')]
CLIENT_IMAGES += [f'modern-shape-{family}-{suffix}.png' for family in ('slab','stairs') for suffix in ('world','gui3','gui2','side','side-default','faces','dormant','restored','configured')]
CLIENT_IMAGES += [f'modern-shape-stairs-{suffix}.png' for suffix in ('slider1','slider2','slider4','slider8','sideways','sideways-world')]
CLIENT_IMAGES += ['modern-shape-items.png']
CLIENT_IMAGES += [f'modern-contained-{suffix}.png' for suffix in ('water-joined-above','water-reference','water-inside','water-joined-below','water-air-exit','water-drained','water-refilled','lava-inside','stair-water')]
CLIENT_IMAGES += [f'modern-shape-chunk-{index}-{phase}.png' for index in range(8) for phase in ('loaded','unloaded','forced-reference','reloaded')]
CLIENT_IMAGES += ['modern-shape-chunk-negative-sky.png']
EXPANDED_CONTROL_IDS = ('industrial_power_lever','compact_power_lever','small_power_lever','large_power_lever','thruster_lever','airliner_throttle','fighter_throttle','wall_slider')
CLIENT_IMAGES += [f'modern-control-{id}-{suffix}.png' for id in EXPANDED_CONTROL_IDS for suffix in ('world','active','gui3','gui2','edited','configured')]
CLIENT_IMAGES += [f'modern-expanded-control-icons-{index}.png' for index in range(8)]
WALL_CLIENT_IMAGES = [f'modern-wall-{kind}-{suffix}.png' for kind in ('plain','porthole','full') for suffix in ('world','main-scale3','main-scale2','configured')]
WALL_CLIENT_IMAGES += [f'modern-wall-{kind}-{suffix}.png' for kind in ('porthole','full') for suffix in ('side-default-scale3','side-scale3','side-scale2','stock-glass-scale3','stock-glass-scale2','custom-glass-scale3','custom-glass-scale2')]
WALL_CLIENT_IMAGES += [f'modern-wall-pane-{graphics}-{mode}.png' for graphics in ('fancy','fabulous') for mode in ('stock','mild','normal','strong')] + ['modern-wall-items.png']
CLIENT_IMAGES += WALL_CLIENT_IMAGES
WALL_CLIENT_MARKERS = ['wall-models PASS','wall-original-matrix PASS','wall-client PASS','wall-pane-gpu PASS','wall-items-model PASS','wall-items-client PASS','client-smoke PASS']
MARKERS['write'] += ['wall-write PASS','wall-joins PASS']
MARKERS['read'] += ['wall-read PASS']
MARKERS['client'] += ['wall-models PASS','wall-original-matrix PASS','wall-client PASS','wall-pane-gpu PASS','wall-items-model PASS','wall-items-client PASS']
FATAL=re.compile(r'\bFATAL\b|Encountered an unexpected exception|Exception in server tick loop|Failed to start the minecraft server|This crash report has been saved to|Preparing crash report|Caused by: java\.lang\.(?:AssertionError|IllegalStateException)|Modern client:|Modern runtime check:')


def verify(text,phase,suite='full'):
    required=WALL_CLIENT_MARKERS if phase=='client' and suite=='walls' else MARKERS[phase]
    missing=[marker for marker in required if '[vandorlabs][modern] '+marker not in text]
    failures=[line.strip()[:300] for line in text.splitlines() if FATAL.search(line)]
    if missing or failures:
        raise RuntimeError('Missing runtime markers: '+', '.join(missing)+'; fatal evidence: '+' | '.join(failures[:4]))
    if 'BUILD SUCCESSFUL' not in text:
        raise RuntimeError('Gradle process completion marker missing')
    ending='Stopping!' if phase=='client' else 'All dimensions are saved'
    if ending not in text:
        raise RuntimeError('Clean Minecraft shutdown marker missing: '+ending)
    return required


def descendants(parent):
    if os.name=='nt':return []
    result=subprocess.run(['ps','-eo','pid=,ppid='],capture_output=True,text=True,check=True)
    relations=[tuple(map(int,line.split())) for line in result.stdout.splitlines() if line.strip()]
    children=[];pending=[parent]
    while pending:
        owner=pending.pop()
        found=[pid for pid,ppid in relations if ppid==owner]
        children.extend(found);pending.extend(found)
    return children


def stop_owned(process):
    if os.name=='nt':
        subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],capture_output=True)
        return
    def identity(pid):
        try:
            fields=Path('/proc/%d/stat'%pid).read_text().rsplit(')',1)[1].split()
            return None if fields[0]=='Z' else fields[19]
        except (FileNotFoundError,ProcessLookupError):return None
    owned=[(pid,identity(pid)) for pid in descendants(process.pid)+[process.pid]]
    def send(sig):
        for pid,started in owned:
            if started is not None and identity(pid)==started:
                try:os.kill(pid,sig)
                except ProcessLookupError:pass
    # Only the process tree and process identities created by this invocation
    # are eligible for cleanup, including Gradle's single-use daemon children.
    send(signal.SIGTERM)
    deadline=time.monotonic()+10
    while time.monotonic()<deadline and any(started is not None and identity(pid)==started for pid,started in owned):
        time.sleep(.1)
    send(signal.SIGKILL)
    process.wait(timeout=5)



def run(phase,args):
    env=dict(os.environ,JAVA_HOME=java_home(21,'VANDOR_JAVA21_HOME'))
    env['PATH']=str(Path(env['JAVA_HOME'])/'bin')+os.pathsep+env.get('PATH','')
    wrapper=PLATFORM/('gradlew.bat' if os.name=='nt' else 'gradlew')
    task='runClient' if phase=='client' else 'runServer'
    command=[str(wrapper),task,'--no-daemon','-PpythonExecutable='+sys.executable]
    command+=['-PmodernClientSmoke=true'] if phase=='client' else ['-PmodernSmoke='+phase]
    if phase=='client':
        command+=['-PmodernClientPort='+str(args.port)]
        if getattr(args,'suite','full')=='walls':command+=['-PmodernClientSuite=walls']
    if args.compiled:
        for task_name in ('compileJava','processResources','generateModernResources'):command+=['-x',task_name]
    if os.name=='nt':command=[os.environ.get('COMSPEC','cmd.exe'),'/d','/v:off','/s','/c','"'+' '.join('"'+item+'"' for item in command)+'"']
    if phase=='client' and args.xvfb and os.name!='nt':command=['xvfb-run','-a','-s','-screen 0 1280x720x24',*command]
    log=args.log_dir/f'{phase}.log';log.parent.mkdir(parents=True,exist_ok=True)
    print(f'Running modern {phase}; timeout {args.timeout}s; log {log}',flush=True)
    started_ns=time.time_ns()
    with log.open('w') as stream:
        process=subprocess.Popen(command,cwd=PLATFORM,env=env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=os.name!='nt')
        previous_handler=signal.getsignal(signal.SIGTERM)
        def interrupted(signum,frame):raise KeyboardInterrupt()
        signal.signal(signal.SIGTERM,interrupted)
        try:
            try:code=process.wait(timeout=args.timeout)
            except subprocess.TimeoutExpired:
                stop_owned(process)
                raise RuntimeError(f'Modern {phase} exceeded {args.timeout}s; owned process tree stopped')
            except KeyboardInterrupt:
                stop_owned(process)
                raise
        finally:signal.signal(signal.SIGTERM,previous_handler)
    if code!=0:raise RuntimeError(f'Modern {phase} process exit {code}; inspect {log}')
    suite=getattr(args,'suite','full')
    markers=verify(log.read_text(errors='replace'),phase,suite)
    images={}
    if phase=='client':
        from PIL import Image
        for name in WALL_CLIENT_IMAGES if suite=='walls' else CLIENT_IMAGES:
            image=PLATFORM/'run/client/screenshots'/name
            if not image.is_file() or image.stat().st_mtime_ns<started_ns:
                raise RuntimeError('Current-run screenshot missing or stale: '+name)
            with Image.open(image) as png:png.verify()
            images[name]=hashlib.sha256(image.read_bytes()).hexdigest()
        metadata_names=('modern-wall-emission.json','modern-wall-original-parity.json','modern-wall-pane-gpu.json','modern-wall-item-goldens.json') if suite=='walls' else ('modern-wireless-glyphs.json','modern-shape-model-parity.json','modern-shape-chunks.json','modern-wall-emission.json','modern-wall-original-parity.json','modern-wall-pane-gpu.json','modern-wall-item-goldens.json')
        for filename in metadata_names:
            metadata=PLATFORM/'run/client/screenshots'/filename
            if not metadata.is_file() or metadata.stat().st_mtime_ns<started_ns:
                raise RuntimeError('Current-run model/glyph metadata missing or stale: '+filename)
            content=json.loads(metadata.read_text())
            if filename=='modern-wall-emission.json' and (content.get('format')!=1 or len(content.get('cases',[]))!=3):raise RuntimeError('Invalid current-run wall emission report')
            if filename=='modern-wall-original-parity.json' and (content.get('format')!=1 or len(content.get('cases',[]))!=696):raise RuntimeError('Invalid current-run wall original-model matrix')
            images[metadata.name]=hashlib.sha256(metadata.read_bytes()).hexdigest()
        if getattr(args,'legacy_wall_reference',None):
            subprocess.run([sys.executable,str(ROOT/'tools/test_wall_original_parity.py'),'--legacy',str(args.legacy_wall_reference),'--modern',str(PLATFORM/'run/client/screenshots/modern-wall-original-parity.json'),'--report',str(args.log_dir/'wall-original-parity.json')],check=True)
        wall_command=[sys.executable,str(ROOT/'tools/test_modern_wall_images.py'),'--screenshots',str(PLATFORM/'run/client/screenshots'),'--output-dir',str(args.log_dir/'wall-pixels')]
        if getattr(args,'legacy_wall_profiles',None):wall_command+=['--legacy-profiles',str(args.legacy_wall_profiles)]
        subprocess.run(wall_command,check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/test_wall_pane_gpu.py'),'--screenshots',str(PLATFORM/'run/client/screenshots'),'--report',str(args.log_dir/'wall-pane-gpu.json')],check=True)
        if bool(getattr(args,'legacy_wall_item_models',None)) != bool(getattr(args,'legacy_wall_item_image',None)):raise ValueError('Both legacy wall item references are required')
        if getattr(args,'legacy_wall_item_models',None):
            subprocess.run([sys.executable,str(ROOT/'tools/compare_housing_item_goldens.py'),'--legacy',str(args.legacy_wall_item_models),'--modern',str(PLATFORM/'run/client/screenshots/modern-wall-item-goldens.json'),'--report',str(args.log_dir/'wall-item-models.json')],check=True)
            subprocess.run([sys.executable,str(ROOT/'tools/test_housing_item_images.py'),'--legacy',str(args.legacy_wall_item_image),'--modern',str(PLATFORM/'run/client/screenshots/modern-wall-items.png'),'--cases','36','--report',str(args.log_dir/'wall-item-images.json')],check=True)
        if suite=='walls':
            print(f'Modern client walls PASS: {len(markers)} runtime markers, {len(WALL_CLIENT_IMAGES)} fresh PNGs and actual native model emission',flush=True)
            return {'phase':phase,'suite':suite,'status':'PASS','markers':markers,'image_sha256':images,'visual_checks':{'walls':'PASS','wall_models':'PASS','cross_version_wall_models':'PASS' if getattr(args,'legacy_wall_reference',None) else 'not_requested','legacy_wall_profiles':'PASS' if getattr(args,'legacy_wall_profiles',None) else 'not_requested'}}
        if args.legacy_shape_reference:
            subprocess.run([sys.executable,str(ROOT/'tools/compare_shape_model_parity.py'),
                            '--legacy',str(args.legacy_shape_reference),
                            '--modern',str(PLATFORM/'run/client/screenshots/modern-shape-model-parity.json'),
                            '--report',str(args.log_dir/'shape-model-parity.json')],check=True)
        subprocess.run([sys.executable,str(PLATFORM/'analyze_wireless_glyphs.py'),str(PLATFORM/'run/client/screenshots')],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/test_modern_filesystem_images.py'),
                        '--screenshots',str(PLATFORM/'run/client/screenshots'),
                        '--output-dir',str(args.log_dir/'filesystem-pixels')],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/test_modern_filesystem_images.py'),
                        '--screenshots',str(PLATFORM/'run/client/screenshots'),'--screen',
                        '--output-dir',str(args.log_dir/'screen-filesystem-pixels')],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/test_modern_screen_family_images.py'),
                        '--screenshots',str(PLATFORM/'run/client/screenshots'),
                        '--output-dir',str(args.log_dir/'screen-family-pixels')],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/test_modern_control_images.py'),'--screenshots',str(PLATFORM/'run/client/screenshots'),'--output-dir',str(args.log_dir/'controls-pixels')],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/test_modern_joined_images.py'),'--screenshots',str(PLATFORM/'run/client/screenshots'),'--output-dir',str(args.log_dir/'joined-pixels')],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/test_modern_expanded_control_images.py'),'--screenshots',str(PLATFORM/'run/client/screenshots'),'--output-dir',str(args.log_dir/'expanded-controls-pixels')],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/test_modern_trigger_images.py'),'--screenshots',str(PLATFORM/'run/client/screenshots'),'--output-dir',str(args.log_dir/'trigger-pixels')],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/test_modern_shape_images.py'),'--screenshots',str(PLATFORM/'run/client/screenshots'),'--output-dir',str(args.log_dir/'shape-pixels')],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/validate_shape_chunk_images.py'),str(PLATFORM/'run/client/screenshots'),'--report',str(args.log_dir/'shape-chunk-pixels.json')],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/test_modern_fluid_images.py'),'--screenshots',str(PLATFORM/'run/client/screenshots'),'--output-dir',str(args.log_dir/'fluid-pixels')],check=True)
    print(f'Modern {phase} PASS: all {len(markers)} runtime markers and clean process completion',flush=True)
    return {'phase':phase,'status':'PASS','markers':markers,'image_sha256':images,'visual_checks':{'filesystem':'PASS','wireless_glyphs':'PASS','screen_families':'PASS','screen_filesystem':'PASS','controls':'PASS','joined_cubes':'PASS','trigger':'PASS','expanded_controls':'PASS','shapes':'PASS','shape_chunks':'PASS','contained_fluids':'PASS','walls':'PASS','wall_models':'PASS','cross_version_wall_models':'PASS' if getattr(args,'legacy_wall_reference',None) else 'not_requested','cross_version_shape_models':'PASS' if args.legacy_shape_reference else 'not_requested'} if phase=='client' else {}}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase',choices=['write','read','both','client'],default='both')
    parser.add_argument('--verify-log',type=Path,help='Check an already completed log instead of launching a process')
    parser.add_argument('--suite',choices=['full','walls'],default='full',help='Run every client family or the isolated flat-wall gate')
    parser.add_argument('--legacy-wall-profiles',type=Path,help='Actual legacy flat-wall profile captures for matched GUI comparisons')
    parser.add_argument('--legacy-wall-reference',type=Path,help='Actual original-renderer wall matrix for independent native geometry/material comparison')
    parser.add_argument('--legacy-wall-item-models',type=Path)
    parser.add_argument('--legacy-wall-item-image',type=Path)
    parser.add_argument('--legacy-shape-reference',type=Path,help='Compare fresh native slab/stair exports with an actual legacy model export')
    parser.add_argument('--compiled',action='store_true',help='Use the existing frozen classes/resources without recompilation')
    parser.add_argument('--port',type=int,default=25570,help='Local fixture server port for rendered checks')
    parser.add_argument('--xvfb',action='store_true',help='Run the rendered client in an isolated virtual display')
    parser.add_argument('--timeout',type=int,default=420)
    parser.add_argument('--log-dir',type=Path,default=ROOT/'build/reports/modern-runtime')
    args=parser.parse_args()
    if args.timeout<=0:parser.error('--timeout must be positive')
    if args.suite!='full' and args.phase!='client':parser.error('--suite applies only to --phase client')
    if args.verify_log:
        if args.phase=='both':parser.error('--verify-log requires one phase')
        verify(args.verify_log.read_text(errors='replace'),args.phase,args.suite)
        print(f'Modern {args.phase} log PASS: complete runtime markers, no fatal evidence, clean process completion')
        return
    phases=['write','read'] if args.phase=='both' else [args.phase]
    results=[];args.log_dir.mkdir(parents=True,exist_ok=True);report=args.log_dir/'result.json'
    report.write_text(json.dumps({'schema':1,'status':'RUNNING','phases':results},indent=2)+'\n')
    try:
        for phase in phases:
            results.append(run(phase,args))
            report.write_text(json.dumps({'schema':1,'status':'RUNNING','phases':results},indent=2)+'\n')
    except BaseException as error:
        report.write_text(json.dumps({'schema':1,'status':'FAILED','phases':results,'error':str(error)},indent=2)+'\n')
        raise
    report.write_text(json.dumps({'schema':1,'status':'PASS','phases':results},indent=2)+'\n')

if __name__=='__main__':
    try:main()
    except KeyboardInterrupt:
        print('Modern runtime interrupted; owned process tree stopped',file=sys.stderr);sys.exit(130)
    except (RuntimeError,subprocess.SubprocessError) as error:
        print('Modern runtime FAILED: '+str(error),file=sys.stderr);sys.exit(1)
