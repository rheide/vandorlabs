#!/usr/bin/env python3
"""Private real S2C Ramp support, raw render restoration, mouseover and near-wall camera."""
import argparse
import os
import re
from pathlib import Path
import socket
import time
from test_cube import ROOT,command,run,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from test_propulsion_world import await_shutdown
from test_ramp_part_world import await_client
from native_test_artifacts import artifact_directory,retain_artifacts


@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)));base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    out=Path(artifact_directory(prefix='ramp-rider-client-',dir=base));phase='ramp-rider-client-world'
    with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';client_log=out/'client.log';server_pid=None
    server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),server_log,env)
    try:
        deadline=time.monotonic()+120
        while True:
            text=server_log.read_text(errors='replace');match=re.search(r'actual server PID=(\d+)',text)
            if match:server_pid=int(match.group(1))
            if 'Done (' in text and server_pid:break
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Rider fixture startup failed: '+str(server_log))
            time.sleep(.5)
        client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
        cmd=command('runClient','-PtransmitterPhase='+phase,'-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
        if args.xvfb:cmd=['xvfb-run','-a','-s','-screen 0 1280x720x24']+cmd;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
        run(cmd,client_log,'[vandorlabs][ramp-rider-client] client PASS',env)
        for label in ['first-person PASS','third-person PASS','numeric PASS']:check_log(client_log,'[vandorlabs][ramp-rider-client] '+label)
        deadline=time.monotonic()+30
        while '[vandorlabs][ramp-rider-client] logout PASS' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Rider authoritative completion/logout missing')
            time.sleep(.1)
        for step in range(8):check_log(server_log,'[vandorlabs][ramp-rider-client] action '+str(step)+' PASS')
        check_log(server_log,'[vandorlabs][ramp-rider-client] complete PASS')
    finally:
        stop(server)
        if server_pid:await_shutdown(saved,server_pid)
        await_client(client_log)
    from PIL import Image
    image=Image.open(client/'screenshots/ramp-rider-first-person.png').convert('RGB');x,y=image.width//2,image.height//2
    green=sum(g>r+15 and g>b+15 for r,g,b in image.crop((x-24,y-24,x+24,y+24)).getdata())
    if green<500:raise AssertionError('Actual visible crosshair is not on the expected lower lime row: '+str(green))
    third=Image.open(client/'screenshots/ramp-rider-third-person-wall.png')
    if third.size!=image.size:raise AssertionError('Third-person capture dimensions differ')
    (out/'rider-render-report.txt').write_text('Crosshair-neighbour lime pixels: '+str(green)+'\nThird-person wall camera/reference and raw-physics negative passed in client log.\n')
    print('Native private Ramp rider client PASS '+str(out),flush=True)


if __name__=='__main__':main()
