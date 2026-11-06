#!/usr/bin/env python3
"""Focused actual Segmented Door terrain upload handoff and body render costs."""
import argparse
import os
from pathlib import Path
import socket
import time
from test_cube import ROOT, command, run, launch, stop, check_log, jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory, retain_artifacts


@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--xvfb',action='store_true')
    args=parser.parse_args()
    phase='segmented-terrain-world'
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_SEGMENTED_TERRAIN_CHECK="true")
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='segmented-terrain-world-',dir=base))
    with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    saved=output/'server';fixture_world(saved,port);server_log=output/'server.log'
    with (saved/'server.properties').open('a') as stream:stream.write('view-distance=8\n')
    server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),server_log,env)
    try:
        deadline=time.monotonic()+120
        while 'Done (' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Segmented terrain fixture startup failed: '+str(server_log))
            time.sleep(.5)
        client=output/'client';client.mkdir()
        (client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:8\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
        client_command=command('runClient','-PtransmitterPhase='+phase,'-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
        if args.xvfb:client_command=['xvfb-run','-a','-s','-screen 0 1280x720x24']+client_command;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
        run(client_command,output/'client.log','[vandorlabs][segmented-terrain-world] client PASS',env)
    finally:stop(server)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
    check_log(saved/'logs/latest.log','All dimensions are saved')
    from PIL import Image,ImageChops
    positive=Image.open(client/'screenshots/terrain-positive.png').convert('RGB')
    without_ber=Image.open(client/'screenshots/terrain-without-ber.png').convert('RGB')
    negative=Image.open(client/'screenshots/terrain-body-disabled.png').convert('RGB')
    def changed(a,b):
        diff=ImageChops.difference(a,b)
        return sum(any(c>12 for c in px) for px in diff.getdata())
    stable=changed(positive,without_ber);body=changed(without_ber,negative)
    if stable>50:raise AssertionError('Settled terrain depends on BER: '+str(stable))
    if body<200:raise AssertionError('Terrain body absent vs disabled body: '+str(body))
    (output/'terrain-report.txt').write_text('Terrain vs disabled BER pixels: '+str(stable)+'\nTerrain body vs disabled body pixels: '+str(body)+'\n')
    print('Segmented terrain GPU masks PASS unchangedBER='+str(stable)+' body='+str(body),flush=True)
    print('Native Segmented terrain world focused checks PASS (development classpath/software rendering): '+str(output),flush=True)


if __name__=='__main__':main()
