#!/usr/bin/env python3
"""Prepared Ramp bodies through real asynchronous before-images and whole-process recovery."""
import argparse,os,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 p=argparse.ArgumentParser();p.add_argument('--case',choices=['normal','abrupt','factory','observer','root-observer','frame','frame-failure','frame-retire-failure','frame-abrupt','frame-rider-refusal','frame-rider-observer'],default='normal');args=p.parse_args()
 output=Path(artifact_directory(prefix='ramp-assembly-journal-native-',dir=ROOT/'build/test-artifacts'));saved=output/'saved-world';world(saved,0)
 for phase in ['write','read']:
  marker='abrupt' if phase=='write' and args.case in ('abrupt','frame-abrupt') else phase
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-assembly-journal-'+args.case+'-'+phase),output/(phase+'.log'),'[vandorlabs][ramp-assembly-journal] '+marker+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
 evidence=[]
 for tree in ['main','smoke']:
  for path in sorted((ROOT/('platforms/neoforge-1.21.1/build/classes/java/'+tree+'/com/vandorlabs/neoforge/ramp')).glob('NativeRamp*.class')):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+tree+'/'+path.name)
 (output/'classes.sha256').write_text('\n'.join(evidence)+'\n');print('Native Ramp prepared assembly '+args.case+' PASS: '+str(output),flush=True)
if __name__=='__main__':main()
