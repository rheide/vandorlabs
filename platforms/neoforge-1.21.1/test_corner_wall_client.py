#!/usr/bin/env python3
"""Corner Walls: item-path placement, joins and dialog on the server; client joins, bent model and views."""
import argparse,os,socket,time,re,hashlib
from pathlib import Path
from test_cube import ROOT,command,run,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from test_propulsion_world import await_shutdown
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
 if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)));base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='corner-wall-client-',dir=base))
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 saved=out/'server';fixture_world(saved,port);log=out/'server.log';pid=None;phase='corner-wall-client';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),log,env)
 try:
  deadline=time.monotonic()+180
  while True:
   text=log.read_text(errors='replace');found=re.search(r'actual server PID=(\d+)',text)
   if 'Done (' in text and found and '[vandorlabs][corner-wall] setup PASS' in text:pid=int(found.group(1));break
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Corner wall startup failed: '+str(log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:3\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  task=command('runClient','-PtransmitterPhase='+phase,'-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
  if args.xvfb:task=['xvfb-run','-a','-s','-screen 0 1280x720x24']+task;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  run(task,out/'client.log','[vandorlabs][corner-wall] client PASS',env)
 finally:
  stop(server)
  if pid is not None:await_shutdown(saved,pid)
 check_log(saved/'logs/latest.log','All dimensions are saved');check_log(log,'[vandorlabs][corner-wall] server PASS');check_log(log,'[vandorlabs][corner-wall] logout PASS')
 print('Corner Wall client PASS '+str(out.relative_to(ROOT)),flush=True)
if __name__=='__main__':main()
