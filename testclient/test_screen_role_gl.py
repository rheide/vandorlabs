#!/usr/bin/env python3
"""Released Screen renderer oracle in an isolated Forge client; no production world is opened."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from test_artifacts import ArtifactRun

def stop(process):
 if process is None or process.poll() is not None:return
 if os.name=='nt':process.terminate()
 else:os.killpg(process.pid,signal.SIGINT)
 try:process.wait(timeout=30)
 except subprocess.TimeoutExpired:
  if os.name=='nt':process.kill()
  else:os.killpg(process.pid,signal.SIGTERM)
  process.wait(timeout=10)

def main():
 p=argparse.ArgumentParser();p.add_argument('--xvfb',action='store_true');p.add_argument('--skip-build',action='store_true');p.add_argument('--capture-only',action='store_true');p.add_argument('--valid-activation',action='store_true');p.add_argument('--runtime',type=Path);a=p.parse_args()
 if a.xvfb and os.name=='nt':p.error('--xvfb requires Linux')
 runtime=a.runtime or ROOT/'testclient/runtime'
 if not (runtime/'cp.txt').is_file():
  common=Path(subprocess.check_output(['git','rev-parse','--git-common-dir'],cwd=ROOT,text=True).strip());common=(ROOT/common).resolve();runtime=common.parent/'testclient/runtime'
 if not (runtime/'cp.txt').is_file():raise RuntimeError('Source client runtime unavailable; supply --runtime')
 java_home=Path(os.environ.get('JAVA8_HOME','/usr/lib/jvm/java-8-openjdk-amd64'));java=java_home/'bin'/('java.exe' if os.name=='nt' else 'java')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(tempfile.mkdtemp(prefix='screen-role-gl-forge-',dir=base));lease=ArtifactRun(out,disposable_paths=('game',));process=None;success=False
 try:
  env=dict(os.environ,JAVA_HOME=str(java_home));wrapper=ROOT/('gradlew.bat' if os.name=='nt' else 'gradlew')
  if not a.skip_build:
   with (out/'build.log').open('w') as log:subprocess.run([str(wrapper),'jar','reobfScreenRoleOracleJar','--no-daemon'],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
  production=next(path for path in (ROOT/'build/libs').glob('vandorlabs-*.jar') if path.name.count('-')==1);fixture=next((ROOT/'build/libs').glob('*-screen-role-oracle.jar'))
  digest=hashlib.sha256(production.read_bytes()).hexdigest();(out/'artifact.sha256').write_text(digest+'  '+production.name+'\n');(out/'fixture.sha256').write_text(hashlib.sha256(fixture.read_bytes()).hexdigest()+'  '+fixture.name+'\n')
  resources=hashlib.sha256()
  with zipfile.ZipFile(production) as jar:
   for name in sorted(n for n in jar.namelist() if n.startswith('assets/') and not n.endswith('/')):resources.update(name.encode()+b'\0'+jar.read(name))
  (out/'resources.sha256').write_text(resources.hexdigest()+'\n');(out/'source-commit.txt').write_text(subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True))
  game=out/'game';(game/'mods').mkdir(parents=True);shutil.copy2(production,game/'mods'/production.name);shutil.copy2(fixture,game/'mods'/fixture.name);(game/'options.txt').write_text('pauseOnLostFocus:false\nrenderDistance:2\nmaxFps:60\nsoundCategory_master:0.0\n')
  cp=[str((runtime/line.strip()).resolve()) for line in (runtime/'cp.txt').read_text().splitlines() if line.strip()]
  for group in ['net.sf.trove4j/trove4j/3.0.3','org.scala-lang/scala-library/2.11.1']:
   found=list((Path.home()/'.gradle/caches/modules-2/files-2.1'/group).glob('*/*.jar'))
   if found:cp.append(str(found[0]))
  # A warmed final singleton getter can inline the original Tessellator and silently bypass capture.
  oracle_flags=['-XX:CompileCommand='+policy+',net/minecraft/client/renderer/Tessellator.'+method for policy in ['exclude','dontinline'] for method in ['getInstance','func_178181_a']]
  if a.valid_activation:oracle_flags.append('-Dvandorlabs.screenOracleValidActivation=true')
  command=[str(java),'-Xmx2G',*oracle_flags,'-Djava.library.path='+str(runtime/'natives'),'-cp',os.pathsep.join(cp),'net.minecraft.launchwrapper.Launch','--tweakClass','net.minecraftforge.fml.common.launcher.FMLTweaker','--gameDir',str(game),'--assetsDir',str(runtime/'assets'),'--assetIndex','1.12','--username','SCREENORACLE','--accessToken','0','--version','1.12.2-Forge14.23.5.2864','--fml.forgeVersion','14.23.5.2864','--fml.mcVersion','1.12.2','--fml.forgeGroup','net.minecraftforge','--width','1024','--height','720']
  if a.xvfb:command=['xvfb-run','-a','-s','-screen 0 1024x720x24']+command;env['LIBGL_ALWAYS_SOFTWARE']='1'
  with (out/'client.log').open('w') as log:
   process=subprocess.Popen(command,cwd=runtime,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=os.name!='nt');lease.track_process(process)
   try:result=process.wait(timeout=240)
   finally:stop(process)
  if result or 'CompilerOracle: unrecognized line' in (out/'client.log').read_text(errors='replace') or '[vandorlabs][screen-role-gl] PASS' not in (out/'client.log').read_text(errors='replace'):raise RuntimeError('Source Screen role GL capture failed: '+str(out/'client.log'))
  captured=game/'screen-role-gl.jsonl'
  if not captured.is_file() or captured.stat().st_size>32*1024*1024:raise RuntimeError('Missing or oversized Source Screen role capture')
  shutil.copy2(captured,out/'screen-role-gl.jsonl');rows=[json.loads(line) for line in captured.read_text().splitlines()];roles={row['id'] for row in rows}
  if len(rows)!=1260 or len(roles)!=7 or any(row['getterMask']<0 or row['getterMask']>=8192 or len(row['draws'])<2 or not any(draw['vertexCount']==4 for draw in row['draws']) for row in rows):raise RuntimeError('Source Screen role coverage mismatch')
  (out/'reference.sha256').write_text(hashlib.sha256(captured.read_bytes()).hexdigest()+'\n')
  from prepare_screen_role_reference import compact,verify
  contract=compact(out)
  if not a.capture_only:verify(contract,ROOT/('testclient/fixtures/screen-role-gl-activation-reference.json' if a.valid_activation else 'testclient/fixtures/screen-role-gl-reference.json'),exact=a.xvfb)
  (out/'screen-role-contract.json').write_text(json.dumps(contract,separators=(',',':'))+'\n')
  if hashlib.sha256(production.read_bytes()).hexdigest()!=digest:raise RuntimeError('Source standard JAR changed during capture')
  success=True;print('Actual Source Screen role GL capture PASS: '+str(out),flush=True)
 finally:
  partial=out/'game/screen-role-gl.jsonl'
  if not success and partial.is_file() and partial.stat().st_size<=32*1024*1024:shutil.copy2(partial,out/'partial-screen-role-gl.jsonl')
  stop(process);summary=lease.finish(success,active_processes=(() if process is None or process.poll() is not None else (process,)));print('Source runtime retention: '+str(summary),flush=True)

if __name__=='__main__':main()
