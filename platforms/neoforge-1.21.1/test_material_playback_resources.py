#!/usr/bin/env python3
"""Real shipped layered animation versus the shared native generator, with bounded disposable output."""
import hashlib
from pathlib import Path
import sys
import tempfile
from PIL import Image
from material_playback_resources import PlaybackResources,_source

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from test_artifacts import ArtifactRun

def main():
 base=ROOT/'build/test-artifacts';base.mkdir(parents=True,exist_ok=True);out=Path(tempfile.mkdtemp(prefix='layered-playback-generator-',dir=base));lease=ArtifactRun(out,disposable_paths=('resources',));success=False
 try:
  assets=out/'resources';name='console_inputs/company_seyr_half_communications';canonical=dict(id='screen_company_seyr_half_communications',source=name+'_static');source=_source(ROOT,name,'_animated.png')
  if source is None:raise AssertionError('Actual released layered animation missing')
  with Image.open(_source(ROOT,name,'_static.png')) as image:base_image=image.convert('RGBA')
  with Image.open(source) as image:expected=image.convert('RGBA')
  count=expected.height//base_image.height
  if expected.width!=base_image.width or count!=6 or expected.getextrema()[3][0]!=0:raise AssertionError('Released six-frame straight-alpha fixture changed')
  descriptor=PlaybackResources(ROOT,assets).entry(dict(category='Screens'),canonical)
  if descriptor['frameCount']!=count or descriptor['animationTexture'] is None:raise AssertionError('Layered animation lost: Source frames='+str(count)+' native='+str(descriptor))
  with Image.open(assets/descriptor['animationTexture'].split(':',1)[1]) as image:actual=image.convert('RGBA')
  if actual.size!=expected.size or actual.tobytes()!=expected.tobytes():raise AssertionError('Layered animation RGBA changed')
  digest=hashlib.sha256(expected.tobytes()).hexdigest();(out/'rgba.sha256').write_text(digest+'\n');(out/'result.txt').write_text('frames='+str(count)+' dimensions='+str(expected.size)+' exact straight-alpha RGBA\n');success=True;print('Layered native playback generator PASS: '+str(out),flush=True)
 finally:print('Runtime retention: '+str(lease.finish(success)),flush=True)

if __name__=='__main__':main()
