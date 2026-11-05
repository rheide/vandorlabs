#!/usr/bin/env python3
"""Focused actual diagonal factories, survival placement and state publication checks."""
import os
from pathlib import Path
import tempfile
from test_transmitter import ROOT,command,run,world,check_log,jdk_home

def main():
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(tempfile.mkdtemp(prefix='diagonal-registration-',dir=base));saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=diagonal-registration'),output/'registration.log','[vandorlabs][diagonal-registration] dedicated PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native diagonal registration focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
