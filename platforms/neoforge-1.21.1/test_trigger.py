#!/usr/bin/env python3
"""Focused native Trigger server, real chunk unload/reload, and second-process persistence."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from test_transmitter import ROOT, command, world, run, check_log, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts


@retain_artifacts
def main():
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    base=ROOT/'build/test-artifacts'
    base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='trigger-native-',dir=str(base)))
    saved=output/'saved-world'
    world(saved,0)
    for phase in ['write','read']:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=trigger-'+phase),output/(phase+'.log'),'[vandorlabs][trigger] server-'+phase+' PASS',env)
        check_log(saved/'logs/latest.log','All dimensions are saved')
    check_log(output/'write.log','actual-chunk-unload-reload PASS')
    print('Native Trigger server acceptance PASS: '+str(output),flush=True)


if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.TimeoutExpired) as error:
        print('Trigger smoke failed: '+str(error),file=sys.stderr)
        sys.exit(1)
