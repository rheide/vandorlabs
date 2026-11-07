#!/usr/bin/env python3
"""Private placed Ramp Part renderer/packet/reload/cost acceptance; no controller activation."""
import argparse
import os
import re
from pathlib import Path
import socket
import time
from test_cube import ROOT,command,run,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from test_propulsion_world import await_shutdown
from native_test_artifacts import artifact_directory,retain_artifacts
from test_artifacts import _processes


def await_client(log):
    match=re.search(r'actual client PID=(\d+)',log.read_text(errors='replace')) if log.exists() else None
    if not match:return
    pid=int(match.group(1));deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        table=_processes()
        if table is not None and (pid not in table or table[pid][3]=='Z'):return
        time.sleep(.1)
    raise RuntimeError('Actual client JVM did not exit PID='+str(pid))


@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    out=Path(artifact_directory(prefix='ramp-part-world-',dir=base));phase='ramp-part-world-client'
    with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';client_log=out/'client.log';server_pid=None
    server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),server_log,env)
    try:
        deadline=time.monotonic()+120
        while True:
            text=server_log.read_text(errors='replace');match=re.search(r'actual server PID=(\d+)',text)
            if match:server_pid=int(match.group(1))
            if 'Done (' in text and server_pid:break
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Ramp Part private fixture startup failed: '+str(server_log))
            time.sleep(.5)
        client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
        cmd=command('runClient','-PtransmitterPhase='+phase,'-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
        if args.xvfb:cmd=['xvfb-run','-a','-s','-screen 0 1280x720x24']+cmd;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
        run(cmd,client_log,'[vandorlabs][ramp-part-world] client PASS',env)
        for marker in ['[vandorlabs][ramp-part-packets] PASS','stationary PASS','moving PASS belt=false','moving PASS belt=true','outline PASS']:check_log(client_log,marker)
        deadline=time.monotonic()+30
        while '[vandorlabs][ramp-part-world] logout PASS' not in server_log.read_text(errors='replace'):
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Ramp Part logout missing')
            time.sleep(.1)
        check_log(server_log,'[vandorlabs][ramp-part-world] setup PASS')
        check_log(server_log,'[vandorlabs][ramp-part-world] outline camera PASS')
    finally:
        stop(server)
        if server_pid:await_shutdown(saved,server_pid)
        await_client(client_log)
    from PIL import Image,ImageChops
    positive=Image.open(client/'screenshots/ramp-part-world-positive.png').convert('RGB')
    def changed(name):
        image=Image.open(client/'screenshots'/name).convert('RGB')
        if image.size!=positive.size:raise AssertionError('Capture dimensions differ')
        diff=ImageChops.difference(positive,image);pixels=sum(any(c>12 for c in value) for value in diff.getdata())
        return diff,pixels
    negative,pixels=changed('ramp-part-world-disabled.png')
    if pixels<500:raise AssertionError('Actual BER disabled control empty: '+str(pixels))
    negative.save(out/'ramp-part-world-difference.png')
    reload_delta,reload_pixels=changed('ramp-part-world-reloaded.png')
    if reload_pixels>100:raise AssertionError('Stationary reload changed actual gallery '+str(reload_pixels))
    restored_delta,restored_pixels=changed('ramp-part-world-restored.png')
    if restored_pixels>100:raise AssertionError('Restored endpoints changed actual gallery '+str(restored_pixels))
    (out/'renderer-report.txt').write_text('Disabled BER changed pixels: '+str(pixels)+'\nReload changed pixels: '+str(reload_pixels)+'\nRestored endpoints changed pixels: '+str(restored_pixels)+'\n')
    print('Native private placed Ramp Part renderer PASS '+str(out),flush=True)


if __name__=='__main__':main()
