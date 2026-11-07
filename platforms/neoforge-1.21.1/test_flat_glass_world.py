#!/usr/bin/env python3
"""Actual private ordinary glass chunk/BER dispatch and independent pass negatives."""
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
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_FLAT_GLASS_WORLD_CHECK='true');base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='flat-glass-world-',dir=base))
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 saved=out/'server';fixture_world(saved,port);log=out/'server.log';pid=None;phase='flat-glass-world-client';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),log,env)
 try:
  deadline=time.monotonic()+180
  while True:
   text=log.read_text(errors='replace');found=re.search(r'actual server PID=(\d+)',text)
   if 'Done (' in text and found and '[vandorlabs][glass-world] setup PASS' in text:pid=int(found.group(1));break
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Private Glass startup failed: '+str(log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:3\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  task=command('runClient','-PtransmitterPhase='+phase,'-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
  if args.xvfb:task=['xvfb-run','-a','-s','-screen 0 1280x720x24']+task;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  run(task,out/'client.log','[vandorlabs][glass-world] client PASS',env)
 finally:
  stop(server)
  if pid is not None:await_shutdown(saved,pid)
 check_log(saved/'logs/latest.log','All dimensions are saved');check_log(log,'[vandorlabs][glass-world] logout PASS')
 from PIL import Image,ImageChops
 def image(name):return Image.open(client/'screenshots'/('glass-world-'+name+'.png')).convert('RGB').crop((420,270,910,440))
 positive=image('positive');counts=[]
 for name in ['no-ber','no-terrain']:
  delta=ImageChops.difference(positive,image(name));pixels=sum(max(pixel)>8 for pixel in delta.getdata())
  if pixels<100:raise AssertionError('Actual Glass pass negative '+name+' changed only '+str(pixels)+' pixels')
  counts.append(pixels)
 delta=ImageChops.difference(positive,image('reload'));changed=sum(max(pixel)>8 for pixel in delta.getdata())
 if changed>positive.width*positive.height//20:raise AssertionError('Reload changed static Glass scene '+str(changed))
 classes=ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge'
 evidence=[]
 for relative in ['NativeFlatGlassModel.class','NativeFlatGlassRenderer.class','client/NativeGlassBlockEntityRenderer.class','glass/NativeGlassEntity.class','glass/NativeGlassService.class','glass/NativeFlatGlassVisual.class']:
  evidence.append(hashlib.sha256((classes/relative).read_bytes()).hexdigest()+'  '+relative)
 (out/'artifact.sha256').write_text('\n'.join(evidence)+'\n')
 print('Private ordinary Glass actual world software PASS negatives='+str(counts)+' reload='+str(changed)+' '+str(out.relative_to(ROOT)),flush=True)
if __name__=='__main__':main()
