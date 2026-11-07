#!/usr/bin/env python3
"""Public canonical Segmented Door factories, native ticks, old layouts and separate-process restart."""
import argparse,os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 p=argparse.ArgumentParser();p.add_argument('--legacy-reference',type=Path,required=True);p.add_argument('--supplied',action='store_true');a=p.parse_args();reference=a.legacy_reference.resolve()
 if not reference.is_file():p.error('exportSegmentedCanonicalReference output required')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='segmented-canonical-native-',dir=base));saved=out/'saved-world';world(saved,0)
 for phase in ['write','read']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=segmented-canonical-'+phase,'-PsegmentedCanonicalReference='+str(reference)),out/(phase+'.log'),'[vandorlabs][segmented-canonical] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=(os.environ.get('JAVA_TOOL_OPTIONS','')+(' -Dvandorlabs.segmentedSuppliedCheck=true' if a.supplied else ''))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Public canonical Segmented Door dedicated acceptance PASS: '+str(out),flush=True)
if __name__=='__main__':main()
