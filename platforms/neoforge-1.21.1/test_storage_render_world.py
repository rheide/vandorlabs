#!/usr/bin/env python3
"""Private Storage chunk renderer, picker update, reload and saved-world inventory isolation."""
import argparse,os,socket,time,hashlib,importlib.util
from pathlib import Path
from test_cube import ROOT,command,run,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');parser.add_argument('--canonical',action='store_true');parser.add_argument('--cube-reference',type=Path,required=True);options=parser.parse_args()
 if options.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 reference=options.cube_reference.resolve()
 if hashlib.sha256(reference.read_bytes()).hexdigest()!='a867ffa4d243aa49c5451498157706b9cffd0e8e1ce1b7cf8170b585369066b7':raise AssertionError('Unexpected actual Source cube reference')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='storage-render-world-',dir=base));original=reference;reference=out/'cube-reference.json';reference.write_bytes(original.read_bytes())
 spec=importlib.util.spec_from_file_location('source_cube_fixture',ROOT/'testclient/prepare_cube_reference.py');cube=importlib.util.module_from_spec(spec);spec.loader.exec_module(cube);cube.prepare(out/'game')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_CUBE_REFERENCE=str(reference),VANDOR_STORAGE_CANONICAL_CHECK='true' if options.canonical else 'false')
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 extra=['-PcubeReference='+str(reference)];saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=storage-render-world',*extra),server_log,env)
 try:
  deadline=time.monotonic()+150
  while 'Done (' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Storage render server startup: '+str(server_log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:3\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  args=command('runClient','-PtransmitterPhase=storage-render-world','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client),*extra)
  if options.xvfb:args=['xvfb-run','-a','-s','-screen 0 1280x720x24']+args;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  process=launch(args,out/'client.log',env)
  try:
   code=process.wait(timeout=300)
   if code:raise RuntimeError('Storage render client exit '+str(code)+': '+str(out/'client.log'))
   check_log(out/'client.log','[vandorlabs][storage-render-world] client PASS');check_log(out/'client.log','[vandorlabs][cube] Forge-reference PASS')
  finally:stop(process)
  deadline=time.monotonic()+30
  while '[vandorlabs][storage-render-world] logout PASS' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Storage render logout missing: '+str(server_log))
   time.sleep(.1)
  for marker in ['setup','picker','saved','logout']+(['placement-setup','placement','mining'] if options.canonical else []):check_log(server_log,'[vandorlabs][storage-render-world] '+marker+' PASS')
 finally:stop(server)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=storage-render-read',*extra),out/'read.log','[vandorlabs][storage-render-world] read PASS',env)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 from PIL import Image,ImageChops
 def image(name):return Image.open(client/('screenshots/storage-world-'+name+'.png')).convert('RGB')
 positive=image('positive')
 def delta(name):return sum(any(c>12 for c in p) for p in ImageChops.difference(positive,image(name)).getdata())
 disabled,reloaded,edited=delta('disabled'),delta('reloaded'),delta('edited')
 if disabled<200:raise AssertionError('Actual terrain positive equals disabled: '+str(disabled))
 if reloaded>100:raise AssertionError('Resource reload changed stationary capture: '+str(reloaded))
 if edited<20:raise AssertionError('Actual picker update did not change terrain: '+str(edited))
 (out/'renderer-report.txt').write_text(f'Pixels changed vs disabled: {disabled}\nPixels changed after reload: {reloaded}\nPixels changed after picker: {edited}\n')
 evidence=[]
 for pattern in ['NativeStorageModel*.class','NativeHousingQuads*.class','storage/NativeStorage*.class','client/NativeStorageSpriteSource*.class']:
  for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge').glob(pattern)):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(ROOT)))
 for path in sorted((ROOT/'testclient/fixtures').glob('storage*reference*gz')):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(ROOT)))
 for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/generated/storage-resources').rglob('*')):
  if path.is_file():evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(ROOT)))
 evidence.append(hashlib.sha256(reference.read_bytes()).hexdigest()+'  actual-source-cube-reference.json')
 (out/'classes-and-reference.sha256').write_text('\n'.join(evidence)+'\n')
 print(('Actual canonical' if options.canonical else 'Actual private')+' Storage renderer/picker/restart PASS: '+str(out),flush=True)
if __name__=='__main__':main()
