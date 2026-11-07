#!/usr/bin/env python3
"""Prepared Ramp bodies through real asynchronous before-images and whole-process recovery."""
import argparse,os,hashlib,zlib,gzip
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home,launch,stop
from native_test_artifacts import artifact_directory,retain_artifacts
def owned_digest(path):
 # The actor witness is at B=(23600,100,23598): entity chunk(1475,1474).
 # Region timestamps, sector allocation and unrelated chunks belong to vanilla.
 if path.suffix!='.mca':return hashlib.sha256(path.read_bytes()).hexdigest()
 data=path.read_bytes();index=(1475&31)+(1474&31)*32;entry=int.from_bytes(data[index*4:index*4+4],'big');offset=(entry>>8)*4096
 if not offset or offset+5>len(data):raise RuntimeError('Missing actor entity chunk record')
 length=int.from_bytes(data[offset:offset+4],'big');kind=data[offset+4];payload=data[offset+5:offset+4+length]
 if len(payload)!=length-1:raise RuntimeError('Truncated actor entity chunk record')
 raw=zlib.decompress(payload) if kind==2 else gzip.decompress(payload) if kind==1 else payload if kind==3 else None
 if raw is None:raise RuntimeError('Unsupported entity chunk compression')
 return hashlib.sha256(raw).hexdigest()
@retain_artifacts
def main():
 p=argparse.ArgumentParser();p.add_argument('--case',choices=['normal','abrupt','factory','observer','root-observer','frame','frame-failure','frame-retire-failure','frame-abrupt','frame-rider-refusal','frame-rider-observer','belt-initial','belt-reset','frame-command','frame-initial-actor','frame-initial-actor-change','frame-initial-actor-abrupt','frame-initial-actor-abrupt-cold','frame-original-align','frame-cost'],default='normal');p.add_argument('--intent-refusal',action='store_true',help='Assert conservative pre-readiness refusal, not healthy actor recovery');args=p.parse_args();
 if args.intent_refusal and args.case!='frame-initial-actor-abrupt':p.error('--intent-refusal requires --case frame-initial-actor-abrupt')
 output=Path(artifact_directory(prefix='ramp-assembly-journal-native-',dir=ROOT/'build/test-artifacts'));saved=output/'saved-world';world(saved,0)
 try:
  for phase in (['write','read','verify'] if args.case=='frame-initial-actor-abrupt-cold' else ['write','read']):
   if phase=='read' and args.intent_refusal:
    files={p:owned_digest(p) for p in saved.rglob('*') if p.is_file() and (p.suffix=='.nbt' and 'ramp-journals' in p.parts or p.name=='r.46.46.mca' and p.parent.name=='entities')}
    if not files or not any('ramp-journals' in p.parts for p in files) or not any(p.parent.name=='entities' for p in files):raise RuntimeError('Missing real journal/entity partial-save evidence')
    (output/'retained-before.sha256').write_text('\n'.join(h+'  '+str(p.relative_to(saved)) for p,h in files.items())+'\n')
    log=output/'read.log';process=launch(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-assembly-journal-'+args.case+'-read'),log,dict(os.environ,JAVA_HOME=str(jdk_home(21))))
    try:process.wait(timeout=180)
    finally:stop(process)
    text=log.read_text(errors='replace')
    (output/'retained-after.sha256').write_text('\n'.join((owned_digest(p) if p.exists() else 'MISSING')+'  '+str(p.relative_to(saved)) for p in files)+'\n')
    if not any(reason in text for reason in ['Unresolved Ramp actor transfer intent retained before gameplay','Cold actor repair cannot rewrite unrelated or missing actor records','Actor chunk has queued or loaded state','Actor chunk has a pending deserialized payload','Actor chunk already has live sections']) or '[vandorlabs][ramp-assembly-journal] started mode=' in text or 'durably relocated actor must not join' in text or any(not p.exists() or owned_digest(p)!=h for p,h in files.items()):raise RuntimeError('Actor intent startup refusal/retention failed: '+str(log))
    (output/'retained.sha256').write_text('\n'.join(h+'  '+str(p.relative_to(saved)) for p,h in files.items())+'\n');print('[vandorlabs][ramp-actor-intent] unresolved startup refusal PASS',flush=True);continue
   marker='abrupt' if phase=='write' and args.case in ('abrupt','frame-abrupt','frame-initial-actor-abrupt','frame-initial-actor-abrupt-cold') else ('read' if phase=='verify' else phase)
   run(command('runServer','-PmodernServerRunDirectory='+str(saved),'-PtransmitterPhase=ramp-assembly-journal-'+args.case+'-'+phase),output/(phase+'.log'),'[vandorlabs][ramp-assembly-journal] '+marker+' PASS',dict(os.environ,JAVA_HOME=str(jdk_home(21))))
 finally:
  evidence=[]
  for tree in ['main','smoke']:
   for path in sorted((ROOT/('platforms/neoforge-1.21.1/build/classes/java/'+tree+'/com/vandorlabs/neoforge/ramp')).glob('NativeRamp*.class')):evidence.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+tree+'/'+path.name)
  (output/'classes.sha256').write_text('\n'.join(evidence)+'\n')
 print('Native Ramp '+('actor intent safety-only' if args.intent_refusal else 'prepared assembly '+args.case)+' PASS: '+str(output),flush=True)
if __name__=='__main__':main()
