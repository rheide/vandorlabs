#!/usr/bin/env python3
"""Run actual installed power-lever host, item, size and saved-bank checks."""
import os
from pathlib import Path
import tempfile
from test_transmitter import ROOT,command,run,world,check_log,jdk_home

def main():
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(tempfile.mkdtemp(prefix='power-levers-',dir=base));saved=output/'saved-world';world(saved,0)
    for phase in ['power-write','power-read']:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),output/(phase+'.log'),'[vandorlabs][power-levers] server-'+phase+' PASS',env)
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native power lever focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
