#!/usr/bin/env python3
"""Inactive canopy shader/atlas/backend focus; no canonical registration or placed-world claim."""
import argparse,os,hashlib
from pathlib import Path
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts

@retain_artifacts
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--xvfb',action='store_true');args=parser.parse_args()
    if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
    base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='canopy-title-',dir=base));game=out/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nmipmapLevels:4\nguiScale:2\n')
    task=command('runClient','-PreaderRenderCheck','-PmodernClientRunDirectory='+str(game));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_CANOPY_RENDER_CHECK='true')
    if args.xvfb:task=['xvfb-run','-a','-s','-screen 0 1280x720x24']+task;env['LIBGL_ALWAYS_SOFTWARE']='1'
    run(task,out/'client.log','[vandorlabs][canopy-render] title PASS',env)
    classes=ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge'
    files=[classes/'NativeCanopyRenderer.class',classes/'NativePanelPlaybackRenderer.class',classes/'canopy/NativeCanopySpriteSource.class',classes/'canopy/NativeCanopyModels.class']
    (out/'artifact.sha256').write_text('\n'.join(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(classes)) for path in files)+'\n')
    print('Inactive canopy title software PASS '+str(out.relative_to(ROOT)),flush=True)

if __name__=='__main__':main()
