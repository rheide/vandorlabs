#!/usr/bin/env python3
"""Actual Source standalone reader geometry in a private dedicated runtime."""
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
    parser.add_argument('--editor-reference',type=Path,default=ROOT/'build/test-artifacts/standalone-reader-editor-forge.json')
    parser.add_argument('--item-reference',type=Path,default=ROOT/'build/test-artifacts/standalone-reader-item-forge.json')
    parser.add_argument('--host-write-only',action='store_true',help='Narrow failure diagnosis; excludes geometry and restart phases')
    args=parser.parse_args();reference=args.legacy_reference.resolve()
    if not reference.is_file():parser.error('Actual Source standalone reader oracle required')
    editor=args.editor_reference.resolve()
    if not editor.is_file():parser.error('Export the Source editor first with exportStandaloneReaderEditorReference, or pass --editor-reference')
    items=args.item_reference.resolve()
    if not items.is_file():parser.error('Export the Source item callbacks first with exportStandaloneReaderItemReference, or pass --item-reference')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(artifact_directory(prefix='standalone-reader-native-',dir=base))
    (output/'reference.sha256').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n' for path in (reference,editor,items)),encoding='utf-8')
    saved=output/'saved-world';world(saved,0)
    if not args.host_write_only:
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=standalone-reader-geometry','-PstandaloneReaderReference='+str(reference)),output/'server.log','[vandorlabs][standalone-reader-geometry] dedicated PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log','All dimensions are saved')
    for phase in (('write',) if args.host_write_only else ('write','read')):
        marker='[vandorlabs][standalone-reader-host-'+phase+'] dedicated PASS'
        properties=['-PstandaloneReaderEditorReference='+str(editor),'-PstandaloneReaderItemReference='+str(items)]
        run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=standalone-reader-host-'+phase,*properties),output/('host-'+phase+'.log'),marker,dict(os.environ,JAVA_HOME=str(jdk_home(21))))
        check_log(saved/'logs/latest.log','All dimensions are saved')
    print(('Standalone reader focused host-write PASS: ' if args.host_write_only else 'Standalone reader geometry/editor/write/restart PASS: ')+str(output),flush=True)

if __name__=='__main__':main()
