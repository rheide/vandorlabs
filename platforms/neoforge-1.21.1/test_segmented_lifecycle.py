#!/usr/bin/env python3
"""Inactive native Segmented Door loaded topology, controller permissions and restart."""
import argparse,os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 p=argparse.ArgumentParser();p.add_argument('--legacy-reference',type=Path,required=True);a=p.parse_args();reference=a.legacy_reference.resolve()
 if not reference.is_file():p.error('exportSegmentedLifecycleReference output required')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='segmented-lifecycle-native-',dir=base));saved=output/'saved-world';world(saved,0)
 for phase in ['write','read']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=segmented-lifecycle-'+phase,'-PsegmentedLifecycleReference='+str(reference)),output/(phase+'.log'),'[vandorlabs][segmented-lifecycle] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Native Segmented lifecycle focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
