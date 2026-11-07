#!/usr/bin/env python3
"""Private placed engine chunk renderer, natural client particles and real dedicated editor/use packets."""
import argparse,os,socket,time,re
from pathlib import Path
from test_cube import ROOT,command,run,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
from test_artifacts import _processes

def await_shutdown(saved,pid):
 deadline=time.monotonic()+30
 while time.monotonic()<deadline:
  text=(saved/'logs/latest.log').read_text(errors='replace') if (saved/'logs/latest.log').is_file() else ''
  table=_processes()
  exited=table is not None and (pid not in table or table[pid][3]=='Z')
  if exited and 'All dimensions are saved' in text:return
  time.sleep(.1)
 raise RuntimeError('Actual server process/save completion not established PID='+str(pid))
def inventory_pixels(path):
 from PIL import Image
 image=Image.open(path).convert('RGB');scale=image.width/640
 if scale!=int(scale):raise AssertionError('Unexpected GUI capture scale')
 scale=int(scale);total=0
 for index in range(21):
  x=(30+index%7*70)*scale;y=(50+index//7*60)*scale
  ink=sum(image.getpixel((x+dx,y+dy))!=(0,0,0) for dx in range(16*scale) for dy in range(16*scale))
  if ink<8:raise AssertionError('Actual ItemRenderer slot empty '+str(index))
  for dx in range(-scale,17*scale):
   for dy in [-scale,17*scale-1]:
    if image.getpixel((x+dx,y+dy))!=(0,0,0):raise AssertionError('Inventory horizontal padding '+str(index))
  for dy in range(-scale,17*scale):
   for dx in [-scale,17*scale-1]:
    if image.getpixel((x+dx,y+dy))!=(0,0,0):raise AssertionError('Inventory vertical padding '+str(index))
  total+=ink
 return total
@retain_artifacts
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');parser.add_argument('--canonical',action='store_true');args=parser.parse_args()
 if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_PROPULSION_WORLD_CHECK='true',VANDOR_PROPULSION_CANONICAL_CHECK=str(args.canonical).lower());base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='propulsion-world-private-',dir=base))
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 server_pid=None;saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';phase='propulsion-world-client';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),server_log,env)
 try:
  deadline=time.monotonic()+120
  while True:
   text=server_log.read_text(errors='replace');pid=re.search(r'actual server PID=(\d+)',text)
   if 'Done (' in text and pid and 'settled gallery READY' in text:server_pid=int(pid.group(1));break
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Private engine server startup failed: '+str(server_log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:3\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  task=command('runClient','-PtransmitterPhase='+phase,'-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
  if args.xvfb:task=['xvfb-run','-a','-s','-screen 0 1280x720x24']+task;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  run(task,out/'client.log','[vandorlabs][propulsion-world] client PASS',env)
  deadline=time.monotonic()+30
  while '[vandorlabs][propulsion-world] logout PASS' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Authoritative engine logout missing: '+str(server_log))
   time.sleep(.1)
  for marker in ['setup','stream','editor','complete','logout']:check_log(server_log,'[vandorlabs][propulsion-world] '+marker+' PASS')
 finally:
  stop(server)
  if server_pid is not None:await_shutdown(saved,server_pid)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 from PIL import Image,ImageChops
 def image(name):return Image.open(client/'screenshots'/name).convert('RGB')
 def changed(a,b,threshold):
  if a.size!=b.size:raise AssertionError('Capture dimensions differ')
  diff=ImageChops.difference(a,b);return sum(max(pixel)>threshold for pixel in diff.getdata()),diff
 body,body_diff=changed(image('propulsion-world-positive.png'),image('propulsion-world-disabled.png'),12)
 if body<1000:raise AssertionError('Actual terrain body negative empty: '+str(body))
 reload,_=changed(image('propulsion-world-positive.png'),image('propulsion-world-reloaded.png'),12)
 if reload>200:raise AssertionError('Stable no-particle authored bodies changed after reload: '+str(reload))
 particles,particle_diff=changed(image('propulsion-world-stream.png'),image('propulsion-world-no-particles.png'),8)
 if particles<30:raise AssertionError('Actual native engine particle queue negative empty: '+str(particles))
 body_diff.save(out/'body-negative.png');particle_diff.save(out/'particle-negative.png');(out/'renderer-report.txt').write_text('Terrain body negative pixels='+str(body)+'\nStable reload changed pixels='+str(reload)+'\nActual particle-engine negative pixels='+str(particles)+'\n')
 if args.canonical:print('Canonical inventory software PASS slots=21 paddedPixels='+str(inventory_pixels(client/'screenshots/propulsion-world-inventory.png')),flush=True)
 print(('Canonical' if args.canonical else 'Private')+' propulsion world software PASS body='+str(body)+' particles='+str(particles)+' reload='+str(reload)+' '+str(out),flush=True)
if __name__=='__main__':main()
