#!/usr/bin/env python3
"""Inactive programmer Source policies, draft and pure copy transformations."""
import argparse,os,json
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--reference',type=Path,default=ROOT/'build/test-artifacts/programmer-forge.json');args=parser.parse_args();reference=args.reference.resolve();source=json.loads(reference.read_text())
 assert source['golden']=='ef75c4ffecd2e1b4c7b4ce4fb50c4bbf6894dc8ccd51e3202e2342e7476a9ce2' and len(source['cases'])==21904 and len(source['operations'])==392
 output=Path(artifact_directory(prefix='programmer-foundations-',dir=ROOT/'build/test-artifacts'));saved=output/'world';world(saved,0)
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=programmer-foundations'),output/'server.log','[vandorlabs][programmer-foundations] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_PROGRAMMER_REFERENCE=str(reference)))
 print('Inactive native programmer Source replay PASS: '+str(output),flush=True)
if __name__=='__main__':main()
