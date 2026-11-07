#!/usr/bin/env python3
"""Actual private Programmer placement/editor/ownership and placed BER; no public activation."""
import argparse,os,socket,time,subprocess,json
from pathlib import Path
from PIL import Image
from test_cube import ROOT,command,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
 if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 platform=Path(__file__).resolve().parent;geometry=ROOT/'src/main/resources/assets/vandorlabs/models/block/data_chip_programmer/ready/parts'
 if not all((geometry/name).is_file() for name in ('static.obj','screen.obj')):raise AssertionError('Missing fixed authored geometry')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_PROGRAMMER_GEOMETRY=str(geometry),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.programmerWorldCheck=true')
 subprocess.run(command('compileSmokeJava'),cwd=platform,env=env,check=True)
 if not (platform/'build/classes/java/smoke/com/vandorlabs/neoforge/programmer/ProgrammerWorldClientChecks.class').is_file():raise AssertionError('Missing compiled Programmer world client fixture')
 out=Path(artifact_directory(prefix='programmer-world-',dir=ROOT/'build/test-artifacts'))
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=programmer-world-client'),server_log,env)
 try:
  deadline=time.monotonic()+120
  while 'Done (' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Programmer server startup: '+str(server_log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  launch_args=command('runClient','-PtransmitterPhase=programmer-world-client','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
  if args.xvfb:launch_args=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch_args;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  process=launch(launch_args,out/'client.log',env)
  try:
   result=process.wait(timeout=240)
   if result:raise RuntimeError('Programmer client exit '+str(result)+': '+str(out/'client.log'))
   check_log(out/'client.log','[vandorlabs][programmer-world] client PASS')
  finally:stop(process)
  deadline=time.monotonic()+30
  while '[vandorlabs][programmer-world] logout PASS' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Programmer logout missing: '+str(server_log))
   time.sleep(.1)
  for marker in ('setup','server','logout'):check_log(server_log,'[vandorlabs][programmer-world] '+marker+' PASS')
  positive=Image.open(client/'screenshots/programmer-placed-positive.png').convert('RGB');negative=Image.open(client/'screenshots/programmer-placed-disabled.png').convert('RGB')
  if positive.size!=(1280,720) or negative.size!=positive.size:raise AssertionError('Unexpected world capture dimensions')
  changed=text=0;colors={(200,238,255),(226,237,245),(166,191,208),(105,121,135)}
  for y in range(150,650):
   for x in range(350,950):
    a,b=positive.getpixel((x,y)),negative.getpixel((x,y));different=max(abs(a[i]-b[i]) for i in range(3))>=10;changed+=different;text+=different and any(max(abs(a[i]-color[i]) for i in range(3))<=5 for color in colors)
  if changed<1000 or text<20:raise AssertionError('Missing actual casing/journal or disabled negative '+str((changed,text)))
  (out/'world-pixels.json').write_text(json.dumps(dict(changedRendererPixels=changed,journalColorPixels=text),sort_keys=True)+'\n')
 finally:stop(server)
 deadline=time.monotonic()+30
 while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Actual private Programmer player/renderer PASS: '+str(out),flush=True)
if __name__=='__main__':main()
