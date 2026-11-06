#!/usr/bin/env python3
"""Narrow credential reader policy against the actual Forge tile in an installed runtime."""
import argparse
import hashlib
import os
from pathlib import Path
from native_test_artifacts import artifact_directory, retain_artifacts
from test_transmitter import ROOT, command, run, world, check_log, jdk_home

@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--legacy-reference',type=Path,required=True)
    args=parser.parse_args();reference=args.legacy_reference.resolve()
    if not reference.is_file():parser.error('Actual Forge credential stack oracle required')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='credential-reader-native-',dir=base))
    (output/'reference.sha256').write_text(hashlib.sha256(reference.read_bytes()).hexdigest()+'  '+reference.name+'\n',encoding='utf-8')
    saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=credential-reader','-PcredentialReaderReference='+str(reference)),output/'server.log','[vandorlabs][credential-reader] dedicated PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved')
    print('Credential reader policy runtime PASS: '+str(output),flush=True)

if __name__=='__main__':main()
