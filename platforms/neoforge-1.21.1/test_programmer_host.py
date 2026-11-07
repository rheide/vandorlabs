#!/usr/bin/env python3
"""Private root-only Programmer host, physical inventory and opaque snapshot restart."""
import os,argparse,json
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--inventory-reference',type=Path,default=ROOT/'build/test-artifacts/programmer-inventory-forge.json');args=parser.parse_args();reference=args.inventory_reference.resolve();source=json.loads(reference.read_text());assert source['golden']=='488ea87cd420cc0f0520ed3e1f21d54d44ac393d240743099698485ae8b3f678' and len(source['cases'])==1800
 output=Path(artifact_directory(prefix='programmer-host-',dir=ROOT/'build/test-artifacts'));saved=output/'saved-world';world(saved,0)
 for phase in ('write','read'):
  run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=programmer-host-'+phase),output/(phase+'.log'),'[vandorlabs][programmer-host] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_PROGRAMMER_INVENTORY_REFERENCE=str(reference)))
  check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Private native Programmer host write/restart PASS: '+str(output),flush=True)
if __name__=='__main__':main()
