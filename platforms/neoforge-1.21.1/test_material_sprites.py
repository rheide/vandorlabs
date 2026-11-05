#!/usr/bin/env python3
"""Assess actual vanilla saved-ID/default-state sprite conflicts without enabling sampling."""
import argparse
import json
import os
from pathlib import Path
import tempfile
from test_cube import ROOT, command, run, jdk_home


def compare(legacy_path, native_path):
    legacy=json.loads(legacy_path.read_text());native=json.loads(native_path.read_text())
    if not legacy['reloadStable'] or not native['reloadStable']:raise RuntimeError('Resource reload was not checked')
    old={row['name']:row for row in legacy['rows']}
    if len(old)!=254:raise RuntimeError('Incomplete actual legacy vanilla reference')
    oak=old['minecraft:wooden_door']
    if oak['choice']!=855711037 or oak['lower']==oak['upper']:raise RuntimeError('Legacy door identity/halves not established')
    conflicts=[]
    for row in native['rows']:
        if row['name'] not in old or old[row['name']]['choice']!=row['choice']:raise RuntimeError('Legacy saved ID mismatch: '+row['name'])
        if not row['match']:conflicts.append(row['name'])
    if len(native['rows'])<100:raise RuntimeError('Incomplete actual native overlap reference')
    print('Actual sprite assessment PASS: legacy=254 overlaps='+str(len(native['rows']))+' conflicts='+str(len(conflicts))+'; reload stable; conflicts require a separate new-sample identity domain',flush=True)
    for name in conflicts:print('Conflict: '+name,flush=True)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--legacy-reference',required=True,type=Path)
    parser.add_argument('--native-report',type=Path,help='Inspect an already completed native assessment instead of launching')
    parser.add_argument('--xvfb',action='store_true')
    args=parser.parse_args()
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    if args.native_report:compare(args.legacy_reference,args.native_report);return
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True)
    output=Path(tempfile.mkdtemp(prefix='material-sprites-',dir=base));game=output/'client';game.mkdir()
    (game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\n')
    report=output/'native-material-sprites.json'
    launch=command('runClient','-PmaterialSpriteAssessment='+str(report),'-PmodernClientRunDirectory='+str(game))
    env=dict(os.environ,JAVA_HOME=str(jdk_home(21)))
    if args.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env=dict(env,LIBGL_ALWAYS_SOFTWARE='1')
    run(launch,output/'client.log','[vandorlabs][material-sprites] native PASS',env)
    compare(args.legacy_reference,report)
    print('Development-classpath/software assessment: '+str(output.relative_to(ROOT)),flush=True)


if __name__=='__main__':main()
