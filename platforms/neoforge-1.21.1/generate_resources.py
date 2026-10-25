#!/usr/bin/env python3
"""Transform canonical housing catalogs/artwork for the NeoForge adapter."""
import json
import re
import shutil
import sys
from pathlib import Path
from PIL import Image
ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / 'build/generated'
sys.path.insert(0,str(ROOT / 'tools'))
from compact_runtime_assets import resized as resize_runtime_item

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

def source_texture(name):
    for source in ('texture-packs/additional', 'texture-packs/default', 'src/main/resources'):
        path = ROOT / source / f'assets/vandorlabs/textures/blocks/{name}.png'
        if path.exists():
            return path
    if name.endswith('_small'):
        return source_texture(name[:-6])
    raise ValueError(f'Missing canonical texture {name}')

def main():
    shutil.rmtree(OUT, ignore_errors=True)
    resources = OUT / 'resources'
    assets = resources / 'assets/vandorlabs'
    catalog = json.loads((ROOT / 'generated-resources/assets/vandorlabs/data/blocks.json').read_text())
    extras = json.loads((ROOT / 'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())
    menu = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/data/texture_menu.json').read_text())
    source = (ROOT / 'src/portable/java/com/vandorlabs/tiles/HousingFinishCatalog.java').read_text()
    base = re.findall(r'new Finish\("([^"]+)", "([^"]+)"\)', source)
    entries = [dict(menu.get(id, {}), id=id, source=texture) for id, texture in base] + extras
    redirects = {}
    for i, entry in enumerate(extras, len(base)):
        if 'alias' in entry:
            redirects[i] = next(j for j, other in enumerate(entries) if other['id'] == entry['alias'])
        if entry.get('detail') == 2:
            for j, other in enumerate(extras, len(base)):
                if other.get('detail') == 0 and (entry.get('design') is not None and entry.get('design') == other.get('design') or entry.get('textureFamily') is not None and entry.get('textureFamily') == other.get('textureFamily')):
                    redirects[i] = j
                    break
    result = []
    atlas = []
    for i, original in enumerate(entries):
        index = redirects.get(i, i)
        entry = entries[index]
        if entry.get('hidden'):
            entry = entries[0]
        texture = f'housing/{entry["id"]}'
        output = assets / f'textures/{texture}.png'
        u, v = 1., 1.
        with Image.open(source_texture(entry['source'])) as source:
            image = source.convert('RGBA')
            if entry['source'].endswith('_small'):
                image=image.resize((128,128),Image.Resampling.LANCZOS)
            w,h = image.size
            crop = 'crop' in entry
            x0,y0 = (w//16,h//32) if crop else (0,0)
            w,h = w-x0,h-2*y0
            if 'design' in entry and entry.get('category') != 'Double Doors' and not entry.get('hidden'):
                h //= 2
                y0 += h
            # Mirror the legacy padded sprite and its used UV range.
            if 'rectangular' in entry or 'top' in entry:
                image = image.crop((x0,y0,x0+w,y0+h))
                canvas = 1
                while canvas < max(w,h): canvas *= 2
                padded = Image.new('RGBA',(canvas,canvas))
                padded.paste(image,(0,0))
                if w < canvas: padded.paste(image.crop((w-1,0,w,h)).resize((canvas-w,h)),(w,0))
                if h < canvas: padded.paste(padded.crop((0,h-1,canvas,h)).resize((canvas,canvas-h)),(0,h))
                image = padded
                u,v = w/canvas,h/canvas
            output.parent.mkdir(parents=True,exist_ok=True)
            metadata=source_texture(entry['source']).with_suffix('.png.mcmeta')
            animated=metadata.exists() and 'animation' in json.loads(metadata.read_text()) and 'rectangular' not in entry and 'top' not in entry
            if not animated and max(image.size)>128:
                image=image.convert('RGBa').resize((128,128),Image.Resampling.LANCZOS).convert('RGBA')
            if not output.exists(): image.save(output,optimize=True)
        metadata = source_texture(entry['source']).with_suffix('.png.mcmeta')
        if metadata.exists() and 'rectangular' not in entry and 'top' not in entry:
            shutil.copy2(metadata, output.with_suffix('.png.mcmeta'))
        label = original.get('label', original['id'].replace('_',' ').title())
        label = re.sub(r' (Small|Medium|Large)$','',label) if 'detail' in original else label
        result.append({'id':original['id'],'label':label,'category':original.get('category','Materials'),'visible':not original.get('hidden',False),'general':not original.get('lightOnly',False) and original.get('detail',0)==0 and original.get('category') not in ('Screens','Lights','Doors','Double Doors'),'canonical':index,'texture':f'vandorlabs:{texture}','u':u,'v':v})
        atlas.append({'type':'minecraft:single','resource':f'vandorlabs:{texture}'})
    write(assets / 'data/housing_finishes.json', result)
    write(resources / 'assets/minecraft/atlases/blocks.json', {'sources':list({e['resource']:e for e in atlas}.values())})
    variants = {f'facing={facing}':{'model':'vandorlabs:block/programmable_block','y':rotation} for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]}
    variants.update({f'facing={facing}':{'model':'vandorlabs:block/programmable_block','y':90} for facing in ('up','down')})
    write(assets / 'blockstates/programmable_block.json', {'variants':variants})
    write(assets / 'models/block/programmable_block.json', {'parent':'minecraft:block/cube_all','textures':{'all':'vandorlabs:housing/dark_wall_panel'}})
    write(assets / 'models/item/programmable_block.json', {'parent':'vandorlabs:block/programmable_block','display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.58,.58,.58]}}})
    lang = {}
    for line in (ROOT / 'generated-resources/assets/vandorlabs/lang/en_us.lang').read_text().splitlines():
        if '=' in line and not line.startswith('#'):
            key,value=line.split('=',1)
            if key.startswith('tile.vandorlabs.'):
                key=key.replace('tile.vandorlabs.','block.vandorlabs.',1).removesuffix('.name')
            elif key.startswith('item.vandorlabs.'):
                key=key.removesuffix('.name')
            lang[key]=value
    write(assets / 'lang/en_us.json', lang)
    (assets / 'textures/item').mkdir(parents=True,exist_ok=True)
    shutil.copy2(ROOT / 'texture-packs/default/assets/vandorlabs/textures/items/configurizer.png', assets / 'textures/item/configurizer.png')
    resize_runtime_item(assets / 'textures/item/configurizer.png',128)
    write(assets / 'models/item/configurizer.json', {'parent':'minecraft:item/generated','textures':{'layer0':'vandorlabs:item/configurizer'}})
    write(resources / 'pack.mcmeta', {'pack':{'pack_format':34,'description':'Vandor Labs modern resources'}})
    write(resources / 'data/vandorlabs/loot_table/blocks/programmable_block.json', {'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:programmable_block','functions':[{'function':'minecraft:copy_components','source':'block_entity','include':['minecraft:block_entity_data']}]}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for tag in ('mineable/pickaxe','needs_stone_tool'):
        write(resources / f'data/minecraft/tags/block/{tag}.json',{'replace':False,'values':['vandorlabs:programmable_matter_block','vandorlabs:programmable_block','vandorlabs:programmable_viewscreen','vandorlabs:redstone_transmitter','vandorlabs:redstone_receiver']})
    for id in ('programmable_matter_ingot','industrial_alloy_ingot'):
        source=ROOT / f'texture-packs/default/assets/vandorlabs/textures/items/{id}.png'
        output=assets / f'textures/item/{id}.png'
        output.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(source,output)
        resize_runtime_item(output,128)
        model=json.loads((ROOT / f'src/main/resources/assets/vandorlabs/models/item/{id}.json').read_text())
        model['textures']={key:value.replace(':items/',':item/') for key,value in model['textures'].items()}
        write(assets / f'models/item/{id}.json',model)
    write(assets / 'lang/en_us.json',lang)
    for id in ('programmable_block','programmable_viewscreen','programmable_matter_block','configurizer','industrial_alloy_ingot'):
        recipe=json.loads((ROOT / f'src/main/resources/assets/vandorlabs/recipes/{id}.json').read_text())
        recipe['result']['id']=recipe['result'].pop('item')
        write(resources / f'data/vandorlabs/recipe/{id}.json',recipe)
    write(assets / 'blockstates/programmable_matter_block.json', {'variants':{'':{'model':'vandorlabs:block/programmable_matter_block'}}})
    write(assets / 'models/block/programmable_matter_block.json', {'parent':'minecraft:block/cube_all','textures':{'all':'vandorlabs:block/matter/matter'}})
    matter_item=json.loads((ROOT / 'src/main/resources/assets/vandorlabs/models/item/programmable_matter_block.json').read_text())
    write(assets / 'models/item/programmable_matter_block.json',matter_item)
    matter_png=assets / 'textures/block/matter/matter.png'
    matter_png.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(source_texture('matter/matter'),matter_png)
    write(resources / 'data/vandorlabs/loot_table/blocks/programmable_matter_block.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:programmable_matter_block'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for id in ('redstone_transmitter','redstone_receiver'):
        model=json.loads((ROOT / f'src/main/resources/assets/vandorlabs/models/block/{id}.json').read_text())
        model['textures']={key:value.replace(':blocks/',':block/') for key,value in model['textures'].items()}
        write(assets / f'models/block/{id}.json', model)
        states=json.loads((ROOT / f'src/main/resources/assets/vandorlabs/blockstates/{id}.json').read_text())
        for variant in states['variants'].values(): variant['model']=f'vandorlabs:block/{id}'
        write(assets / f'blockstates/{id}.json',states)
        write(assets / f'models/item/{id}.json',json.loads((ROOT / f'src/main/resources/assets/vandorlabs/models/item/{id}.json').read_text()))
        for name in set(model['textures'].values()):
            texture=name.split('block/',1)[1]
            output=assets / f'textures/block/{texture}.png'
            output.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(source_texture(texture),output)
        write(resources / f'data/vandorlabs/loot_table/blocks/{id}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'vandorlabs:{id}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    from generate_screen_resources import generate as generate_screen
    generate_screen(ROOT, resources)
    from generate_control_resources import generate as generate_controls
    generate_controls(ROOT,resources)
    from generate_trigger_resources import generate as generate_trigger
    generate_trigger(ROOT,resources)
    from generate_table_resources import generate as generate_table
    generate_table(ROOT,resources)
    native_blocks=['industrial_table','industrial_power_lever','compact_power_lever','small_power_lever','large_power_lever','thruster_lever','airliner_throttle','fighter_throttle','wall_slider','programmable_trigger_block','push_button','rocker_switch','programmable_block','programmable_matter_block','programmable_viewscreen','programmable_console','programmable_half_console','programmable_input','programmable_half_input','programmable_diagonal_screen','programmable_diagonal_half_console','redstone_transmitter','redstone_receiver']
    write(OUT / 'adapter-coverage.json', {
        'registered_blocks':native_blocks,
        'registered_direct_items':['configurizer','programmable_matter_ingot','industrial_alloy_ingot'],
        'finish_count':len(entries),
        'unsupported_catalog_ids':[e['id'] for e in catalog if not e.get('retired') and not e.get('programmable_only') and not e.get('internal_model') and e['id'] not in native_blocks],
        'complete_families':[],
        'coverage_inventory':'docs/development/feature-coverage.json',
        'programmable_block':{
            'implemented':['shared finish catalog','shared dialog controls','per-face settings','layout settings','NBT settings persistence','server-authoritative editing','filesystem material discovery and resource reload','bounded client-thread joined face snapshots and authored Stretch/Fit geometry'],
            'pending':['custom material definitions','carried assembly joins','shader format interoperability','full family parity']}})
if __name__ == '__main__': main()
