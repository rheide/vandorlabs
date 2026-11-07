#!/usr/bin/env python3
"""Actual canonical flat/diagonal Glass and Porthole preparation/emission."""
import argparse,os,socket,time,re,hashlib
from pathlib import Path
from test_cube import ROOT,command,run,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from test_propulsion_world import await_shutdown
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');parser.add_argument('--shared',action='store_true');args=parser.parse_args()
 if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_DENSE_WALL_SHARED_CHECK=str(args.shared).lower());base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='dense-wall-client-',dir=base))
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 saved=out/'server';fixture_world(saved,port);log=out/'server.log';pid=None;phase='dense-wall-client';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),log,env)
 try:
  deadline=time.monotonic()+180
  while True:
   text=log.read_text(errors='replace');found=re.search(r'actual server PID=(\d+)',text)
   if 'Done (' in text and found and '[vandorlabs][dense-wall] setup PASS' in text:pid=int(found.group(1));break
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Canonical density startup failed: '+str(log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:3\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  task=command('runClient','-PtransmitterPhase='+phase,'-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
  if args.xvfb:task=['xvfb-run','-a','-s','-screen 0 1280x720x24']+task;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  run(task,out/'client.log','[vandorlabs][dense-wall] client PASS',env)
 finally:
  stop(server)
  if pid is not None:await_shutdown(saved,pid)
 check_log(saved/'logs/latest.log','All dimensions are saved');check_log(log,'[vandorlabs][dense-wall] logout PASS')
 classes=ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge'
 files=sorted({path for prefix in ['NativeFlatWallModel','NativePanelPlaybackRenderer','NativeFlatGlassRenderer'] for path in classes.glob(prefix+'*.class')})
 (out/'artifact.sha256').write_text('\n'.join(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name for path in files)+'\n')
 print('Canonical Glass/Porthole density PASS '+str(out.relative_to(ROOT)),flush=True)
if __name__=='__main__':main()
