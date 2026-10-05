#!/usr/bin/env python3
"""Compose both leaves and their optional jamb into a square inventory preview."""
from pathlib import Path
import json,copy
root=Path(__file__).resolve().parents[1]
assets=root/'generated-resources/assets/vandorlabs'
# Model filenames are an asset contract shared with TileEntitySpaceDoor.modelId.
families=['observation','airlock','standard','security','reactor','viewport','laboratory','cargo','ventilation','cargo_lift','blast_shield','glazed_hangar','quarantine_seal','reactor_barrier','modular_shutter','white_glass']
for tier in ['low','medium','high']:
 for design,family in enumerate(families):
  for sliding in [False,True]:
   for framed in [False,True]:
    for hinges in ([True] if sliding else [False,True]):
     base='space_'+('reactor_service' if family=='reactor' else family)+('_sliding' if sliding else '_rotating')+('_door_' if design<5 else '_')+('framed' if framed else 'bare')
     suffix='' if sliding or hinges else '_no_hinges'
     # Leave slot padding after GUI rotation; unrotated 16x16 bounds are insufficient.
     model={'ambientocclusion':False,'textures':{},'elements':[],'display':{'gui':{'rotation':[15,205,0],'scale':[.65,.65,.65]},'firstperson_righthand':{'scale':[.75,.75,.75]},'thirdperson_righthand':{'scale':[.75,.75,.75]}}}
     for hand in ['left','right']:
      for part in ['fixed','leaf']:
       source=assets/'models/block/detailed_doors'/tier/(base+'_paired_'+hand+'_'+part+suffix+'.json');source=json.loads(source.read_text())
       prefix=hand+'_'+part+'_'
       for key,value in source['textures'].items():model['textures'][prefix+key]=value
       for element in copy.deepcopy(source['elements']):
        for bound in ['from','to']:
         x,y,z=element[bound];element[bound]=[(x+(16 if hand=='right' else 0))/2,y/2,z/3]
        for face in element['faces'].values():face['texture']='#'+prefix+face['texture'][1:]
        model['elements'].append(element)
     model['textures']['particle']=model['textures']['left_leaf_particle']
     dest=assets/'models/item/detailed_doors'/tier/('large_'+base+'_left_leaf'+suffix+'.json');dest.write_text(json.dumps(model,indent=2)+'\n')
