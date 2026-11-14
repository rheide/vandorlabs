#!/usr/bin/env python3
"""Actual native arm async preparation, durable completion, abrupt source/target partial-save recovery."""
import argparse,os,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,world,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--case',choices=['source','target','prepared-edit','io-failure'])
    args=parser.parse_args()
    out=Path(artifact_directory(prefix='gear-recovery-',dir=ROOT/'build/test-artifacts'))
    for name,phases in [('source',['crash-source','recover-source']),('target',['crash-target','recover-target']),('prepared-edit',['prepared-edit']),('io-failure',['io-failure'])]:
        if args.case and args.case!=name:continue
        saved=out/'saved-world'/name
        world(saved,0)
        for phase in phases:
            env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.gearRuntime=true -Dvandorlabs.gearJournalPhase='+phase)
            run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/(phase+'.log'),'[vandorlabs][gear-recovery] '+phase+' PASS',env)
    classes=ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge'
    (out/'classes.sha256').write_text('\n'.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(classes)) for p in sorted((classes/'gear').glob('NativeGear*.class')))+'\n')
    print('Private native arm recovery PASS '+str(out),flush=True)

if __name__=='__main__':main()
