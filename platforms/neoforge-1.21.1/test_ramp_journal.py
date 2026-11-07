#!/usr/bin/env python3
"""Real asynchronous Ramp journals, durable completion and abrupt cross-chunk recovery."""
import os,hashlib,argparse
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home,launch,stop
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--case',choices=['normal','io-failure','abrupt','checkpoint-failure','prepared-edit','restored-abrupt','startup-invalid']);args=parser.parse_args()
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='ramp-journal-native-',dir=base))
 for suite,phases in [('restored-abrupt',['restored-crash','restored-recover']),('prepared-edit',['prepared-edit','prepared-recover']),('normal',['write','read']),('io-failure',['iofail','iofail-read']),('abrupt',['crash','recover']),('checkpoint-failure',['checkpoint-fail','checkpoint-recover']),('startup-invalid',[])]:
  if args.case and args.case!=suite:continue
  saved=output/'saved-world'/suite;world(saved,0)
  if suite=='startup-invalid':
   blocked=saved/'world/vandorlabs/ramp-journals';blocked.parent.mkdir(parents=True,exist_ok=True);blocked.write_text('unavailable journal directory\n')
   log=output/'startup-invalid.log';process=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-journal-startup-invalid'),log,dict(os.environ,JAVA_HOME=str(jdk_home(21))))
   try:process.wait(timeout=180)
   finally:stop(process)
   text=log.read_text(errors='replace')
   if 'Ramp recovery admission cannot inspect journal directory' not in text or '[vandorlabs][ramp-journal]' in text or blocked.read_text()!='unavailable journal directory\n':raise RuntimeError('Startup refusal missing or recovery path changed: '+str(log))
   print('[vandorlabs][ramp-journal] startup-invalid PASS (explicit refusal before normal gameplay)',flush=True)
   continue
  for phase in phases:
   run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-journal-'+phase),output/(phase+'.log'),'[vandorlabs][ramp-journal] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
   if phase not in ['crash','checkpoint-fail','prepared-edit','restored-crash']:check_log(saved/'logs/latest.log','All dimensions are saved')
 evidence=[]
 classes=ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge'
 for path in sorted(list((classes/'ramp').glob('NativeRamp*.class'))+list((classes/'mixin').glob('NativeRamp*.class'))):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(classes)))
 (output/'classes.sha256').write_text('\n'.join(evidence)+'\n');print('Native Ramp journal focused PASS: '+str(output),flush=True)
if __name__=='__main__':main()
