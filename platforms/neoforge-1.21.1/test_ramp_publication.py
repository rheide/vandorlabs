#!/usr/bin/env python3
"""Selective Ramp before-image/receipt boundaries, whole-suite retention and fresh recovery."""
import argparse,os,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home,launch,stop
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--case',choices=['moving-factory','moving','unknown-part','receipt-failure','normal','arming-edit','armed','vacated','restoring','restored','factory'],default='normal');args=parser.parse_args()
 output=Path(artifact_directory(prefix='ramp-publication-native-',dir=ROOT/'build/test-artifacts'));saved=output/'saved-world';world(saved,0)
 for phase in ['write','read']:
  if args.case=='factory' and phase=='read':
   files={p:hashlib.sha256(p.read_bytes()).hexdigest() for p in (saved/'world/vandorlabs/ramp-journals').rglob('*.nbt')}
   if len(files)!=1:raise RuntimeError('Missing retained publication journal')
   log=output/'read.log';process=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-publication-factory-read'),log,dict(os.environ,JAVA_HOME=str(jdk_home(21))))
   try:process.wait(timeout=180)
   finally:stop(process)
   text=log.read_text(errors='replace')
   if 'Ramp recovery proxy factory unavailable' not in text or 'started mode=factory-read' in text or any(not p.exists() or hashlib.sha256(p.read_bytes()).hexdigest()!=h for p,h in files.items()):raise RuntimeError('Factory startup refusal/retention failed: '+str(log))
   print('[vandorlabs][ramp-publication] factory startup refusal PASS',flush=True);continue
  abrupt=phase=='write' and args.case in ['moving-factory','moving','armed','vacated','restoring','restored','factory'];marker='[vandorlabs][ramp-publication] '+('abrupt' if abrupt else phase)+' PASS'
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-publication-'+args.case+'-'+phase),output/(phase+'.log'),marker,dict(os.environ,JAVA_HOME=str(jdk_home(21))))
 evidence=[]
 for tree in ['main','smoke']:
  for path in sorted((ROOT/('platforms/neoforge-1.21.1/build/classes/java/'+tree+'/com/vandorlabs/neoforge/ramp')).glob('NativeRamp*.class')):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+tree+'/'+path.name)
 (output/'classes.sha256').write_text('\n'.join(evidence)+'\n');print('Native Ramp selective publication '+args.case+' PASS: '+str(output),flush=True)
if __name__=='__main__':main()
