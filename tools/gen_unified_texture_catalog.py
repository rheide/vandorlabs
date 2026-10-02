#!/usr/bin/env python3
"""Append shared artwork choices while preserving the 78 original housing indices."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
assets=root/'texture-packs/default/assets/vandorlabs/textures/blocks'
entries=[]
for name in ['porthole','light_column_wall','slatted_lamp','window_lamp','lightbar_wall','logo']:
    entries.append(dict(id='light_'+name,label=name.replace('_wall','').replace('_',' ').title()+' On',category='Lights',source=name+'_on',unlit=name+'_off'))
designs=['observation','airlock','standard','security','reactor','door_viewport','door_laboratory','door_cargo','door_ventilation','lift_cargo_lift','lift_blast_shield','lift_glazed_hangar','lift_quarantine_seal','lift_reactor_barrier','lift_modular_shutter']
for design,name in enumerate(designs):
    for detail,tier in enumerate(['low','medium','high']):
        entries.append(dict(id='door_'+name+'_'+tier,label=name.removeprefix('door_').removeprefix('lift_').replace('_',' ').title()+' '+['Small','Medium','Large'][detail],category='Doors',source='space_doors/'+tier+'/'+name,rectangular=True,crop=True,design=design,detail=detail))
# First-frame artwork only. Include full screens and the half-height input/control set.
catalog=json.loads((root/'generated-resources/assets/vandorlabs/data/blocks.json').read_text())
sources=sorted({e['id']+'_static' for e in catalog if e.get('type')=='display'}|{'console_inputs/'+p.stem for p in (assets/'console_inputs').glob('*_static.png')})
for source in sources:
    if not (assets/(source+'.png')).exists():raise SystemExit('Missing source: '+source)
    name=source.rsplit('/',1)[-1].removesuffix('_static')
    entries.append(dict(id='screen_'+source.replace('/','_'),label=name.replace('_',' ').title(),category='Screens',source=source,rectangular=True))
# Append Off choices to keep all existing catalog identifiers stable.
for name in ['porthole','light_column_wall','slatted_lamp','window_lamp','lightbar_wall','logo']:
    entries.append(dict(id='light_'+name+'_off',label=name.replace('_wall','').replace('_',' ').title()+' Off',category='Lights',source=name+'_off'))
out=root/'generated-resources/assets/vandorlabs/data/unified_textures.json'
out.write_text(json.dumps(entries,indent=2)+'\n')
print('Generated',len(entries),'additional texture choices')
