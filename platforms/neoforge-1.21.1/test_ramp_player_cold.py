#!/usr/bin/env python3
"""One real Ramp player cold path in the unchanged canonical warm scene."""
import argparse,os,re,socket,time
from pathlib import Path
from test_cube import ROOT,command,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from test_propulsion_world import await_shutdown
from test_ramp_part_world import await_client
from native_test_artifacts import artifact_directory,retain_artifacts
from test_artifacts import _processes

def await_abrupt(pid):
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        table=_processes()
        if table is not None and (pid not in table or table[pid][3]=='Z'):return
        time.sleep(.1)
    raise RuntimeError('Abrupt server JVM did not exit PID='+str(pid))

@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');parser.add_argument('--case',choices=('healthy','interrupted'),default='healthy');args=parser.parse_args()
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    out=Path(artifact_directory(prefix='ramp-player-cold-',dir=ROOT/'build/test-artifacts'))
    with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    saved=out/'server';fixture_world(saved,port);client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\npauseOnLostFocus:false\n')
    for phase in ('write','read'):
        env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.ramps=true -Dvandorlabs.rampCold='+args.case+'-'+phase)
        server_log=out/(phase+'-server.log');client_log=out/(phase+'-client.log');server_pid=None;client_process=None
        server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-controller-player'),server_log,env)
        try:
            deadline=time.monotonic()+180
            while True:
                text=server_log.read_text(errors='replace');match=re.search(r'actual server PID=(\d+)',text)
                if match:server_pid=int(match.group(1))
                if '[vandorlabs][ramp-controller-player] FAIL' in text or server.poll() is not None:raise RuntimeError('Cold server failed: '+str(server_log))
                if 'Done (' in text and server_pid:break
                if time.monotonic()>deadline:raise RuntimeError('Cold server startup timed out: '+str(server_log))
                time.sleep(.25)
            cmd=command('runClient','-PtransmitterPhase=ramp-controller-player','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
            if args.xvfb:cmd=['xvfb-run','-a','-s','-screen 0 1280x720x24']+cmd;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
            client_process=launch(cmd,client_log,env);deadline=time.monotonic()+180
            marker='[vandorlabs][ramp-player-cold] '+phase+' PASS'
            while marker not in server_log.read_text(errors='replace'):
                st=server_log.read_text(errors='replace');ct=client_log.read_text(errors='replace')
                if '[vandorlabs][ramp-controller-player] FAIL' in st or '[vandorlabs][ramp-controller-player] FAIL' in ct or server.poll() is not None or client_process.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Cold '+phase+' path failed: '+str(server_log)+' / '+str(client_log))
                time.sleep(.25)
            check_log(server_log,marker)
            if phase=='write':
                # Fixture itself terminates the real server without shutdown/player resave.
                server.wait(timeout=30)
                if server.returncode:raise RuntimeError('Abrupt fixture exit was not expected0: '+str(server_log))
                if server_pid:await_abrupt(server_pid)
                print(marker+' (real process halted without normal save)',flush=True)
            else:
                client_process.wait(timeout=60)
                if client_process.returncode:raise RuntimeError('Cold read client failed: '+str(client_log))
                check_log(client_log,'[vandorlabs][ramp-player-cold] client PASS')
                print(marker+' (ordinary post-restart client continuation)',flush=True)
        finally:
            if client_process is not None:stop(client_process)
            stop(server)
            if server_pid:
                if phase=='write':await_abrupt(server_pid)
                else:await_shutdown(saved,server_pid)
            await_client(client_log)
    print('Native real Ramp player cold '+args.case+' PASS: '+str(out),flush=True)

if __name__=='__main__':main()
