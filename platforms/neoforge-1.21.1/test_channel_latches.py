#!/usr/bin/env python3
"""Focused installed-host channel latch and independent JVM saved-bank checks."""
import os
from pathlib import Path
import tempfile
from test_transmitter import ROOT, command, run, world, check_log, jdk_home

def main():
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(tempfile.mkdtemp(prefix='channel-latches-',dir=base));saved=output/'saved-world';world(saved,0)
    for phase in ['latch-write','latch-read']:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase='+phase),output/(phase+'.log'),'[vandorlabs][channel-latches] server-'+phase+' PASS',env)
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Native channel latch host focused checks PASS: '+str(output),flush=True)

if __name__=='__main__':main()
