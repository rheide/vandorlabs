#!/usr/bin/env python3
"""Inactive programmer snapshot staging from actual legacy saves and opaque-owner refusals."""
import argparse,os,json
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--reference',type=Path,default=ROOT/'build/test-artifacts/programmer-saved-forge.json');args=parser.parse_args();reference=args.reference.resolve();source=json.loads(reference.read_text());assert source['golden']=='c9fab72182445314cc7f6999ac818f586df26e733ad50ebdfef59335c5a19de3' and len(source['cases'])==3528
 output=Path(artifact_directory(prefix='programmer-saved-',dir=ROOT/'build/test-artifacts'));saved=output/'world';world(saved,0)
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=programmer-saved'),output/'server.log','[vandorlabs][programmer-saved] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_PROGRAMMER_SAVED_REFERENCE=str(reference)))
 print('Inactive native programmer saved staging PASS: '+str(output),flush=True)
if __name__=='__main__':main()
