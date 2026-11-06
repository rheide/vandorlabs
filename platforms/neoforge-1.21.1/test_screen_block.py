#!/usr/bin/env python3
"""Inactive Screen block domains and actual engine collision unions in an isolated dedicated process."""
import os,argparse
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--rows-only',action='store_true');args=parser.parse_args();phase='screen-block-rows' if args.rows_only else 'screen-block';marker='Native Screen row edits PASS' if args.rows_only else 'Native Screen block foundation PASS'
 required={'screen-joined-power-reference.json':'testScreenJoinedPowerReference','screen-row-edits-reference.json':'testScreenRowEditsReference','screen-transmission-actions-reference.json':'testScreenTransmissionActionsReference'}
 missing=[(name,task) for name,task in required.items() if not (ROOT/'build/test-artifacts'/name).is_file()]
 if missing:raise RuntimeError('Missing released Screen references before runtime creation: '+', '.join(name for name,task in missing)+'; generate with Java 8 ./gradlew '+' '.join(task for name,task in missing))
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='screen-block-native-',dir=base));saved=output/'world';world(saved,0)
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),output/'run.log',marker,dict(os.environ,JAVA_HOME=str(jdk_home(21))))
 check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Inactive native Screen block foundation PASS: '+str(output),flush=True)
if __name__=='__main__':main()
