#!/usr/bin/env python3
"""Load the exact native mod JAR plus a separate copy fixture mod in isolated ModDev runtimes."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time
from test_cube import PLATFORM,ROOT,command,world,run,launch,stop,check_log,jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts


def install(game,production,fixture):
    mods=game/'mods';mods.mkdir(exist_ok=True)
    target=mods/production.name
    shutil.copy2(production,target);shutil.copy2(fixture,mods/fixture.name)
    sha=hashlib.sha256(target.read_bytes()).hexdigest()
    return ['-PcopyJar='+str(target),'-PcopySha='+sha]


@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,help='Exact existing native standard JAR to validate, without rebuilding it')
    parser.add_argument('--client',action='store_true')
    parser.add_argument('--xvfb',action='store_true')
    args=parser.parse_args()
    if args.xvfb and (not args.client or os.name=='nt'):parser.error('--xvfb requires --client on Linux')
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)));env.pop('MOD_CLASSES',None)
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='transmitter-copy-native-',dir=str(base)))
    tasks=('copySmokeJar',) if args.jar else ('jar','copySmokeJar')
    with (output/'build.log').open('w') as log:subprocess.run(command(*tasks),cwd=PLATFORM,env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
    production=args.jar.resolve() if args.jar else next((PLATFORM/'build/libs').glob('*.jar'))
    if not production.is_file():raise RuntimeError('Exact native JAR missing: '+str(production))
    fixture=next((PLATFORM/'build/test-fixtures').glob('*copy-smoke.jar'))
    (output/'artifact.sha256').write_text(hashlib.sha256(production.read_bytes()).hexdigest()+'  '+production.name+'\n')
    saved=output/'matrix-server';world(saved,0);properties=install(saved,production,fixture)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=copy-allocation',*properties),output/'matrix.log','[vandorlabs][transmitter-copy] copy-allocation PASS',env)
    check_log(output/'matrix.log','[vandorlabs][transmitter-copy] packaged-JAR PASS')
    if not args.client:return
    with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    server_world=output/'client-server';world(server_world,port);properties=install(server_world,production,fixture)
    server_log=output/'client-server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(server_world),'-PtransmitterPhase=copy-client',*properties),server_log,env)
    try:
        deadline=time.monotonic()+120
        while 'Done (' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Copy fixture server failed: '+str(server_log))
            time.sleep(.5)
        client=output/'client';client.mkdir();(client/'options.txt').write_text('guiScale:1\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:2\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
        properties=install(client,production,fixture)
        launch_client=command('runClient','-PmodernClientRunDirectory='+str(client),'-PmatterClientPort='+str(port),'-PtransmitterPhase=copy-client',*properties)
        if args.xvfb:launch_client=['xvfb-run','-a','-s','-screen 0 1280x720x24',*launch_client];env['LIBGL_ALWAYS_SOFTWARE']='1'
        run(launch_client,output/'client.log','[vandorlabs][transmitter-copy] client PASS',env)
        check_log(output/'client.log','[vandorlabs][transmitter-copy] packaged-JAR PASS')
        screenshot=client/'screenshots/transmitter-copy-native.png'
        from PIL import Image
        with Image.open(screenshot) as image:
            if image.size!=(1280,720):raise RuntimeError('Copy screenshot dimensions: '+str(image.size))
            image.verify()
    finally:stop(server)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline and 'All dimensions are saved' not in (server_world/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
    check_log(server_log,'[vandorlabs][transmitter-copy] server-client-actions PASS')
    check_log(server_world/'logs/latest.log','All dimensions are saved')
    print('Exact native mod JAR copy acceptance PASS: '+str(output),flush=True)
    print('Runtime launched via ModDev; this is not NeoForge installer or hardware FPS acceptance.',flush=True)


if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.CalledProcessError,subprocess.TimeoutExpired) as error:
        print('Transmitter copy smoke failed: '+str(error),file=sys.stderr);sys.exit(1)
