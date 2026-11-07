#!/usr/bin/env python3
"""Actual vanilla chip use on private Screen hosts, plus whole-process saved-owner replay."""
import argparse
import os
import socket
import shutil
import time
from pathlib import Path
from test_cube import ROOT, command, launch, run, stop, check_log, jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory, retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--xvfb',action='store_true')
    parser.add_argument('--restart-from',type=Path,help='Replay only the read phase on a copy of an already stopped fixture server')
    args=parser.parse_args()
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_READER_SCREEN_CHECK='true')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='screen-chip-player-',dir=base))
    with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    saved=output/'server';fixture_world(saved,port)
    if args.restart_from:
        source=args.restart_from.resolve()
        check_log(source/'logs/latest.log','All dimensions are saved')
        shutil.copytree(source/'world',saved/'world')
        (output/'restart-source.txt').write_text(str(source)+'\n')
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=screen-chip-player-read'),output/'read.log','[vandorlabs][screen-chip-player] read PASS',env)
        print('Native Screen chip saved-world replay PASS: '+str(output),flush=True)
        return
    server_log=output/'server.log';phase='screen-chip-player-client'
    server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),server_log,env)
    try:
        deadline=time.monotonic()+120
        while 'Done (' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Chip fixture startup failed: '+str(server_log))
            time.sleep(.5)
        client=output/'client';client.mkdir()
        (client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nonboardAccessibility:false\n')
        call=command('runClient','-PtransmitterPhase='+phase,'-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
        client_env=env
        if args.xvfb:call=['xvfb-run','-a','-s','-screen 0 1280x720x24']+call;client_env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
        run(call,output/'client.log','[vandorlabs][screen-chip-player] client PASS',client_env)
        deadline=time.monotonic()+30
        while '[vandorlabs][screen-chip-player] logout PASS' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Chip fixture logout missing: '+str(server_log))
            time.sleep(.1)
        for marker in ['setup','remove','insert','complete','logout']:check_log(server_log,'[vandorlabs][screen-chip-player] '+marker+' PASS')
    finally:stop(server)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
    check_log(saved/'logs/latest.log','All dimensions are saved')
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=screen-chip-player-read'),output/'read.log','[vandorlabs][screen-chip-player] read PASS',env)
    print('Native actual Screen chip player and restart focus PASS: '+str(output),flush=True)

if __name__=='__main__':main()
