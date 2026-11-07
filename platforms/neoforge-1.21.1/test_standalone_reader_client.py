#!/usr/bin/env python3
"""Actual private standalone reader placement/use/tool/menu and mounted contents client."""
import argparse,os,socket,time,json
from PIL import Image
from pathlib import Path
from test_cube import ROOT,command,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');parser.add_argument('--canonical',action='store_true');options=parser.parse_args()
 if options.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 phase='canonical-reader-client' if options.canonical else 'standalone-reader-client'
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)));base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix=phase+'-',dir=base))
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),server_log,env)
 try:
  deadline=time.monotonic()+120
  while 'Done (' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Reader world server startup: '+str(server_log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  args=command('runClient','-PtransmitterPhase='+phase,'-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
  if options.xvfb:args=['xvfb-run','-a','-s','-screen 0 1280x720x24']+args;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  process=launch(args,out/'client.log',env)
  try:
   code=process.wait(timeout=240)
   if code:raise RuntimeError('Reader world client exit '+str(code)+': '+str(out/'client.log'))
   check_log(out/'client.log','[vandorlabs][reader-world] client PASS')

  finally:stop(process)
  deadline=time.monotonic()+30
  while '[vandorlabs][reader-world] logout PASS' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Reader world logout missing: '+str(server_log))
   time.sleep(.1)
  for marker in ['setup','logout']:check_log(server_log,'[vandorlabs][reader-world] '+marker+' PASS')
  check_log(server_log,'[vandorlabs][reader-world] server PASS')
  check_log(out/'client.log','[vandorlabs][reader-world] warm PASS')
  for name in (['reader-card-mounted-world.png','reader-chip-mounted-world.png'] if options.canonical else ['reader-card-mounted-world.png','reader-chip-mounted-world.png','reader-card-mounted-world-no-contents.png','reader-chip-mounted-world-no-contents.png']):
   if not (client/'screenshots'/name).is_file():raise AssertionError('Missing mounted reader world '+name)
  pixels={}
  if options.canonical:
   for kind in ['card','chip']:
    positive=Image.open(client/'screenshots'/('reader-'+kind+'-mounted-world.png')).convert('RGB')
    values=[positive.getpixel((x,y)) for y in range(307,415) for x in range(608,676)]
    dark=sum(max(value)<45 for value in values);colored=sum(max(value)-min(value)>30 for value in values)
    if positive.size!=(1280,720) or dark<100 or colored<100:raise AssertionError('Missing canonical mounted frame/artwork: '+str((kind,dark,colored)))
    pixels[kind]=dict(darkFramePixels=dark,coloredArtworkPixels=colored)
  for kind in ([] if options.canonical else ['card','chip']):
   positive=Image.open(client/'screenshots'/('reader-'+kind+'-mounted-world.png')).convert('RGB');negative=Image.open(client/'screenshots'/('reader-'+kind+'-mounted-world-no-contents.png')).convert('RGB')
   if positive.size!=(1280,720) or negative.size!=positive.size:raise AssertionError('Unexpected world capture dimensions')
   changed=sum(max(abs(a-b) for a,b in zip(positive.getpixel((x,y)),negative.getpixel((x,y))))>=6 for y in range(307,415) for x in range(608,676))
   dark=sum(max(negative.getpixel((x,y)))<45 for y in range(307,415) for x in range(608,676))
   if changed<20 or dark<40:raise AssertionError('Missing contents positive/terrain-frame negative: '+str((kind,changed,dark)))
   pixels[kind]=dict(changedContentsPixels=changed,retainedDarkFramePixels=dark)
  (out/'world-pixels.json').write_text(json.dumps(pixels,sort_keys=True)+'\n')
 finally:stop(server)
 deadline=time.monotonic()+30
 while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 print(('Actual canonical reader production player/binding path PASS: ' if options.canonical else 'Actual private standalone reader player path PASS: ')+str(out),flush=True)
if __name__=='__main__':main()
