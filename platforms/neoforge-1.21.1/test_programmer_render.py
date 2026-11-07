#!/usr/bin/env python3
"""Isolated placed Programmer slots, native generations and actual upper-only engine visibility."""
import argparse,os,socket,time,subprocess,json
from pathlib import Path
from PIL import Image,ImageChops
from test_cube import ROOT,command,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');options=parser.parse_args()
 if options.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 platform=Path(__file__).resolve().parent;geometry=ROOT/'src/main/resources/assets/vandorlabs/models/block/data_chip_programmer/ready/parts'
 if not all((geometry/name).is_file() for name in ('static.obj','screen.obj')):raise AssertionError('Missing authored render geometry')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_PROGRAMMER_GEOMETRY=str(geometry),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.programmerWorldCheck=true -Dvandorlabs.programmerRenderMatrixCheck=true')
 subprocess.run(command('compileSmokeJava'),cwd=platform,env=env,check=True)
 if not (platform/'build/classes/java/smoke/com/vandorlabs/neoforge/programmer/ProgrammerRenderWorldChecks.class').is_file():raise AssertionError('Missing compiled render matrix fixture')
 out=Path(artifact_directory(prefix='programmer-render-world-',dir=ROOT/'build/test-artifacts'))
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=programmer-render-world'),server_log,env)
 try:
  deadline=time.monotonic()+120
  while 'Done (' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Programmer render server startup: '+str(server_log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  args=command('runClient','-PtransmitterPhase=programmer-render-world','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client))
  if options.xvfb:args=['xvfb-run','-a','-s','-screen 0 1280x720x24']+args;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  process=launch(args,out/'client.log',env)
  try:
   code=process.wait(timeout=420)
   if code:raise RuntimeError('Programmer render client exit '+str(code)+': '+str(out/'client.log'))
   check_log(out/'client.log','[vandorlabs][programmer-render-world] client PASS')
  finally:stop(process)
  deadline=time.monotonic()+30
  while '[vandorlabs][programmer-render-world] logout PASS' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Programmer render logout missing: '+str(server_log))
   time.sleep(.1)
  check_log(server_log,'[vandorlabs][programmer-render-world] server PASS')
  meta=json.loads((client/'programmer-world-projections.json').read_text());assert len(meta['profiles'])==24
  pixels=[]
  for row in meta['profiles']:
   profile=row['profile'];a=Image.open(client/f'screenshots/programmer-dock-{profile:02d}-empty.png').convert('RGB');b=Image.open(client/f'screenshots/programmer-dock-{profile:02d}-occupied.png').convert('RGB')
   if a.size!=(1280,720) or b.size!=a.size:raise AssertionError('Capture dimensions')
   inside=outside=0;px0,py0,px1,py1=row['pad'];x0,y0,x1,y1=row['body']
   for y in range(max(0,y0),min(720,y1)):
    for x in range(max(0,x0),min(1280,x1)):
     ca,cb=a.getpixel((x,y)),b.getpixel((x,y))
     if max(abs(ca[i]-cb[i]) for i in range(3))>=6:
      if px0<=x<px1 and py0<=y<py1:inside+=1
      else:outside+=1
   if inside<20 or outside>max(50,inside//6):raise AssertionError('Isolated authored dock pixels '+str((profile,row,inside,outside)))
   pixels.append(dict(profile=profile,inside=inside,outside=outside))
  crop=tuple(meta['journalBody']);baseline=Image.open(client/'screenshots/programmer-journal-before.png').convert('RGB').crop(crop)
  for name in ('programmer-journal-material.png','programmer-journal-reloaded.png'):
   if ImageChops.difference(baseline,Image.open(client/'screenshots'/name).convert('RGB').crop(crop)).getbbox():raise AssertionError('Journal/casing changed during material/reload '+name)
  proof=meta['upperOnly'];assert not proof['originSectionVisible'] and proof['ownerBoundsVisible'] and proof['draws']>0
  a=Image.open(client/'screenshots/programmer-upper-only-positive.png').convert('RGB');b=Image.open(client/'screenshots/programmer-upper-only-disabled.png').convert('RGB');changed=sum(max(abs(ca[i]-cb[i]) for i in range(3))>=10 for ca,cb in zip(a.getdata(),b.getdata()))
  if changed<1000:raise AssertionError('Upper-only disabled negative missing '+str(changed))
  (out/'render-pixels.json').write_text(json.dumps(dict(docks=pixels,upperOnly=proof,upperChanged=changed),sort_keys=True)+'\n')
 finally:stop(server)
 deadline=time.monotonic()+30
 while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Placed native Programmer24docks/reload/material/upper-only PASS: '+str(out),flush=True)
if __name__=='__main__':main()
