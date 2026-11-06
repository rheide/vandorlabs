#!/usr/bin/env python3
"""Actual inactive Trapdoor item placement and source formation records."""
import os,argparse,time,hashlib,shutil
from pathlib import Path
from test_transmitter import ROOT,command,launch,stop,world,check_log,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--part',choices=['all','basic','formation','contacts','faults'],default='all');parser.add_argument('--replay-read',type=Path,help='Replay only the failed read phase in a retained disposable runtime');args=parser.parse_args()
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='trapdoor-item-native-',dir=base));previous=args.replay_read.resolve() if args.replay_read else out;saved=previous/'saved-world'
 if args.replay_read and (previous.parent!=base.resolve() or not previous.name.startswith('trapdoor-item-native-') or not (previous/'expected.snbt').is_file()):parser.error('--replay-read requires a retained Trapdoor item fixture runtime')
 if not args.replay_read:world(saved,0)
 env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_TRAPDOOR_ITEM_PART=args.part,VANDOR_TRAPDOOR_ITEM_EXPECTED=str(previous/"expected.snbt"))
 for key,name in [('VANDOR_TRAPDOOR_PLACEMENT_REFERENCE','trapdoor-placement-forge.json'),('VANDOR_TRAPDOOR_FORMATION_REFERENCE','trapdoor-formation-forge.jsonl'),('VANDOR_TRAPDOOR_CONTACT_REFERENCE','trapdoor-contact-placement-forge.jsonl')]:
  reference=base/name
  if not reference.is_file():raise RuntimeError('Missing actual source reference: '+str(reference))
  env[key]=str(reference)
  digest=hashlib.sha256()
  with reference.open('rb') as stream:
   for chunk in iter(lambda:stream.read(65536),b''):digest.update(chunk)
  with (out/'reference.sha256').open('a') as report:report.write(digest.hexdigest()+'  '+name+'\n')
 diagnostic=ROOT/'platforms/neoforge-1.21.1/build/trapdoor-read-shutdown-threads.log'
 if diagnostic.is_file():shutil.copyfile(diagnostic,out/'unload-before-fix-threads.log')
 for phase in (['read'] if args.replay_read else ['write','read'] if args.part in ['all','faults'] else ['write']):
  log=out/(phase+'.log');process=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=trapdoor-item-'+phase),log,env)
  try:
   deadline=time.monotonic()+180
   while process.poll() is None:
    text=log.read_text(errors='replace')
    if 'Exception caught during firing event: Trapdoor item world:' in text or 'Encountered an unexpected exception' in text:raise RuntimeError('Trapdoor fixture failed: '+str(log))
    if time.monotonic()>deadline:raise RuntimeError('Trapdoor fixture timed out: '+str(log))
    time.sleep(.2)
   if process.returncode:raise RuntimeError('Trapdoor fixture exited '+str(process.returncode)+': '+str(log))
   check_log(log,'[vandorlabs][trapdoor-item] '+phase+' PASS')
  finally:stop(process)
  check_log(saved/'logs/latest.log','All dimensions are saved')
 print('Native Trapdoor item focused PASS: '+str(out),flush=True)
if __name__=='__main__':main()
