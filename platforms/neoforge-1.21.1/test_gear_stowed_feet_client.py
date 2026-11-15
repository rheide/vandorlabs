#!/usr/bin/env python3
"""Actual stopped native Ski shift-use append and reversal only."""
import argparse,os,socket,time,hashlib
from pathlib import Path
from test_cube import ROOT,command,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');options=parser.parse_args()
 if options.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.gearStowedFootClient=true');base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='gear-stowed-foot-client-',dir=base))
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 extra=[];saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=gear-client',*extra),server_log,env)
 try:
  deadline=time.monotonic()+120
  while 'Done (' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Native gear server startup: '+str(server_log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  args=command('runClient','-PtransmitterPhase=gear-client','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client),*extra)
  if options.xvfb:args=['xvfb-run','-a','-s','-screen 0 1280x720x24']+args;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  process=launch(args,out/'client.log',env)
  try:
   deadline=time.monotonic()+180
   while process.poll() is None:
    if server.poll() is not None or '[vandorlabs][gear-stowed-foot-client] FAIL' in server_log.read_text(errors='replace'):raise RuntimeError('Native stopped Ski server failed: '+str(server_log))
    if time.monotonic()>deadline:raise RuntimeError('Native stopped Ski client timeout: '+str(out/'client.log'))
    time.sleep(.2)
   code=process.returncode
   if code:raise RuntimeError('Native gear client exit '+str(code)+': '+str(out/'client.log'))
   check_log(out/'client.log','[vandorlabs][gear-stowed-foot-client] client PASS')
  finally:stop(process)
  deadline=time.monotonic()+30
  while '[vandorlabs][gear-stowed-foot-client] logout PASS' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Native gear logout missing: '+str(server_log))
   time.sleep(.1)
  for marker in ['setup','floor-clearance','crouch-aim','append','ordinary','completed','logout']:check_log(server_log,'[vandorlabs][gear-stowed-foot-client] '+marker+' PASS')
 finally:stop(server)
 deadline=time.monotonic()+30
 while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 evidence=[]
 for pattern in ['gear/NativeGear*.class','gear/client/NativeGear*.class']:
  for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge').glob(pattern)):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(ROOT)))
 (out/'classes-and-reference.sha256').write_text('\n'.join(evidence)+'\n')
 print('Actual native stopped Ski client PASS: '+str(out),flush=True)
if __name__=='__main__':main()
