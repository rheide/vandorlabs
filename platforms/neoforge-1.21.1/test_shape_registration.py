#!/usr/bin/env python3
"""Small dedicated check of the actual native slab/stair registration and configured item boundaries."""
import os
import tempfile
from pathlib import Path
from test_transmitter import ROOT,command,run,world,check_log,jdk_home

def main():
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(tempfile.mkdtemp(prefix='shape-registration-',dir=base));saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=shape-registration'),output/'registration.log','[vandorlabs][shape-registration] dedicated PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native shape registration focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
