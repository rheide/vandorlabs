#!/usr/bin/env python3
"""Existing exact cell journal plus pending actor readiness and saved APPLIED retirement; no new family."""
import argparse,os,sys,shutil
from pathlib import Path
NATIVE=Path(__file__).resolve().parents[2]/'platforms/neoforge-1.21.1'
if not (NATIVE/'test_transmitter.py').is_file():NATIVE=Path(__file__).resolve().parent
sys.path.insert(0,str(NATIVE))
from test_transmitter import ROOT,command,run,jdk_home
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--read-world',type=Path);parser.add_argument('--phases',default='write,crash,read,crash-applied,read-applied');args=parser.parse_args();phases=args.phases.split(',');allowed={'write','crash','read','crash-applied','read-applied'}
 if not phases or any(phase not in allowed for phase in phases):raise ValueError('Unknown readiness phase')
 out=Path(artifact_directory(prefix='cell-readiness-',dir=ROOT/'build/test-artifacts'))
 for phase in phases:
  group='applied' if 'applied' in phase else 'armed' if phase in ['crash','read'] else 'live';saved=out/group
  if not saved.exists():
   if args.read_world:
    if phase not in ('read','read-applied'):raise ValueError('Read-world replay permits only cold read phases')
    shutil.copytree(args.read_world.resolve(),saved,ignore=shutil.ignore_patterns('session.lock'))
   else:fixture_world(saved,0)
  env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.cellJournalChecks=true -Dvandorlabs.cellReadinessChecks=true -Dvandorlabs.cellJournalPhase='+phase)
  marker={'write':'PASS checks=','crash':'crash PASS','read':'read PASS','crash-applied':'applied-crash PASS','read-applied':'applied-read PASS'}[phase]
  log=out/(phase+'.log');run(command('runServer','-PmodernServerRunDirectory='+str(saved)),log,'[vandorlabs][cell-journal] '+marker,env)
  text=log.read_text(errors='replace')
  if phase.startswith('crash'):
   at=text.index('[vandorlabs][cell-journal] '+marker)
   if 'Stopping server' in text[at:]:raise AssertionError('Abrupt readiness writer gracefully stopped')
  elif '[vandorlabs][cell-readiness] PASS' not in text:raise AssertionError('Actual pending entity-section readiness proof missing')
 print('Typed cell actor readiness focused PASS '+str(out),flush=True)
if __name__=='__main__':main()
