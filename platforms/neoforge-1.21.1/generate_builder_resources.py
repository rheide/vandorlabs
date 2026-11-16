#!/usr/bin/env python3
"""Project only the three released builder structures and their exact native artwork."""
import hashlib
import json
from pathlib import Path
import shutil
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent/'build/generated/builder-resources'
IDS=('synthesis_tower','assembly_press','enclosed_gantry')
SOURCE=ROOT/'src/main/resources/assets/vandorlabs'

def main():
    if OUT.exists():shutil.rmtree(OUT)
    assets=OUT/'assets/vandorlabs';textures={};shared={};manifest={}
    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')
    def modern(reference):
        return {'minecraft:blocks/concrete_black':'minecraft:block/black_concrete','minecraft:blocks/concrete_white':'minecraft:block/white_concrete','minecraft:blocks/concrete_green':'minecraft:block/green_concrete'}.get(reference,reference.replace('minecraft:blocks/','minecraft:block/'))
    def texture(reference):
        reference=modern(reference)
        if not reference.startswith('vandorlabs:'):return reference
        if reference in textures:return reference
        relative=reference.split(':',1)[1]+'.png'
        bases=(ROOT/'texture-packs/additional/assets/vandorlabs/textures',ROOT/'texture-packs/default/assets/vandorlabs/textures',SOURCE/'textures')
        source=next((base/relative for base in bases if (base/relative).is_file()),None)
        if source is None:raise ValueError('Missing exact builder sprite '+reference)
        with Image.open(source) as image:
            if image.width>256 or image.height%image.width:raise ValueError('Invalid native builder sprite '+reference)
        digest=hashlib.sha256(source.read_bytes()).hexdigest()
        meta=source.with_suffix('.png.mcmeta')
        if relative.startswith(('blocks/resource_ports/','blocks/module_rack/')):
            # Cube projection already owns these exact native shared sprites.
            owner=OUT.parent/'cube-resources/assets/vandorlabs/textures'/relative
            if not owner.is_file():raise ValueError('generateCubeResources must run before builders: '+str(owner))
            if hashlib.sha256(owner.read_bytes()).hexdigest()!=digest:raise ValueError('Shared builder artwork differs from owner: '+reference)
            owner_meta=owner.with_suffix('.png.mcmeta')
            if meta.exists()!=owner_meta.exists() or meta.exists() and meta.read_bytes()!=owner_meta.read_bytes():raise ValueError('Shared builder animation metadata differs: '+reference)
            shared[reference]={'owner':'cube-resources','sha256':digest}
        else:
            target=assets/'textures'/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
            if meta.exists():shutil.copyfile(meta,target.with_suffix('.png.mcmeta'))
        textures[reference]=digest;return reference
    texture("vandorlabs:blocks/core_flat")
    for name in ("heavy_constructor","automated_fabricator","industrial_printer"):texture("vandorlabs:blocks/voxel_cores/"+name)
    catalog=json.loads((SOURCE/'data/frame_machines.json').read_text())
    for design in IDS:
        spec=next(v for v in catalog if v['design']==design)
        source=SOURCE/'models/block'/spec['model'];destination=assets/'models/block/builders'/design;destination.mkdir(parents=True,exist_ok=True)
        obj=(source/'complete.obj').read_bytes();(destination/'complete.obj').write_bytes(obj)
        lines=[]
        for line in (source/'materials.mtl').read_text().splitlines():
            if line.startswith('map_Kd '):line='map_Kd '+texture(line.split(None,1)[1])
            lines.append(line)
        (destination/'materials.mtl').write_text('\n'.join(lines)+'\n')
        write(destination/'complete.json',{'loader':'neoforge:obj','model':f'vandorlabs:models/block/builders/{design}/complete.obj','automatic_culling':False,'shade_quads':True,'flip_v':True,'emissive_ambient':False,'textures':{'particle':texture('vandorlabs:blocks/matter/'+design+'_core')}})
        core=json.loads((SOURCE/f'models/block/{design}_core.json').read_text());core['parent']='minecraft:block/cube_all';core['textures']={key:texture(value) for key,value in core['textures'].items()};write(destination/'core.json',core)
        item=json.loads((SOURCE/f'models/item/{design}_core.json').read_text());item['parent']=f'vandorlabs:block/builders/{design}/core';write(assets/f'models/item/{design}_core.json',item)
        write(assets/f'blockstates/{design}_core.json',{'variants':{f'facing={face},formed={str(formed).lower()}':{'model':'vandorlabs:block/builders/empty' if formed else f'vandorlabs:block/builders/{design}/core'} for face in ('north','east','south','west') for formed in (False,True)}})
        write(OUT/f'data/vandorlabs/loot_table/blocks/{design}_core.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'vandorlabs:{design}_core'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        manifest[design]={'width':spec['width'],'height':spec['height'],'depth':spec['depth'],'obj_sha256':hashlib.sha256(obj).hexdigest(),'model':f'vandorlabs:block/builders/{design}/complete'}
    write(assets/'models/block/builders/empty.json',{'parent':'minecraft:block/block','textures':{'particle':texture('vandorlabs:blocks/resource_ports/port_hull')}})
    write(assets/'blockstates/constructor_part.json',{'variants':{'':{'model':'vandorlabs:block/builders/empty'}}})
    # Dynamic fabrication uses the same exact RF/Item Port sprites as the static finished preview.
    for name in ('power_in_on','power_in_off','matter_in_off','matter_out_off'):texture('vandorlabs:blocks/resource_ports/'+name)
    write(assets/'data/native_builders.json',{'designs':manifest,'textures':textures,'shared':shared})
    # Fail unexpected overlaps across the complete generated tree and canonical resources.
    # Known shared textures above are omitted only after exact-byte validation.
    owners=[folder for folder in OUT.parent.iterdir() if folder.is_dir() and folder!=OUT]
    owners.append(Path(__file__).resolve().parent/'src/main/resources')
    for path in OUT.rglob('*'):
        if not path.is_file():continue
        relative=path.relative_to(OUT)
        for owner in owners:
            existing=owner/relative
            if existing.is_file():
                if existing.read_bytes()!=path.read_bytes():raise ValueError('Conflicting native builder resource: '+str(relative)+' owner='+owner.name)
                raise ValueError('Undeclared duplicate native builder resource: '+str(relative)+' owner='+owner.name)
    print('Native builder resources: exactly three complete structures, original sprite bytes and padded core items')
if __name__=='__main__':main()
