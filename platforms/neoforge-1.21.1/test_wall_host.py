#!/usr/bin/env python3
"""Dedicated current wall/frame save replay; renderer and player-widget acceptance remain separate."""
import argparse
import os
from pathlib import Path
import tempfile
from test_transmitter import ROOT,command,run,world,check_log,jdk_home

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--legacy-reference',type=Path);parser.add_argument('--editor-only',action='store_true');args=parser.parse_args();reference=args.legacy_reference.resolve() if args.legacy_reference else None
    if not args.editor_only and (reference is None or not reference.is_file()):parser.error('Actual Forge testWallFrameLegacy reference required')
    phase='wall-editor' if args.editor_only else 'wall-host'
    properties=['-PtransmitterPhase='+phase]
    if reference:properties.append('-PwallFrameReference='+str(reference))
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(tempfile.mkdtemp(prefix='wall-host-native-',dir=base));saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),*properties),output/'persistence.log','[vandorlabs]['+phase+'] dedicated PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    check_log(saved/'logs/latest.log','All dimensions are saved');print('Native wall host focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
