#!/usr/bin/env python3
"""Draw native 64x32 controller panels and emit horizontal slab housings."""
import json
import random
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'generated-resources/assets/vandorlabs'
TEXTURES=ROOT/'texture-packs/additional/assets/vandorlabs/textures/block'
MODES=('ramp','filled_ramp','lift','extend','escalator_up','escalator_down','moat')
TEXTURES.mkdir(parents=True,exist_ok=True)

def panel(name,active,mode=None,arrow=None):
    # New half-height composition: short corner brackets, horizontal machinery,
    # shallow side vents and a wide status window. No source artwork is resized.
    rng=random.Random(name);im=Image.new('RGB',(64,32));px=im.load()
    for y in range(32):
        for x in range(64):
            grain=rng.randrange(-3,4);px[x,y]=tuple(v+grain for v in (38,46,56))
    d=ImageDraw.Draw(im);cyan=(127,244,247) if active else (78,170,181)
    d.rectangle((1,1,62,30),outline=(12,20,29));d.line((4,2,59,2),fill=(90,105,115));d.line((4,29,59,29),fill=(15,22,30))
    for x in (2,54):
        for y in (3,22):
            d.rectangle((x,y,x+7,y+6),fill=(24,33,48));d.line((x,y,x+7,y),fill=(69,82,96));d.rectangle((x+2,y+2,x+3,y+3),fill=(5,12,21))
    if mode is None:
        d.rectangle((12,6,44,25),fill=(14,22,26));d.line((12,6,44,6),fill=(92,108,113));d.line((12,25,44,25),fill=(66,80,88))
        for x in (16,23,30,37):
            d.rectangle((x,9,x+3,22),fill=(9,16,20));d.line((x,9,x,21),fill=(57,72,80))
        d.rectangle((47,10,51,21),fill=(8,20,25));d.line((48,12,50,12),fill=cyan);d.line((48,19,50,19),fill=(175,57,22))
    else:
        d.rectangle((12,6,51,25),fill=(8,22,27));d.line((12,6,51,6),fill=(81,125,137));d.line((12,25,51,25),fill=(58,90,101))
        if mode in ('ramp','filled_ramp','escalator_up','escalator_down'):
            d.line([(19,22),(25,22),(25,18),(31,18),(31,14),(37,14),(37,10),(44,10)],fill=cyan,width=2)
            if mode=='filled_ramp':d.polygon([(19,23),(44,10),(44,23)],fill=(53,108,117))
            if mode.startswith('escalator'):d.polygon([(16,13),(20,9),(24,13)] if mode.endswith('up') else [(40,19),(44,23),(48,19)],fill=cyan)
        elif mode=='lift':
            d.line((22,21,42,21),fill=cyan,width=2);d.line((32,11,32,18),fill=cyan,width=2);d.polygon([(27,12),(32,8),(37,12)],fill=cyan)
        elif mode=='extend':
            d.rectangle((19,13,44,20),outline=cyan);d.line((23,10,41,10),fill=cyan);d.polygon([(37,7),(43,10),(37,12)],fill=cyan)
        else:
            d.line([(19,10),(19,21),(44,21),(44,10)],fill=cyan,width=2);d.line((25,14,38,14),fill=cyan,width=2)
        d.line((14,10,14,21),fill=cyan);d.line((49,10,49,21),fill=cyan)
    if arrow:
        d.rectangle((25,8,44,24),fill=(8,20,25))
        points={'left':[(26,16),(34,9),(34,13),(43,13),(43,19),(34,19),(34,23)],'right':[(43,16),(35,9),(35,13),(26,13),(26,19),(35,19),(35,23)],'up':[(35,9),(27,16),(32,16),(32,23),(38,23),(38,16),(43,16)],'down':[(35,23),(27,16),(32,16),(32,9),(38,9),(38,16),(43,16)]}[arrow]
        d.polygon([(x,y+1) for x,y in points],fill=(29,83,95));d.polygon(points,fill=cyan)
        if arrow in ('left','right'):d.line((30,15,39,15),fill=(191,255,255))
        else:d.line((34,12,34,20),fill=(191,255,255))
    # Minecraft 1.12 stitches square static sprites. Two native half-height
    # panels share a square atlas; side UVs select the first without resizing.
    atlas=Image.new('RGB',(64,64));atlas.paste(im,(0,0));atlas.paste(im,(0,32));atlas.save(TEXTURES/(name+'.png'))

