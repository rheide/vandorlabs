#!/usr/bin/env python3
"""Actual native supplied gate bake/mesh/atlas comparison, isolated from gameplay."""
import argparse,hashlib,os
from pathlib import Path
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');parser.add_argument('--reference',type=Path,default=ROOT/'build/test-artifacts/segmented-gate-forge.jsonl');args=parser.parse_args();reference=args.reference.resolve();data=reference.read_bytes()
 if hashlib.sha256(data).hexdigest()!='2c2a5246aeb2894ea2237f75f287f0a162047fd615ba2f60b8a54325d2e6d605':raise ValueError('Export the frozen actual Source gate reference before starting runtime')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='segmented-gate-render-',dir=base));game=out/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nmipmapLevels:4\nguiScale:2\n')
 launch=command('runClient','-PreaderRenderCheck','-PmodernClientRunDirectory='+str(game));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_SEGMENTED_GATE_RENDER_CHECK='true',VANDOR_SEGMENTED_GATE_REFERENCE=str(reference))
 if args.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
 run(launch,out/'client.log','[vandorlabs][segmented-gates] title PASS',env);print('Native Segmented supplied gate render PASS: '+str(out),flush=True)
if __name__=='__main__':main()
