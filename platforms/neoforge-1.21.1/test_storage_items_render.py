#!/usr/bin/env python3
"""Private Storage actual ItemRenderer/Source golden and padded icons, without public bindings."""
import argparse,os,hashlib
from pathlib import Path
from test_cube import ROOT,command,run,jdk_home
from native_test_artifacts import artifact_directory,retain_artifacts
def evidence(out):
 rows=[]
 for pattern in ['NativeStorage*Model*.class','NativeHousingQuads*.class','storage/NativeStorageAssets*.class','client/NativeStorageSpriteSource*.class']:
  for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/classes/java/main/com/vandorlabs/neoforge').glob(pattern)):rows.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(ROOT)))
 for path in sorted((ROOT/'testclient/fixtures').glob('storage-item*model*gz')):rows.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(ROOT)))
 for path in sorted((ROOT/'testclient/fixtures').glob('storage-item-content*gz')):rows.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(ROOT)))
 for path in sorted((ROOT/'platforms/neoforge-1.21.1/build/generated/storage-resources').rglob('*')):
  if path.is_file():rows.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(ROOT)))
 (out/'classes-and-resources.sha256').write_text('\n'.join(rows)+'\n')
@retain_artifacts
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--xvfb',action='store_true');parser.add_argument('--canonical',action='store_true');args=parser.parse_args()
 if args.xvfb and os.name=='nt':parser.error('--xvfb requires Linux')
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(artifact_directory(prefix='storage-items-render-',dir=base));game=out/'client';game.mkdir();(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nmaxFps:60\nmipmapLevels:4\nguiScale:2\n')
 launch=command('runClient','-PreaderRenderCheck','-PtransmitterPhase=storage-render-items','-PmodernClientRunDirectory='+str(game));env=dict(os.environ,JAVA_HOME=str(jdk_home(21)),VANDOR_STORAGE_CANONICAL_CHECK='true' if args.canonical else 'false')
 if args.xvfb:launch=['xvfb-run','-a','-s','-screen 0 1280x720x24']+launch;env['LIBGL_ALWAYS_SOFTWARE']='1'
 run(launch,out/'client.log','[vandorlabs][storage-item-render] title PASS',env)
 evidence(out)
 print(('Canonical' if args.canonical else 'Private')+' Storage item golden/actual icon PASS: '+str(out),flush=True)
if __name__=='__main__':main()
