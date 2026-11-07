#!/usr/bin/env python3
"""Audit canonical dynamic-family bucket/flow material policy against actual released source."""
import argparse,os,json
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--require-parity',action='store_true');parser.add_argument('--reference',type=Path,default=ROOT/'build/test-artifacts/mounted-fluid-material-forge.json');args=parser.parse_args()
 reference=args.reference.resolve();source=json.loads(reference.read_text());assert source['schema']==1 and source['blocks']==42 and len(source['cases'])==48872 and source['golden']=='3e7619ff6a3719369ef6cd24957f5b91a539394630b11a3e36e3fc9a41953cc1'
 output=Path(artifact_directory(prefix='mounted-fluid-material-',dir=ROOT/'build/test-artifacts'));saved=output/'world';world(saved,0)
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_MOUNTED_FLUID_MATERIAL_REFERENCE=str(reference),VANDOR_MOUNTED_FLUID_MATERIAL_OUTPUT=str(output/'audit.json'))
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=mounted-fluid-material-audit'),output/'audit.log','[vandorlabs][fluid-material-audit] PASS',env)
 result=json.loads((output/'audit.json').read_text());
 if args.require_parity and (result['missing'] or result['differences']):raise AssertionError('Canonical fluid material parity/ownership differs: '+str(output/'audit.json'))
 print('Actual canonical fluid material audit: '+str({k:v for k,v in result.items() if k!='cases'})+' '+str(output),flush=True)
if __name__=='__main__':main()
