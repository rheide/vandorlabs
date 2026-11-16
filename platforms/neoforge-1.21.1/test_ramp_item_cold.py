#!/usr/bin/env python3
"""Healthy native item ACTIVE restart, exact resources and ordinary platform return."""
import os,argparse,hashlib
from pathlib import Path
from test_transmitter import ROOT,command,run,jdk_home,launch,stop
from test_sampled_materials import fixture_world
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--case',choices=('healthy','root-ahead','actors-ahead','return-before','return-after','unsupported','legacy','legacy-unknown','return-intent','pause'),default='healthy');args=parser.parse_args()
    (ROOT/'build/test-artifacts').mkdir(parents=True,exist_ok=True)
    out=Path(artifact_directory(prefix='ramp-item-cold-',dir=ROOT/'build/test-artifacts'));saved=out/'saved-world';fixture_world(saved,0)
    base=dict(os.environ,JAVA_HOME=str(jdk_home(21)),JAVA_TOOL_OPTIONS=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dvandorlabs.ramps=true -Dvandorlabs.rampSourceSnapshot='+str(ROOT/'testclient/fixtures/vehicle-ramps-2.6.nbt'))
    for phase in (('write',) if args.case=='unsupported' else ('write','read')):
        value=phase if args.case=='healthy' else args.case+'-'+phase
        env=dict(base,JAVA_TOOL_OPTIONS=base['JAVA_TOOL_OPTIONS']+' -Dvandorlabs.rampItemCold='+value+' -Dvandorlabs.transmitterPhase=ramp-controller-item-cold')
        if args.case=='legacy-unknown' and phase=='read':
            from test_ramp_assembly_journal import owned_digest
            files={p:owned_digest(p) for p in saved.rglob('*') if p.is_file() and (p.suffix=='.nbt' and 'ramp-journals' in p.parts or p.name=='r.46.46.mca' and p.parent.name=='entities')}
            if not files or not any('ramp-journals' in p.parts for p in files):raise RuntimeError('Missing actual future journal evidence')
            log=out/'read.log';process=launch(command('runServer','-PmodernServerRunDirectory='+str(saved)),log,env)
            try:process.wait(timeout=180)
            finally:stop(process)
            text=log.read_text(errors='replace')
            if 'Unknown legacy ACTIVE actor envelope retained' not in text or '[vandorlabs][ramp-item-cold] read PASS' in text or any(not p.exists() or owned_digest(p)!=digest for p,digest in files.items()):raise RuntimeError('Future journal refusal/opaque retention failed: '+str(log))
            (out/'retained.sha256').write_text('\n'.join(digest+'  '+str(p.relative_to(saved)) for p,digest in files.items())+'\n')
            print('[vandorlabs][ramp-item-cold] read PASS unknownFutureHeader/refusedBeforeGameplay/journalAndNativeEntitiesExact',flush=True)
            continue
        run(command('runServer','-PmodernServerRunDirectory='+str(saved)),out/(phase+'.log'),'[vandorlabs][ramp-item-cold] '+phase+' PASS',env)
    print('Native healthy item cold restart PASS '+str(out),flush=True)
if __name__=='__main__':main()
