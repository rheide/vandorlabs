#!/usr/bin/env python3
"""Private actual Light menu/widget/C2S/S2C authorization roundtrip."""
import argparse,os,socket,time
from pathlib import Path
from test_cube import ROOT,command,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');options=parser.parse_args()
 if options.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)));base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='light-editor-client-',dir=base))
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=light-editor-client'),server_log,env)
 try:
  deadline=time.monotonic()+120
  while 'Done (' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Light editor server startup: '+str(server_log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  args=command('runClient','-PtransmitterPhase=light-editor-client','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
  if options.xvfb:args=['xvfb-run','-a','-s','-screen 0 1280x720x24']+args;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  process=launch(args,out/'client.log',env)
  try:
   code=process.wait(timeout=240)
   if code:raise RuntimeError('Light editor client exit '+str(code)+': '+str(out/'client.log'))
   check_log(out/'client.log','[vandorlabs][light-editor-client] client PASS')
  finally:stop(process)
  deadline=time.monotonic()+30
  while '[vandorlabs][light-editor-client] logout PASS' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Light editor logout missing: '+str(server_log))
   time.sleep(.1)
  for marker in ['setup','roundtrip','current-tool','closed-window','logout']:check_log(server_log,'[vandorlabs][light-editor-client] '+marker+' PASS')
 finally:stop(server)
 deadline=time.monotonic()+30
 while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Actual client Light editor PASS (private fixture; private Light IDs; renderer acceptance separate): '+str(out),flush=True)
if __name__=='__main__':main()
