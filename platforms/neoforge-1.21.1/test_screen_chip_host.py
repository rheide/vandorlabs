#!/usr/bin/env python3
"""Selected Screen chip ownership and configured snapshots across a whole-process restart."""
import argparse,os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 p=argparse.ArgumentParser();p.add_argument('--artwork-reference',type=Path,help='Deprecated; the host uses its production resource catalog');a=p.parse_args()
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='screen-chip-host-',dir=base));saved=output/'saved-world';world(saved,0)
 for phase in ['write','read']:
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=screen-block-chip-host-'+phase),output/(phase+'.log'),'[vandorlabs][screen-chip-host] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_READER_SCREEN_CHECK="true"))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Selected Screen chip host focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
