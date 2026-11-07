#!/usr/bin/env python3
"""Actual private placed Canopy chunk/BER/culling and resource reload acceptance."""
import argparse,os,socket,time,subprocess
from pathlib import Path
from test_cube import ROOT,command,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
def await_exit():
 deadline=time.monotonic()+60
 marker='-Dvandorlabs.transmitterPhase=canopy-world-client'
 while True:
  running=subprocess.check_output([str(jdk_home(21)/'bin'/('jps.exe' if os.name=='nt' else 'jps')),'-lv'],text=True)
  if marker not in running:return
  if time.monotonic()>=deadline:raise RuntimeError('Canopy world JVM did not exit: '+running)
  time.sleep(.25)
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
 if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 platform=Path(__file__).resolve().parent
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.canopyWorldCheck=true')
 subprocess.run(command('compileSmokeJava'),cwd=platform,env=env,check=True)
 out=Path(artifact_directory(prefix='canopy-world-',dir=ROOT/'build/test-artifacts'))
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=canopy-world-client'),server_log,env)
 try:
  deadline=time.monotonic()+120
  while 'Done (' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Canopy world server startup: '+str(server_log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  launch_args=command('runClient','-PtransmitterPhase=canopy-world-client','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
  if args.xvfb:launch_args=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch_args;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  process=launch(launch_args,out/'client.log',env)
  try:
   result=process.wait(timeout=240)
   if result:raise RuntimeError('Canopy world client exit '+str(result)+': '+str(out/'client.log'))
   check_log(out/'client.log','[vandorlabs][canopy-world] client PASS')
  finally:stop(process)
  deadline=time.monotonic()+30
  while '[vandorlabs][canopy-world] server PASS' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Canopy world logout missing: '+str(server_log))
   time.sleep(.1)
  for marker in ('gallery','server'):check_log(server_log,'[vandorlabs][canopy-world] '+marker+' PASS')
  for name in ['canopy-world-cull-positive.png','canopy-world-regular-positive.png']:
   if not (client/'screenshots'/name).is_file():raise AssertionError('Missing actual Canopy world capture '+name)
  from PIL import Image,ImageChops
  for kind in ['cull','regular']:
   a=Image.open(client/'screenshots'/('canopy-world-'+kind+'-positive.png')).convert('RGB');b=Image.open(client/'screenshots'/('canopy-world-'+kind+'-negative.png')).convert('RGB');changed=sum(max(pixel)>8 for pixel in ImageChops.difference(a,b).getdata());assert changed>200,(kind,changed);print('Canopy world negative pixels '+kind+'='+str(changed),flush=True)
 finally:
  stop(server)
  await_exit()
 deadline=time.monotonic()+30
 while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Actual private placed Canopy PASS: '+str(out),flush=True)
if __name__=='__main__':main()
