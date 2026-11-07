#!/usr/bin/env python3
"""Private placed Screen renderer with normal artwork, row glyphs, resource reload and real Survival use."""
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
    parser.add_argument('--canonical',action='store_true')
    parser.add_argument('--chips',action='store_true',help='Add embedded trays and a joined reader-backed journal')
    args=parser.parse_args()
    canonical_args=(['-PscreenCanonical=true'] if args.canonical else [])+['-PscreenRenderWorldCheck']+(['-PscreenChipRenderWorldCheck'] if args.chips else [])
    phase='screen-render-world-client'
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),**({'VANDOR_READER_SCREEN_CHECK':'true'} if args.chips else {}))
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='screen-render-world-private-',dir=base))
    with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    saved=output/'server';fixture_world(saved,port);server_log=output/'server.log'
    server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase,*canonical_args),server_log,env)
    try:
        deadline=time.monotonic()+120
        while 'Done (' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Screen renderer fixture startup failed: '+str(server_log))
            time.sleep(.5)
        client=output/'client';client.mkdir()
        (client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
        client_command=command('runClient','-PtransmitterPhase='+phase,'-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client),*canonical_args)
        if args.xvfb:client_command=['xvfb-run','-a','-s','-screen 0 1280x720x24']+client_command;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
        run(client_command,output/'client.log','[vandorlabs][screen-render-world] client PASS',env)
        deadline=time.monotonic()+30
        while '[vandorlabs][screen-render-world] logout PASS' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Authoritative logout missing: '+str(server_log))
            time.sleep(.1)
        for marker in ['setup','toggle','light-off','complete','logout']:check_log(server_log,'[vandorlabs][screen-render-world] '+marker+' PASS')
    finally:stop(server)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
    check_log(saved/'logs/latest.log','All dimensions are saved')
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase,*canonical_args,'-PscreenRenderWorldRead'),output/'read.log','[vandorlabs][screen-render-world] read PASS',env)
    from PIL import Image,ImageChops
    positive=Image.open(client/'screenshots/screen-world-positive.png').convert('RGB');negative=Image.open(client/'screenshots/screen-world-disabled.png').convert('RGB')
    if positive.size!=negative.size:raise AssertionError('Renderer capture sizes differ')
    diff=ImageChops.difference(positive,negative);mask=diff.point(lambda value:255 if value>12 else 0);pixels=sum(any(channel>0 for channel in pixel) for pixel in mask.getdata())
    if pixels<200:raise AssertionError('Actual private BER leaf capture is empty vs disabled renderer: '+str(pixels)+' pixels')
    diff.save(output/'screen-world-difference.png')
    reloaded=Image.open(client/'screenshots/screen-world-reloaded.png').convert('RGB')
    raw_reload=ImageChops.difference(positive,reloaded)
    exact_reload_pixels=sum(any(channel>0 for channel in pixel) for pixel in raw_reload.getdata())
    maximum_reload_delta=max(max(pixel) for pixel in raw_reload.getdata())
    delta=raw_reload.point(lambda value:255 if value>12 else 0)
    reload_pixels=sum(any(channel>0 for channel in pixel) for pixel in delta.getdata())
    if reload_pixels>100:raise AssertionError('Actual resource reload changed stationary Screen capture: '+str(reload_pixels)+' pixels')
    (output/'renderer-report.txt').write_text('Changed pixels vs disabled renderer: '+str(pixels)+'\nChanged pixels after resource reload above12/255: '+str(reload_pixels)+'\nExact changed pixels after reload: '+str(exact_reload_pixels)+'\nMaximum reload RGB delta: '+str(maximum_reload_delta)+'\n')
    off=Image.open(client/'screenshots/screen-world-toggled.png').convert('RGB')
    off_diff=ImageChops.difference(positive,off).point(lambda value:255 if value>12 else 0)
    off_pixels=sum(any(channel>0 for channel in pixel) for pixel in off_diff.getdata())
    if off_pixels<5:raise AssertionError('Actual Survival row-toggle capture unchanged: '+str(off_pixels))
    with (output/'renderer-report.txt').open('a') as report:report.write('Changed pixels after actual Survival row toggle: '+str(off_pixels)+'\n')
    print('Actual row-toggle mask PASS pixels='+str(off_pixels),flush=True)
    print(('Canonical' if args.canonical else 'Private')+' Screen renderer mask PASS pixels='+str(pixels),flush=True)
    print('Native '+('canonical' if args.canonical else 'private')+' Screen world client focused checks PASS (development classpath/software rendering): '+str(output),flush=True)


if __name__=='__main__':main()
