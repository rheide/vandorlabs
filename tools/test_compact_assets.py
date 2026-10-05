#!/usr/bin/env python3
"""Validate packaged model inheritance, migration resources and reduced image dimensions."""
import io,json,sys
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
from compact_runtime_assets import redirect
from prune_retired_textures import retired,remap_model
ROOT=Path(__file__).resolve().parents[1]
jar=Path(sys.argv[1]);prefix='assets/vandorlabs/'
with ZipFile(jar) as archive:
    names=set(archive.namelist())
    models={name:json.loads(archive.read(name)) for name in names if name.startswith(prefix+'models/') and name.endswith('.json')}
    entries=json.loads(archive.read(prefix+'data/unified_textures.json'))
    aliases={'vandorlabs:blocks/unified/'+e['id']:'vandorlabs:blocks/unified/'+e['alias'] for e in entries if e.get('alias')}
    paths=retired()
    def expanded(model):
        parent=model.get('parent','')
        if parent.startswith('vandorlabs:block/runtime_shared/'):
            base=models[prefix+'models/'+parent.split(':',1)[1]+'.json'].copy();base.update({k:v for k,v in model.items() if k!='parent'});return base
        return model
    checked=0
    # Equality includes every face, UV, bounds, shade flag, display transform and texture binding.
    sources={}
    for directory in [ROOT/'src/main/resources/assets/vandorlabs/models',ROOT/'generated-resources/assets/vandorlabs/models']:
        for file in directory.rglob('*.json'):sources[file.relative_to(directory).as_posix()]=file
    for relative,file in sources.items():
        if 'high' in Path(relative).parts:continue
        expected=redirect(json.loads(file.read_text()),aliases)
        expected=json.loads(remap_model(json.dumps(expected),paths))
        actual=expanded(models[prefix+'models/'+relative]);assert actual==expected,relative;checked+=1
    for name,model in models.items():
        parent=model.get('parent','')
        if parent.startswith('vandorlabs:'):assert prefix+'models/'+parent.split(':',1)[1]+'.json' in names,(name,parent)
        for texture in model.get('textures',{}).values():
            if texture.startswith('vandorlabs:') and ':blocks/unified/' not in texture:
                assert prefix+'textures/'+texture.split(':',1)[1]+'.png' in names,(name,texture)
    def references(value,name):
        if isinstance(value,dict):
            model=value.get('model')
            if isinstance(model,str) and model.startswith('vandorlabs:'):
                rel=model.split(':',1)[1];suffix='' if rel.endswith('.obj') else '.json'
                assert any(prefix+'models/'+start+rel+suffix in names for start in ['block/','']),(name,model)
            for child in value.values():references(child,name)
        elif isinstance(value,list):
            for child in value:references(child,name)
    for name in names:
        if name.startswith(prefix+'blockstates/') and name.endswith('.json'):references(json.loads(archive.read(name)),name)
        if name.endswith('.png'):
            image=Image.open(io.BytesIO(archive.read(name)))
            if '/textures/items/' in name:assert image.size==(128,128),name
            if '/imported/trapdoors/' in name:assert image.size==((128,128) if name.endswith('_small.png') else (256,256)),name
        assert not any(path in name for path in ['/textures/blocks/space_doors/high/','/textures/blocks/cargo_doors/high/','/models/block/detailed_doors/high/','/models/item/detailed_doors/high/'])
    pane=lambda tier:Image.open(io.BytesIO(archive.read(prefix+'textures/blocks/space_doors/'+tier+'/glass_tile.png'))).convert('RGBA')
    small,large=pane('low'),pane('medium');assert small.size==(256,256) and large.size==(512,512)
    expected=large.convert('RGBa').resize((256,256),Image.Resampling.LANCZOS).convert('RGBA')
    assert small.tobytes()==expected.tobytes(),'Pane pattern/UV density differs across sizes'
    assert sum(e.get('hidden',False) and 'alias' in e for e in entries)==29
print('PASS:',checked,'packaged models preserve geometry/UVs/materials after shared-parent expansion; references resolve, High resources absent, hatch/icon sizes and matched pane pattern verified')
