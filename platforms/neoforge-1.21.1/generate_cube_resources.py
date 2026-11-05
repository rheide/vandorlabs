#!/usr/bin/env python3
"""Generate native cube catalogs/resources from the current canonical sources."""
import json
from pathlib import Path
import re
import shutil
from PIL import Image
ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / 'build/generated/cube-resources'
ASSETS = OUT / 'assets/vandorlabs'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def source_texture(name):
    for directory in ['texture-packs/additional', 'texture-packs/default', 'src/main/resources']:
        path=ROOT/directory/('assets/vandorlabs/textures/blocks/'+name+'.png')
        if path.is_file(): return path
    if name.endswith('_small'): return source_texture(name[:-6])
    raise ValueError('Missing canonical artwork: '+name)


def main():
    if OUT.exists():shutil.rmtree(OUT)
    menu=json.loads((ROOT/'src/main/resources/assets/vandorlabs/data/texture_menu.json').read_text())
    extra=json.loads((ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())
    base=re.findall(r'new Finish\("([^"]+)", "([^"]+)"\)',(ROOT/'src/portable/java/com/vandorlabs/tiles/HousingFinishCatalog.java').read_text())
    if not base:raise ValueError('Missing shared base catalog')
    entries=[dict(menu.get(id,{}),id=id,source=source) for id,source in base]+extra
    redirects={}
    for index,entry in enumerate(extra,len(base)):
        if 'alias' in entry:redirects[index]=next(i for i,item in enumerate(entries) if item['id']==entry['alias'])
        if entry.get('detail',0)>0:
            for target,item in enumerate(extra,len(base)):
                if item.get('detail')==0 and (entry.get('design') is not None and entry.get('design')==item.get('design') or entry.get('textureFamily') is not None and entry.get('textureFamily')==item.get('textureFamily')):
                    redirects[index]=target;break
    language={line.split('=',1)[0]:line.split('=',1)[1] for line in (ROOT/'generated-resources/assets/vandorlabs/lang/en_us.lang').read_text().splitlines() if '=' in line and not line.startswith('#')}
    catalog=[];atlas=[];copied={}
    for index,original in enumerate(entries):
        canonical=redirects.get(index,index);entry=entries[canonical]
        if entry.get('hidden',False):entry=entries[0]
        texture='housing/'+entry['id'];u=v=1.
        if texture not in copied:
            source=source_texture(entry['source'])
            with Image.open(source) as image:
                image=image.convert('RGBA');w,h=image.size
                x0,y0=(w//16,h//32) if 'crop' in entry else (0,0)
                w,h=w-x0,h-2*y0
                if 'design' in entry and entry.get('category')!='Double Doors' and not entry.get('hidden',False):h//=2;y0+=h
                if 'rectangular' in entry or 'top' in entry:
                    image=image.crop((x0,y0,x0+w,y0+h));canvas=1
                    while canvas<max(w,h):canvas*=2
                    padded=Image.new('RGBA',(canvas,canvas));padded.paste(image,(0,0))
                    if w<canvas:padded.paste(image.crop((w-1,0,w,h)).resize((canvas-w,h)),(w,0))
                    if h<canvas:padded.paste(padded.crop((0,h-1,canvas,h)).resize((canvas,canvas-h)),(0,h))
                    image=padded;u,v=w/canvas,h/canvas
                metadata=source.with_suffix('.png.mcmeta');animated=metadata.is_file() and 'animation' in json.loads(metadata.read_text()) and 'rectangular' not in entry and 'top' not in entry
                if not animated and max(image.size)>128:image=image.convert('RGBa').resize((128,128),Image.Resampling.LANCZOS).convert('RGBA')
                output=ASSETS/('textures/'+texture+'.png');output.parent.mkdir(parents=True,exist_ok=True);image.save(output,optimize=True)
                if animated:shutil.copy2(metadata,output.with_suffix('.png.mcmeta'))
                copied[texture]=(u,v)
        u,v=copied[texture]
        label=original.get('label',language.get('tile.vandorlabs.'+original['id']+'.name','tile.vandorlabs.'+original['id']+'.name'))
        if 'detail' in original:label=re.sub(r' (Small|Medium|Large)$','',label)
        general=not original.get('lightOnly',False) and original.get('detail',0)==0 and original.get('category') not in ['Frame','Screens','Lights','Doors','Double Doors']
        category=original.get('category','Materials')
        if category=='Screens':category='Controls' if 'menuGroup' not in original and original.get('source','').startswith('console_inputs/') else 'Displays'
        if 'menuGroup' in original:category+='/'+original['menuGroup']
        source=original['source'];bare=source.replace('_framed_','_bare_').replace('/framed_','/bare_')
        if bare.startswith('framed_'):bare='bare_'+bare[7:]
        bare_index=next((i for i,item in enumerate(entries) if item['source']==bare),0)
        display_selectable=category.startswith('Displays') and (bare==source or bare_index==0 or bare_index==index)
        catalog.append(dict(id=original['id'],label=label,category=category,visible=not original.get('hidden',False),general=general,displaySelectable=display_selectable,canonical=canonical,texture='vandorlabs:'+texture,u=u,v=v))
        # Preserve authored frame semantics independently of the main selectable artwork.
        if original.get('category')=='Frame' or any(key in original for key in ['frameRail','frameAspect','frameBorder','frameLight']):
            rail=original.get('frameRail');rail_id=None
            if rail:
                rail_id='vandorlabs:frame-rails/'+rail
                output=ASSETS/('textures/frame-rails/'+rail+'.png')
                if not output.exists():
                    from frame_edge_pixels import crop
                    with Image.open(source_texture(original['source'])) as full:
                        full=full.convert('RGBA');aspect=max(1,original.get('frameAspect',1))
                        if aspect>1:full=full.crop((0,0,full.width,max(1,int(full.height/aspect+.5))))
                        for edge in range(4):
                            edge_id=rail_id+('' if edge==0 else '_edge_'+str(edge))
                            edge_path=ASSETS/('textures/'+edge_id.split(':',1)[1]+'.png')
                            edge_path.parent.mkdir(parents=True,exist_ok=True)
                            crop(full,edge).save(edge_path,optimize=True)
                for edge in range(4):atlas.append(dict(type='minecraft:single',resource=rail_id+('' if edge==0 else '_edge_'+str(edge))))
            catalog[-1]['frame']=dict(rail=rail_id,aspect=max(1,original.get('frameAspect',1)),border=original.get('frameBorder',True),light=max(0,min(5,original.get('frameLight',0))))
        atlas.append(dict(type='minecraft:single',resource='vandorlabs:'+texture))
    metal=ASSETS/'textures/frame-default/metal_side.png'
    metal.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(source_texture('programmable_glass/metal_side'),metal)
    atlas.append(dict(type='minecraft:single',resource='vandorlabs:frame-default/metal_side'))
    from generate_mounted_control_resources import generate as generate_controls
    atlas.extend(generate_controls(ROOT,OUT))
    from generate_shape_gameplay_resources import generate as generate_shapes
    generate_shapes(ROOT,OUT)
    from generate_wireless_resources import generate as generate_wireless
    atlas.extend(generate_wireless(ROOT,OUT))
    write(ASSETS/'data/housing_finishes.json',catalog)
    write(OUT/'assets/minecraft/atlases/blocks.json',dict(sources=list({value['resource']:value for value in atlas}.values())+[dict(type='vandorlabs:frame_edges')]))
    variants={f'facing={face}':dict(model='vandorlabs:block/programmable_block',y=angle) for face,angle in [('north',0),('east',90),('south',180),('west',270),('up',90),('down',90)]}
    write(ASSETS/'blockstates/programmable_block.json',dict(variants=variants))
    write(ASSETS/'models/block/programmable_block.json',dict(parent='minecraft:block/cube_all',textures=dict(all='vandorlabs:housing/dark_wall_panel')))
    item=json.loads((ROOT/'src/main/resources/assets/vandorlabs/models/item/programmable_block.json').read_text());item['parent']='vandorlabs:block/programmable_block';item.pop('textures',None);write(ASSETS/'models/item/programmable_block.json',item)
    for name in ['programmable_block','configurizer']:
        recipe=json.loads((ROOT/('src/main/resources/assets/vandorlabs/recipes/'+name+'.json')).read_text());recipe['result']['id']=recipe['result'].pop('item');write(OUT/('data/vandorlabs/recipe/'+name+'.json'),recipe)
    output=ASSETS/'textures/item/configurizer.png';output.parent.mkdir(parents=True,exist_ok=True)
    with Image.open(ROOT/'texture-packs/default/assets/vandorlabs/textures/items/configurizer.png') as image:image.convert('RGBA').resize((128,128),Image.Resampling.LANCZOS).save(output,optimize=True)
    write(ASSETS/'models/item/configurizer.json',dict(parent='minecraft:item/generated',textures=dict(layer0='vandorlabs:item/configurizer')))
    write(OUT/'data/vandorlabs/loot_table/blocks/programmable_block.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:programmable_block','functions':[{'function':'minecraft:copy_components','source':'block_entity','include':['minecraft:block_entity_data']}]}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    trigger='programmable_trigger_block'
    write(ASSETS/('blockstates/'+trigger+'.json'),dict(variants=variants))
    write(ASSETS/('models/item/'+trigger+'.json'),item)
    recipe=json.loads((ROOT/('src/main/resources/assets/vandorlabs/recipes/'+trigger+'.json')).read_text());recipe['result']['id']=recipe['result'].pop('item')
    write(OUT/('data/vandorlabs/recipe/'+trigger+'.json'),recipe)
    loot=json.loads((OUT/'data/vandorlabs/loot_table/blocks/programmable_block.json').read_text());loot['pools'][0]['entries'][0]['name']='vandorlabs:'+trigger
    write(OUT/('data/vandorlabs/loot_table/blocks/'+trigger+'.json'),loot)
    print('Native cube catalog:',len(catalog),'stable choices,',len(copied),'canonical sprites')

if __name__=='__main__':main()
