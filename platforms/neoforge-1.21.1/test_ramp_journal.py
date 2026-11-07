#!/usr/bin/env python3
"""Real asynchronous Ramp journals, durable completion and abrupt cross-chunk recovery."""
import os,hashlib,argparse
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--case',choices=['normal','io-failure','abrupt','checkpoint-failure','prepared-edit']);args=parser.parse_args()
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='ramp-journal-native-',dir=base))
 for suite,phases in [('prepared-edit',['prepared-edit','prepared-recover']),('normal',['write','read']),('io-failure',['iofail','iofail-read']),('abrupt',['crash','recover']),('checkpoint-failure',['checkpoint-fail','checkpoint-recover'])]:
  if args.case and args.case!=suite:continue
  saved=output/'saved-world'/suite;world(saved,0)
  for phase in phases:
   run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-journal-'+phase),output/(phase+'.log'),'[vandorlabs][ramp-journal] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
   if phase not in ['crash','checkpoint-fail','prepared-edit']:check_log(saved/'logs/latest.log','All dimensions are saved')
 evidence=[]
 classes=ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge'
 for path in sorted(list((classes/'ramp').glob('NativeRamp*.class'))+list((classes/'mixin').glob('NativeRamp*.class'))):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(classes)))
 (output/'classes.sha256').write_text('\n'.join(evidence)+'\n');print('Native Ramp journal focused PASS: '+str(output),flush=True)
if __name__=='__main__':main()
