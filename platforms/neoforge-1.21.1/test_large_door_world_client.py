#!/usr/bin/env python3
"""Focused actual canonical Large Door player placement, renderer/culling, reader dialog and harvest."""
import argparse
import os
from pathlib import Path
import socket
import tempfile
import time
from test_cube import ROOT, command, run, launch, stop, check_log, jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory, retain_artifacts


@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--xvfb',action='store_true')
    args=parser.parse_args()
    phase='large-door-world-client'
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_DOOR_RESOURCE_CHECK="true")
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='large-door-world-client-',dir=base))
    with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    saved=output/'server';fixture_world(saved,port);server_log=output/'server.log'
    server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),server_log,env)
    try:
        deadline=time.monotonic()+120
        while 'Done (' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Door target fixture startup failed: '+str(server_log))
            time.sleep(.5)
        client=output/'client';client.mkdir()
        (client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
        client_command=command('runClient','-PtransmitterPhase='+phase,'-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
        if args.xvfb:client_command=['xvfb-run','-a','-s','-screen 0 1280x720x24']+client_command;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
        run(client_command,output/'client.log','[vandorlabs][large-door-world-client] client PASS',env)
        deadline=time.monotonic()+30
        while '[vandorlabs][large-door-world-client] logout PASS' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Authoritative logout missing: '+str(server_log))
            time.sleep(.1)
        for marker in ['placement','configuration','reader-insert','reader-eject','motion','configured-pick','harvest','logout']:check_log(server_log,'[vandorlabs][large-door-world-client] '+marker+' PASS')
    finally:stop(server)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
    check_log(saved/'logs/latest.log','All dimensions are saved')
    from PIL import Image,ImageChops
    positive=Image.open(client/'screenshots/canonical-large-upper-positive.png').convert('RGB');negative=Image.open(client/'screenshots/canonical-large-upper-negative.png').convert('RGB')
    if positive.size!=negative.size:raise AssertionError('Culling capture sizes differ')
    diff=ImageChops.difference(positive,negative);mask=diff.point(lambda value:255 if value>12 else 0);pixels=sum(any(channel>0 for channel in pixel) for pixel in mask.getdata())
    if pixels<200:raise AssertionError('Actual canonical BER upper-body capture is empty vs disabled renderer: '+str(pixels)+' pixels')
    diff.save(output/'canonical-large-upper-difference.png');(output/'culling-report.txt').write_text('Changed pixels vs disabled renderer: '+str(pixels)+'\n')
    print('Canonical Large upper-only renderer mask PASS pixels='+str(pixels),flush=True)
    print('Native canonical Large Door world client focused checks PASS (development classpath/software rendering): '+str(output),flush=True)


if __name__=='__main__':main()