variants={}
for active in (False,True):
    status='on' if active else 'off';panel('ramp_slab_side_'+status,active)
    for arrow in ('left','right','up','down'):panel('ramp_slab_side_'+arrow+'_'+status,active,arrow=arrow)
    for mode,texture in enumerate(MODES):
        front='ramp_slab_'+texture+'_'+status;panel(front,active,texture)
        for half,y0 in (('bottom',0),('top',8)):
            for direction in ('horizontal','up','down'):
                name=f'ramp_slab_{texture}_{status}_{half}_{direction}'
                surfaces={f:'vandorlabs:block/ramp_slab_side_'+status for f in ('north','south','east','west')}
                surfaces.update(up='vandorlabs:block/ramp_mode_'+texture,down='vandorlabs:block/ramp_elevator_controller_bottom_'+status)
                if direction=='horizontal':
                    surfaces['north']='vandorlabs:block/'+front
                    surfaces['west']='vandorlabs:block/ramp_slab_side_left_'+status
                    surfaces['east']='vandorlabs:block/ramp_slab_side_right_'+status
                else:
                    surfaces.update({f:'vandorlabs:block/ramp_slab_side_'+direction+'_'+status for f in ('north','south','east','west')})
                    surfaces[direction]='vandorlabs:block/ramp_mode_'+texture
                faces={f:{'texture':'#'+f,'cullface':f,'uv':[0,0,16,8 if f in ('north','south','east','west') else 16]} for f in surfaces}
                # The internal horizontal half face must be emitted beside any neighbour.
                faces['up' if half=='bottom' else 'down'].pop('cullface')
                model={'parent':'minecraft:block/block','textures':{'particle':'vandorlabs:block/ramp_slab_side_'+status,**surfaces},'elements':[{'from':[0,y0,0],'to':[16,y0+8,16],'faces':faces}]}
                p=ASSETS/'models/block'/(name+'.json');p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(model,indent=2)+'\n')
            for face in ('north','east','south','west','up','down'):
                direction=face if face in ('up','down') else 'horizontal'
                entry={'model':f'vandorlabs:ramp_slab_{texture}_{status}_{half}_{direction}'}
                if face in ('east','south','west'):entry['y']={'east':90,'south':180,'west':270}[face]
                variants[f'active={str(active).lower()},active_face={face},half={half},mode={mode}']=entry
(ASSETS/'blockstates/programmable_ramp_slab.json').write_text(json.dumps({'variants':variants},indent=2)+'\n')
display={'gui':{'rotation':[30,225,0],'translation':[0,2,0],'scale':[.52,.52,.52]}}
for mode in MODES:
    (ASSETS/'models/item'/('ramp_slab_'+mode+'.json')).write_text(json.dumps({'parent':f'vandorlabs:block/ramp_slab_{mode}_off_bottom_horizontal','display':display},indent=2)+'\n')
(ASSETS/'models/item/programmable_ramp_slab.json').write_text(json.dumps({'parent':'vandorlabs:block/ramp_slab_ramp_off_bottom_horizontal','display':display,'overrides':[{'predicate':{'vandorlabs:ramp_mode':i},'model':'vandorlabs:item/ramp_slab_'+mode} for i,mode in enumerate(MODES) if i]},indent=2)+'\n')
lang=ASSETS/'lang/en_us.lang';s=lang.read_text();key='tile.vandorlabs.programmable_ramp_slab.name='
s='\n'.join(l for l in s.splitlines() if not l.startswith(key))+'\n'+key+'Ramp Controller Slab\n';lang.write_text(s)
recipe={'type':'minecraft:crafting_shaped','pattern':['RRR'],'key':{'R':{'item':'vandorlabs:programmable_ramp'}},'result':{'item':'vandorlabs:programmable_ramp_slab','count':6}}
(ROOT/'src/main/resources/assets/vandorlabs/recipes/programmable_ramp_slab.json').write_text(json.dumps(recipe,indent=2)+'\n')
print(f'Generated {len(variants)} slab states, 84 slab models and 24 new native 64x32 panels')
