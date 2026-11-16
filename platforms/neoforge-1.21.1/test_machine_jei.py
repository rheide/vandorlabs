#!/usr/bin/env python3
"""Installed JEI categories, catalysts, recipe browser and actual Survival Infuser transfer."""
import argparse,os,socket,time,hashlib
from pathlib import Path
from test_cube import ROOT,command,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');parser.add_argument('--with-jei',action='store_true',help='Enable optional installed JEI runtime (required for this focus)');options=parser.parse_args()
 if not options.with_jei:parser.error('--with-jei is required for actual browser validation')
 if options.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 out=Path(artifact_directory(prefix='machine-jei-',dir=ROOT/'build/test-artifacts'));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.machineJeiCheck=true')
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=machine-jei','-PwithJei'),server_log,env)
 try:
  deadline=time.monotonic()+120
  while 'Done (' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Infuser server startup: '+str(server_log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  args=command('runClient','-PtransmitterPhase=machine-jei','-PwithJei','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
  if options.xvfb:args=['xvfb-run','-a','-s','-screen 0 1280x720x24']+args;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  process=launch(args,out/'client.log',env)
  try:
   if process.wait(timeout=240):raise RuntimeError('Infuser client nonzero exit: '+str(out/'client.log'))
   check_log(out/'client.log','[vandorlabs][machine-jei] client PASS')
   check_log(out/'client.log','[vandorlabs][machine-jei] transfer PASS')
  finally:stop(process)
  deadline=time.monotonic()+30
  while '[vandorlabs][machine-jei] logout PASS' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Infuser logout missing: '+str(server_log))
   time.sleep(.1)
  for marker in ('setup','logout'):check_log(server_log,'[vandorlabs][machine-jei] '+marker+' PASS')
 finally:stop(server)
 deadline=time.monotonic()+30
 while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 lines=[]
 for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge/compat/jei').rglob('*.class')):lines.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(ROOT)))
 (out/'classes.sha256').write_text('\n'.join(lines)+'\n')
 print('Actual native installed JEI PASS '+str(out),flush=True)
if __name__=='__main__':main()
