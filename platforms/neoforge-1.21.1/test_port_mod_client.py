#!/usr/bin/env python3
"""Four installed optional networks in isolated native runtimes, with no compile dependencies."""
import argparse,os,socket,time,hashlib,json,shutil
from pathlib import Path
from test_cube import ROOT,command,launch,stop,check_log,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');parser.add_argument('--start',type=int,default=0);parser.add_argument('--end',type=int,default=4);options=parser.parse_args()
 if not 0<=options.start<options.end<=4:parser.error('Require 0 <= start < end <= 4')
 if options.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.resourcePorts=true -Dvandorlabs.portModClientCheck=true');base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='port-mod-client-',dir=base))
 metadata=json.loads((ROOT/'build/port-research/native-interop-downloads.json').read_text())
 mods=out/'optional-mods';mods.mkdir();records=[]
 required={'Mekanism-1.21.1-10.7.19.85.jar':'mekanism','rftoolspower-1.21-7.0.6.jar':'rftoolspower','mcjtylib-1.21-9.0.22.jar':'mcjtylib','rftoolsbase-1.21-6.0.11.jar':'rftoolsbase'}
 folders={'vandorlabs':os.pathsep.join(str((ROOT/'platforms/neoforge-1.21.1/build'/part).resolve()) for part in ['classes/java/main','resources/main','classes/java/smoke','resources/smoke'])}
 for item in metadata:
  if item['file'] not in required:continue
  source=ROOT/'build/compat-mods/neoforge-1.21.1'/item['file'];data=source.read_bytes();digest=hashlib.sha256(data).hexdigest()
  if digest!=item['sha256']:raise RuntimeError('Optional fixture JAR hash mismatch: '+str(source))
  target=mods/source.name;shutil.copyfile(source,target);folders[required[source.name]]=str(target.resolve());records.append(digest+'  '+source.name)
 if len(records)!=4:raise RuntimeError('Exact four verified optional JARs required')
 properties=out/'dev-mod-folders.properties';properties.write_text(''.join(key+'='+value.replace('\\','\\\\')+'\n' for key,value in folders.items()))
 (out/'optional-mods.sha256').write_text('\n'.join(records)+'\n')
 env['JAVA_TOOL_OPTIONS']+=' -Dvandorlabs.portModStart='+str(options.start)+' -Dvandorlabs.portModEnd='+str(options.end)+' -Xmx8G -Dfml.modFoldersFile="'+str(properties.resolve())+'"'
 with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
 extra=[];saved=out/'server';fixture_world(saved,port);server_log=out/'server.log';server=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=port-client',*extra),server_log,env)
 try:
  deadline=time.monotonic()+180
  while 'Done (' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Native port server startup: '+str(server_log))
   time.sleep(.5)
  client=out/'client';client.mkdir();(client/'options.txt').write_text('guiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nentityShadows:false\nonboardAccessibility:false\n')
  args=command('runClient','-PtransmitterPhase=port-client','-PmatterClientPort='+str(port),'-PmodernClientRunDirectory='+str(client),*extra)
  if options.xvfb:args=['xvfb-run','-a','-s','-screen 0 1280x720x24']+args;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
  process=launch(args,out/'client.log',env)
  try:
   code=process.wait(timeout=420)
   if code:raise RuntimeError('Native port client exit '+str(code)+': '+str(out/'client.log'))
   check_log(out/'client.log','[vandorlabs][port-mod-client] client PASS')
  finally:stop(process)
  deadline=time.monotonic()+30
  while '[vandorlabs][port-mod-client] logout PASS' not in server_log.read_text(errors='replace'):
   if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Native port logout missing: '+str(server_log))
   time.sleep(.1)
  for name in ['MEK_POWER','MEK_FLUID','MEK_MATTER','RF_POWER'][options.start:options.end]:check_log(server_log,'[vandorlabs][port-mod-client] case PASS '+name)
  for marker in ['completed','logout']:check_log(server_log,'[vandorlabs][port-mod-client] '+marker+' PASS')
 finally:stop(server)
 deadline=time.monotonic()+30
 while time.monotonic()<deadline and 'All dimensions are saved' not in (saved/'logs/latest.log').read_text(errors='replace'):time.sleep(.1)
 check_log(saved/'logs/latest.log','All dimensions are saved')
 evidence=[]
 for pattern in ['ports/Native*Port*.class','ports/NativeResourcePort*.class','ports/client/NativePort*.class']:
  for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge').glob(pattern)):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(ROOT)))
 (out/'classes-and-reference.sha256').write_text('\n'.join(evidence)+'\n')
 print('Actual native optional port networks PASS: '+str(out),flush=True)
if __name__=='__main__':main()
