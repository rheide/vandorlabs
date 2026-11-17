#!/usr/bin/env python3
"""Existing asynchronous/rollback/retry and abrupt cross-chunk recovery on frozen navigation inputs."""
import argparse
from pathlib import Path
import test_cell_journal as asynchronous
import test_cell_journal_recovery as recovery
from test_transmitter import ROOT,command
from native_test_artifacts import artifact_directory,retain_artifacts
from navigation_test_artifact import snapshot

def frozen_command(*args):
 return command(*args,'-x','compileJava','-x','compileSmokeJava','-x','processResources')

@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--allow-working-source-changes',action='store_true');parser.add_argument('--recovery-only',action='store_true');args=parser.parse_args()
 out=Path(artifact_directory(prefix='navigation-journals-',dir=ROOT/'build/test-artifacts'));frozen=snapshot(out,'before')
 asynchronous.command=frozen_command;recovery.command=frozen_command
 if not args.recovery_only:asynchronous.main();snapshot(out,'after-async',frozen,require_source_unchanged=not args.allow_working_source_changes)
 recovery.main();snapshot(out,'after-abrupt-recovery',frozen,require_source_unchanged=not args.allow_working_source_changes)
 print('Frozen navigation shared journal regression PASS '+str(out),flush=True)
if __name__=='__main__':main()
