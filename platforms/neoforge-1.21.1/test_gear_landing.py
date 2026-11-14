#!/usr/bin/env python3
"""Private functional arm placement, redstone/reversal and real process restart."""
import os,argparse
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--recovery-only',action='store_true')
    parser.add_argument('--recover',type=Path)
    args=parser.parse_args()
    out=Path(artifact_directory(prefix='gear-landing-',dir=ROOT/'build/test-artifacts'))
    saved=args.recover if args.recover else out/'saved-world'
    if not args.recover:fixture_world(saved,0)
    for phase in (('recover',) if args.recover else ('crash','recover') if args.recovery_only else ('write','read','crash','recover')):
        if phase=='crash':
            saved=out/'crash-world'
            fixture_world(saved,0)
        env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.gearRuntime=true -Dvandorlabs.gearJournalPhase=landing -Dvandorlabs.gearLandingPhase='+phase)
        run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/(phase+'.log'),'[vandorlabs][gear-landing] '+phase+' PASS',env)
    print('Private native landing arm PASS '+str(out),flush=True)

if __name__=='__main__':main()
