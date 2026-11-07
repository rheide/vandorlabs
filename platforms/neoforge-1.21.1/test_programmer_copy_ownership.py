#!/usr/bin/env python3
"""Copy-only unsupported ownership refusal; Program preserves a consumed item intact."""
import os
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 output=Path(artifact_directory(prefix='programmer-copy-ownership-',dir=ROOT/'build/test-artifacts'));saved=output/'world';world(saved,0)
 run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=programmer-copy-ownership'),output/'server.log','[vandorlabs][programmer-copy-ownership] PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
 print('Inactive programmer Copy ownership focus PASS: '+str(output),flush=True)
if __name__=='__main__':main()
