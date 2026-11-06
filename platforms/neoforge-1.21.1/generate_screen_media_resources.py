#!/usr/bin/env python3
"""Source Screen media/crop policy and direct builtin-input playback, separate from atlas materials."""
import json
from pathlib import Path
import shutil
from PIL import Image
from material_playback_resources import _source,decode_animation

ROOT=Path(__file__).resolve().parents[2]
PLATFORM=Path(__file__).resolve().parent
OUT=PLATFORM/'build/generated/screen-media-resources'
ASSETS=OUT/'assets/vandorlabs'
UNPAIRED={'analysis_framed_gas_giant','analysis_framed_ice_planet','analysis_framed_surface_map','analysis_framed_terrestrial'}

def image(name,suffix):
 source=_source(ROOT,name,suffix)
 if source is None:raise ValueError('Missing source Screen media '+name+suffix)
 with Image.open(source) as value:return value.convert('RGBA')

def main():
 if OUT.exists():shutil.rmtree(OUT)
 (ASSETS/'textures/screen_media').mkdir(parents=True)
 catalog=json.loads((PLATFORM/'build/generated/screen-resources/assets/vandorlabs/data/screen_catalog.json').read_text())
 finishes=json.loads((PLATFORM/'build/generated/cube-resources/assets/vandorlabs/data/housing_finishes.json').read_text())
 extra=json.loads((ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text());offset=len(finishes)-len(extra)
 if offset<1 or len(catalog['choices'])!=len(finishes):raise ValueError('Source Screen media choice alignment')
 entries=[{} for _ in range(offset)]+extra;flags=[]
 for i,entry in enumerate(entries):
  source=entry.get('source','');canonical=entries[finishes[i]['canonical']].get('source','')
  unpaired=source.startswith('console_inputs/') and source.endswith('_static') and source[15:-7] in UNPAIRED
  flags.append((1 if entry.get('category')=='Screens' else 0)|(2 if canonical=='engineering_screen_static' else 0)|(4 if unpaired else 0))
 inputs={entry['id']:entry for entry in catalog['inputs']};built={};rows=[];decoded_pixels=0
 for original in catalog['inputs']:
  name=original['id'];canonical=catalog['inputAliases'].get(name,name)
  if canonical not in built:
   definition=inputs[canonical];frames=max(1,definition.get('frames',1)) if definition.get('animated',False) else 1;source='console_inputs/'+canonical;static=image(source,'_static.png');off=image(source,'_off.png');key='textures/screen_media/'+canonical
   if static.size!=off.size:raise ValueError('Source input Off dimensions '+canonical)
   choice=catalog['sources'].get(source+'_static',0);existing=finishes[choice].get('playback');animation=None;actual_frames=1
   if existing is not None:
    stationary=existing['staticTexture'];animation=existing.get('animationTexture')
    actual_frames=existing['frameCount']
   else:
    static.save(ASSETS/(key+'_static.png'),optimize=True);stationary='vandorlabs:'+key+'_static.png'
    if frames>1:
     decoded=decode_animation(ROOT,source,static)
     if decoded is None:raise ValueError('Missing source input animation '+canonical)
     strip,count=decoded;actual_frames=count
     decoded_pixels+=strip.width*strip.height
     if decoded_pixels>64*1024*1024:raise ValueError('Screen media decoded pixel budget exceeded')
     strip.save(ASSETS/(key+'_animated.png'),optimize=True);animation='vandorlabs:'+key+'_animated.png'
   off.save(ASSETS/(key+'_off.png'),optimize=True)
   built[canonical]=dict(canonical=canonical,frames=actual_frames,declaredFrames=frames,aspect=static.width/static.height,crop=1/32 if canonical in UNPAIRED else 0,staticTexture=stationary,offTexture='vandorlabs:'+key+'_off.png',animationTexture=animation,sourceStationary='vandorlabs:textures/blocks/'+source+'_static.png',sourceOff='vandorlabs:textures/blocks/'+source+'_off.png')
  rows.append(dict(id=name,**built[canonical]))
 data=ASSETS/'data';data.mkdir();(data/'screen_media_policy.json').write_text(json.dumps(dict(flags=flags,inputs=rows),separators=(',',':'))+'\n')
 print('Native Screen media:',len(flags),'choices,',sum(bool(f&1) for f in flags),'Screens,',sum(bool(f&2) for f in flags),'engineering crops,',sum(bool(f&4) for f in flags),'input crops,',len(rows),'input IDs,',len(built),'canonical bindings')

if __name__=='__main__':main()
