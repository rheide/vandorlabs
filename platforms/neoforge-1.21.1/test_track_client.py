#!/usr/bin/env python3
"""Native Track ordinary placement, style dialog, joined arm motion and mining."""
import argparse,os,socket,time,hashlib
from pathlib import Path
from test_cube import ROOT,command,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');options=parser.parse_args()
 if options.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.trackPlayer=true -Dvandorlabs.trackRenderChecks=true');base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='track-player-',dir=base))
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 extra=[];saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),*extra),server_log,env)
 try:
  deadline=time.monotonic()+120
  while 'Done (' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Native Track server startup: '+str(server_log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  args=command('runClient','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client),*extra)
  if options.xvfb:args=['xvfb-run','-a','-s','-screen 0 1280x720x24']+args;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  process=launch(args,out/'client.log',env)
  try:
   code=process.wait(timeout=240)
   if code:raise RuntimeError('Native Track client exit '+str(code)+': '+str(out/'client.log'))
   check_log(out/'client.log','[vandorlabs][track-player] client PASS')
   check_log(out/'client.log','[vandorlabs][track-render] PASS')
  finally:stop(process)
  deadline=time.monotonic()+30
  while '[vandorlabs][track-player] logout PASS' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Native Track logout missing: '+str(server_log))
   time.sleep(.1)
  for marker in ['setup','completed','logout']:check_log(server_log,'[vandorlabs][track-player] '+marker+' PASS')
 finally:stop(server)
 deadline=time.monotonic()+30
 while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 rows=[]
 base=ROOT/'platforms/neoforge-1.21.1'
 for folder in ('build/classes/java/main','build/resources/main'):
  for path in sorted((base/folder).rglob('*')):
   if path.is_file():rows.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(base)))
 manifest='\n'.join(rows)+'\n';(out/'main-runtime.sha256').write_text(manifest)
 (out/'main-runtime-fingerprint.txt').write_text(hashlib.sha256(manifest.encode()).hexdigest()+'\n')
 print('Actual native Track and side-wheel player PASS: '+str(out),flush=True)
if __name__=='__main__':main()
