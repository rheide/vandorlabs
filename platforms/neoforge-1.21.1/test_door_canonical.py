#!/usr/bin/env python3
"""Actual canonical ordinary Door registrations across live native ticks and a subsequent server process."""
import os
from test_transmitter import ROOT, command, run, world, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts
from pathlib import Path

@retain_artifacts
def main():
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='door-canonical-native-',dir=base));saved=output/'saved-world';world(saved,0)
    for phase in ['write','read']:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=door-canonical-'+phase),output/(phase+'.log'),'[vandorlabs][door-canonical] '+phase+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Actual canonical ordinary Door dedicated acceptance PASS: '+str(output),flush=True)
if __name__=='__main__':main()
