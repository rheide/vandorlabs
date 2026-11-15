#!/usr/bin/env python3
"""One connected Ramp controller path with real GUI, C2S movement, async fence and native return."""
import argparse,os,re,socket,time
from pathlib import Path
from test_cube import ROOT,command,run,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from test_propulsion_world import await_shutdown
from test_ramp_part_world import await_client
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    out=Path(artifact_directory(prefix='ramp-controller-player-',dir=ROOT/'build/test-artifacts'))
    with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';client_log=out/'client.log';server_pid=None
    server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-controller-player'),server_log,env)
    try:
        deadline=time.monotonic()+180
        while True:
            text=server_log.read_text(errors='replace');match=re.search(r'actual server PID=(\d+)',text)
            if match:server_pid=int(match.group(1))
            if '[vandorlabs][ramp-controller-player] FAIL' in text or server.poll() is not None:raise RuntimeError('Controller server failed: '+str(server_log))
            if 'Done (' in text and server_pid:break
            if time.monotonic()>deadline:raise RuntimeError('Controller server startup timed out: '+str(server_log))
            time.sleep(.25)
        client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\npauseOnLostFocus:false\n')
        cmd=command('runClient','-PtransmitterPhase=ramp-controller-player','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
        if args.xvfb:cmd=['xvfb-run','-a','-s','-screen 0 1280x720x24']+cmd;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
        run(cmd,client_log,'[vandorlabs][ramp-controller-player] client PASS',env)
        deadline=time.monotonic()+30
        while '[vandorlabs][ramp-controller-player] logout PASS' not in server_log.read_text(errors='replace'):
            text=server_log.read_text(errors='replace')
            if '[vandorlabs][ramp-controller-player] FAIL' in text or server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Authoritative controller completion/logout missing: '+str(server_log))
            time.sleep(.1)
        for marker in ['preparing-free PASS','commit-fence PASS','server PASS']:check_log(server_log,'[vandorlabs][ramp-controller-player] '+marker)
        if not (client/'screenshots/ramp-controller-player-native-return.png').is_file():raise RuntimeError('Missing actual native-return capture')
    finally:
        stop(server)
        if server_pid:await_shutdown(saved,server_pid)
        await_client(client_log)
    print('Native connected Ramp controller PASS: '+str(out),flush=True)

if __name__=='__main__':main()
