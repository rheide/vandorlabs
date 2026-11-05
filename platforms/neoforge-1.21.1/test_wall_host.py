#!/usr/bin/env python3
"""Dedicated current wall/frame save replay; renderer and player-widget acceptance remain separate."""
import argparse
import os
from pathlib import Path
import tempfile
from test_transmitter import ROOT,command,run,world,check_log,jdk_home

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--legacy-reference',type=Path);parser.add_argument('--diagonal-reference',type=Path);modes=parser.add_mutually_exclusive_group();modes.add_argument('--editor-only',action='store_true');modes.add_argument('--panel-only',action='store_true');modes.add_argument('--porthole-registration',action='store_true');modes.add_argument('--diagonal-host',action='store_true');args=parser.parse_args();reference=args.legacy_reference.resolve() if args.legacy_reference else None
    if not args.editor_only and not args.porthole_registration and not args.diagonal_host and (reference is None or not reference.is_file()):parser.error('Actual Forge reference required: testPanelGlassLegacy for --panel-only, testWallFrameLegacy otherwise')
    phase='diagonal-host' if args.diagonal_host else 'porthole-registration' if args.porthole_registration else 'wall-editor' if args.editor_only else 'panel-glass' if args.panel_only else 'wall-host'

    diagonal_reference=args.diagonal_reference.resolve() if args.diagonal_reference else None
    if diagonal_reference and (not args.diagonal_host or not diagonal_reference.is_file()):parser.error('--diagonal-reference requires --diagonal-host and an existing actual Forge export')
    properties=['-PtransmitterPhase='+phase]
    if diagonal_reference:properties.append('-PdiagonalReference='+str(diagonal_reference))
    if reference:properties.append(('-PpanelGlassReference=' if args.panel_only else '-PwallFrameReference=')+str(reference))
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(tempfile.mkdtemp(prefix='wall-host-native-',dir=base));saved=output/'saved-world';world(saved,0)
    run(command('runServer','-PmodernServerRunDirectory='+str(saved),*properties),output/'persistence.log','[vandorlabs]['+phase+'] dedicated PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    if diagonal_reference:check_log(output/'persistence.log','[vandorlabs][diagonal-topology] dedicated PASS')
    check_log(saved/'logs/latest.log','All dimensions are saved');print('Native wall host focused checks PASS: '+str(output),flush=True)
if __name__=='__main__':main()
