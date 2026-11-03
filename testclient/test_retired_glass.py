#!/usr/bin/env python3
"""Upgrade a real previous-JAR world, then load an untouched far chunk after another restart."""
import argparse, hashlib, shutil, subprocess, time
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--forge',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--jar',type=Path)
p.add_argument('--old-jar',type=Path,help='Previous standard JAR used to prepare the source world')
p.add_argument('--prepare',action='store_true')
p.add_argument('--java',default='java')
a=p.parse_args();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
marker=out/'.retired-glass-fixture'
if not marker.exists():
 if (out/'world').exists() or (out/'server.properties').exists():p.error('Refusing an existing server directory without the fixture marker')
 marker.write_text('Isolated retired glass upgrade fixture\n')
(out/'eula.txt').write_text('eula=true\n')
if not (out/'server.properties').exists():
 (out/'server.properties').write_text('server-port=25571\nonline-mode=false\nlevel-name=world\nlevel-type=FLAT\ngenerate-structures=false\nallow-nether=false\nspawn-protection=0\nview-distance=3\nmax-tick-time=-1\n')
source=a.old_jar if a.prepare else a.jar
if not a.prepare and not a.jar:p.error('--jar is required for upgrade checks')
if source:
 for old in (out/'mods').glob('vandorlabs-*.jar'):old.unlink()
 shutil.copy2(source,out/'mods'/'vandorlabs-2.8.jar')
 print('candidate sha256='+hashlib.sha256(source.read_bytes()).hexdigest(),flush=True)
ids=['clear_cockpit_glass','pale_cyan_cockpit_glass','smoked_cockpit_glass']
phases=['prepare-spawn','prepare-near','prepare-far'] if a.prepare else ['near','far','repeat']
for phase in phases:
 path=out/(phase+'.log')
 with path.open('w') as log:
  args=[a.java,'-Xmx2G']
  if not a.prepare:args+=['-Dvandorlabs.retiredGlassCheck='+phase]
  proc=subprocess.Popen(args+['-jar',str(a.forge.resolve()),'nogui'],cwd=out,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
  def send(command):proc.stdin.write(command+'\n');proc.stdin.flush()
  def wait(marker):
   deadline=time.monotonic()+180
   while time.monotonic()<deadline:
    text=path.read_text(errors='replace')
    if marker in text:return
    if proc.poll() is not None:raise RuntimeError(phase+' stopped; inspect '+str(path))
    if 'Exception in server tick loop' in text:raise RuntimeError(phase+' failed; inspect '+str(path))
    time.sleep(.2)
   raise TimeoutError(phase+' waiting for '+marker+'; inspect '+str(path))
  try:
   wait('Done (')
   if a.prepare and phase=='prepare-spawn':
    send('setworldspawn 8 70 8');send('save-all flush');send('say GLASS_SOURCE_READY');wait('GLASS_SOURCE_READY')
   elif a.prepare:
    base=8 if phase=='prepare-near' else 520
    for i,name in enumerate(ids):send(f'setblock {base+i} 80 {base} vandorlabs:{name}')
    for i,meta in enumerate([-1,0,7,9,14]):send(f'setblock {base+3+i} 80 {base} minecraft:'+('glass' if meta<0 else 'stained_glass '+str(meta)))
    if phase=='prepare-near':
     items=','.join('{Slot:'+str(i)+'b,id:"vandorlabs:'+name+'",Count:17b,Damage:0s,tag:{display:{Name:"Legacy '+name+'"},Opaque:{Proof:28}}}' for i,name in enumerate(ids))
     send(f'setblock 8 80 10 minecraft:chest 0 replace {{Items:[{items}]}}')
     send('summon minecraft:item 8.5 83 10.5 {NoGravity:1b,Age:-32768s,PickupDelay:32767s,Item:{id:"vandorlabs:pale_cyan_cockpit_glass",Count:3b,Damage:0s}}')
    next_spawn=520 if phase=='prepare-near' else 8
    send(f'setworldspawn {next_spawn} 70 {next_spawn}')
    send('save-all flush');send('say GLASS_SOURCE_READY');wait('GLASS_SOURCE_READY')
    if path.read_text().count('Block placed')!= (9 if phase=='prepare-near' else 8):raise RuntimeError('Legacy blocks not all placed: '+str(path))
   else:wait('retired-glass-'+phase+' PASS')
   send('stop');proc.wait(timeout=45)
   if proc.returncode:raise RuntimeError(phase+' shutdown failed')
   print(phase+' PASS '+str(path),flush=True)
  finally:
   if proc.poll() is None:
    send('stop')
    try:proc.wait(timeout=30)
    except subprocess.TimeoutExpired:proc.kill();proc.wait()
print('Retired glass migration '+('source prepared' if a.prepare else 'upgrade and cold restart PASS')+': '+str(out),flush=True)
