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
p.add_argument('--legacy-artwork',action='store_true',help='Use ordinary artwork when the source JAR predates built-in chip readers')
p.add_argument('--java',default='java')
a=p.parse_args();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
marker=out/'.block-rename-fixture'
(out/'mods').mkdir(exist_ok=True)
if not marker.exists():
 if (out/'world').exists() or (out/'server.properties').exists():p.error('Refusing an existing server directory without the fixture marker')
 marker.write_text('Isolated block rename upgrade fixture\n')
(out/'eula.txt').write_text('eula=true\n')
if not (out/'server.properties').exists():
 (out/'server.properties').write_text('server-port=25573\nonline-mode=false\nlevel-name=world\nlevel-type=FLAT\ngenerate-structures=false\nallow-nether=false\nspawn-protection=0\nview-distance=3\nmax-tick-time=-1\n')
source=a.old_jar if a.prepare else a.jar
if not a.prepare and not a.jar:p.error('--jar is required for upgrade checks')
if source:
 for old in (out/'mods').glob('vandorlabs-*.jar'):old.unlink()
 shutil.copy2(source,out/'mods'/'vandorlabs-2.8.jar')
 print('candidate sha256='+hashlib.sha256(source.read_bytes()).hexdigest(),flush=True)
ids=['programmable_diagonal_half_console']*12
metas=[0,1,2,3,4,5,8,9,10,11,12,13]
phases=['prepare-spawn','prepare-near','prepare-far'] if a.prepare else ['near','far','repeat']
for phase in phases:
 path=out/(phase+'.log')
 with path.open('w') as log:
  args=[a.java,'-Xmx2G']
  if not a.prepare:args+=['-Dvandorlabs.blockRenameCheck='+phase,'-Dvandorlabs.blockRenameLegacyArtwork='+str(a.legacy_artwork).lower()]
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
    tile='{PrimarySurfaceTexture:-4,ScreenChipReader:{InsertedToken:{id:"vandorlabs:data_chip",Count:1b,Damage:3s,tag:{ChipType:"journal",JournalTitle:"Retained reader",JournalPages:["Preserved page"]}}}}'
    if a.legacy_artwork:tile='{PrimarySurfaceTexture:0}'
    for i,name in enumerate(ids):send(f'setblock {base+i} 80 {base} vandorlabs:{name} {metas[i]} replace '+(tile if i==0 else '{}'))
    send(f'setblock {base+13} 80 {base} minecraft:stone')
    if phase=='prepare-near':
     items=','.join('{Slot:'+str(i)+'b,id:"vandorlabs:'+name+'",Count:'+str(13+i)+'b,Damage:'+str(metas[i])+'s,tag:{display:{Name:"Legacy half screen '+str(metas[i])+'"},Opaque:{Proof:28}}}' for i,name in enumerate(ids))
     send(f'setblock 8 80 10 minecraft:chest 0 replace {{Items:[{items}]}}')
     send('summon minecraft:item 8.5 83 10.5 {NoGravity:1b,Age:-32768s,PickupDelay:32767s,Item:{id:"vandorlabs:programmable_diagonal_half_console",Count:3b,Damage:11s}}')
    next_spawn=520 if phase=='prepare-near' else 8
    send(f'setworldspawn {next_spawn} 70 {next_spawn}')
    send('save-all flush');send('say GLASS_SOURCE_READY');wait('GLASS_SOURCE_READY')
    if path.read_text().count('Block placed')!= (14 if phase=='prepare-near' else 13):raise RuntimeError('Legacy blocks not all placed: '+str(path))
   else:wait('block-rename-'+phase+' PASS')
   send('stop');proc.wait(timeout=45)
   if proc.returncode:raise RuntimeError(phase+' shutdown failed')
   print(phase+' PASS '+str(path),flush=True)
  finally:
   if proc.poll() is None:
    send('stop')
    try:proc.wait(timeout=30)
    except subprocess.TimeoutExpired:proc.kill();proc.wait()
print('Block rename migration '+('source prepared' if a.prepare else 'upgrade and cold restart PASS')+': '+str(out),flush=True)
