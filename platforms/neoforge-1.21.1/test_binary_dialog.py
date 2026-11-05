#!/usr/bin/env python3
"""Focused exact-JAR native binary dialog, real player settings packets and installed world scene."""
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
from test_transmitter_copy import install


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,help='Validate an existing frozen native standard JAR without rebuilding it')
    parser.add_argument('--xvfb',action='store_true')
    args=parser.parse_args()
    if args.xvfb and os.name=='nt':parser.error('--xvfb is Linux only')
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)));env.pop('MOD_CLASSES',None)
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(tempfile.mkdtemp(prefix='binary-dialog-native-',dir=str(base)))
    tasks=('copySmokeJar',) if args.jar else ('jar','copySmokeJar')
    with (output/'build.log').open('w') as log:subprocess.run(command(*tasks),cwd=PLATFORM,env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
    production=args.jar.resolve() if args.jar else next((PLATFORM/'build/libs').glob('*.jar'));fixture=next((PLATFORM/'build/test-fixtures').glob('*copy-smoke.jar'))
    sha=hashlib.sha256(production.read_bytes()).hexdigest();(output/'artifact.sha256').write_text(sha+'  '+production.name+'\n')
    with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    server_world=output/'server';world(server_world,port);properties=install(server_world,production,fixture);server_log=output/'server.log'
    server=launch(command('runServer','-PmodernServerRunDirectory='+str(server_world),'-PtransmitterPhase=binary-ui',*properties),server_log,env)
    try:
        deadline=time.monotonic()+120
        while 'Done (' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Binary GUI fixture server failed: '+str(server_log))
            time.sleep(.5)
        client=output/'client';client.mkdir();(client/'options.txt').write_text('guiScale:1\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:2\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
        properties=install(client,production,fixture);launch_client=command('runClient','-PmodernClientRunDirectory='+str(client),'-PmatterClientPort='+str(port),'-PtransmitterPhase=binary-ui',*properties)
        if args.xvfb:launch_client=['xvfb-run','-a','-s','-screen 0 1280x720x24',*launch_client];env['LIBGL_ALWAYS_SOFTWARE']='1'
        run(launch_client,output/'client.log','[vandorlabs][binary-gui] client PASS',env)
        check_log(output/'client.log','[vandorlabs][binary-gui] packaged-JAR client PASS');check_log(server_log,'[vandorlabs][binary-gui] packaged-JAR common PASS')
        from PIL import Image
        for name in ['binary-dialog-native.png','binary-world-native.png']:
            with Image.open(client/'screenshots'/name) as image:
                if image.size!=(1280,720):raise RuntimeError('Native capture dimensions: '+str(image.size))
                image.verify()
    finally:stop(server)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline and 'All dimensions are saved' not in (server_world/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
    check_log(server_log,'[vandorlabs][binary-gui] server-client-actions PASS');check_log(server_world/'logs/latest.log','All dimensions are saved')
    if hashlib.sha256(production.read_bytes()).hexdigest()!=sha:raise RuntimeError('Frozen production JAR changed during check')
    print('Exact native binary dialog acceptance PASS: '+str(output),flush=True)
    print('Runtime via ModDev; not standalone installer or hardware FPS acceptance.',flush=True)


if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.CalledProcessError,subprocess.TimeoutExpired) as error:
        print('Binary dialog smoke failed: '+str(error),file=sys.stderr);sys.exit(1)
