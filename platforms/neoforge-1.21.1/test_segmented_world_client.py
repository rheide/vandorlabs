#!/usr/bin/env python3
"""Public Segmented Door real client placement/editor/moved uses/lag/culling/mining acceptance."""
import argparse,os,socket,time
from pathlib import Path
from test_cube import ROOT,command,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');parser.add_argument('--supplied',action='store_true');options=parser.parse_args()
 if options.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)));
 if options.supplied:env['VANDOR_SEGMENTED_SUPPLIED_WORLD']='true'
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='segmented-world-client-',dir=base))
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=segmented-world-client'),server_log,env)
 try:
  deadline=time.monotonic()+120
  while 'Done (' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Canonical Segmented startup failed: '+str(server_log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  args=command('runClient','-PtransmitterPhase=segmented-world-client','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
  if options.xvfb:args=['xvfb-run','-a','-s','-screen 0 1280x720x24']+args;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  process=launch(args,out/'client.log',env)
  try:
   code=process.wait(timeout=360)
   if code:raise RuntimeError('Canonical Segmented client exit '+str(code)+': '+str(out/'client.log'))
   check_log(out/'client.log','[vandorlabs][segmented-world-client] client PASS')
  finally:stop(process)
  deadline=time.monotonic()+30
  while '[vandorlabs][segmented-world-client] logout PASS' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Canonical Segmented authoritative logout missing: '+str(server_log))
   time.sleep(.1)
  markers=['placement','supplied-0','supplied-1','supplied-2','supplied-motion','supplied-complete','logout'] if options.supplied else ['placement','frame-subgroup','configuration','eight-mechanisms','survival-sneak','stale-identity','nearer-refusal','lag-empty','stale-sequence','moved-bucket','closed-after-bucket','configured-pick','harvest','logout']
  for marker in markers:check_log(server_log,'[vandorlabs][segmented-world-client] '+marker+' PASS')
 finally:stop(server)
 deadline=time.monotonic()+30
 while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 if options.supplied:
  from PIL import Image,ImageChops
  reports=[]
  for style in range(3):
   positive=Image.open(client/f'screenshots/supplied-segmented-{style}-positive.png').convert('RGB');negative=Image.open(client/f'screenshots/supplied-segmented-{style}-negative.png').convert('RGB')
   w,h=positive.size;region=(w//2-230,h//2-250,w//2+230,h//2+250);diff=ImageChops.difference(positive.crop(region),negative.crop(region));pixels=sum(max(pixel)>12 for pixel in diff.getdata())
   if pixels<500:raise AssertionError('Supplied gate runtime BER missing against disabled renderer: '+str(style)+' pixels='+str(pixels))
   diff.save(out/f'supplied-{style}-difference.png');reports.append('Style '+str(style)+': '+str(pixels)+' changed pixels against disabled BER')
  (out/'supplied-render-report.txt').write_text('\n'.join(reports)+'\n')
  print('Public canonical supplied Segmented world acceptance PASS: '+str(out),flush=True);return
 from PIL import Image,ImageChops
 positive=Image.open(client/'screenshots/canonical-segmented-cull-positive.png').convert('RGB');negative=Image.open(client/'screenshots/canonical-segmented-cull-negative.png').convert('RGB')
 if positive.size!=negative.size:raise AssertionError('Culling capture dimensions differ')
 diff=ImageChops.difference(positive,negative);mask=diff.point(lambda x:255 if x>12 else 0);pixels=sum(any(c>0 for c in pixel) for pixel in mask.getdata())
 if pixels<200:raise AssertionError('Public canonical lower-body BER capture empty vs disabled renderer: '+str(pixels))
 diff.save(out/'canonical-segmented-cull-difference.png');(out/'culling-report.txt').write_text('Changed pixels vs disabled renderer: '+str(pixels)+'\n')
 print('Public canonical Segmented real client acceptance PASS pixels='+str(pixels)+' (development classpath/software rendering): '+str(out),flush=True)
if __name__=='__main__':main()
