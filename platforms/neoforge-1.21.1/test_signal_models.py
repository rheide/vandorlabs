#!/usr/bin/env python3
"""Narrow actual signal-control emitted-model/item matrix, separate from live interaction acceptance."""
import argparse
import os
from pathlib import Path
import tempfile
from test_cube import ROOT, command, run, jdk_home
from native_test_artifacts import artifact_directory, retain_artifacts


@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');parser.add_argument('--reference',type=Path,required=True);parser.add_argument('--base-poses',action='store_true');args=parser.parse_args()
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);output=Path(artifact_directory(prefix='signal-model-',dir=base));game=output/'client';game.mkdir()
    (game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\n')
    props=['-PmodernClientRunDirectory='+str(game)];env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_SIGNAL_MODEL_CHECKS='true')
    if args.base_poses:env['VANDOR_SIGNAL_BASE_POSES']='true'
    if args.reference:env['VANDOR_SIGNAL_REFERENCE']=str(args.reference.resolve())
    launch=command('runClient',*props)
    if args.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
    run(launch,output/'client.log','[vandorlabs][signal-model] base PASS' if args.base_poses else '[vandorlabs][signal-model] client PASS',env)
    print('Actual emitted signal-control model checks passed: '+str(output.relative_to(ROOT)),flush=True)


if __name__=='__main__':main()
