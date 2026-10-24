"""Refreshing staged icons must not make shared geometry inherit itself."""
import json
import tempfile
from pathlib import Path
from PIL import Image
from compact_runtime_assets import stage

with tempfile.TemporaryDirectory() as temporary:
    root=Path(temporary)
    for folder in ['models/block','data','textures/blocks/space_doors/medium','textures/blocks/space_doors/low']:
        (root/folder).mkdir(parents=True,exist_ok=True)
    (root/'data/unified_textures.json').write_text('[]')
    Image.new('RGBA',(512,512),(100,180,220,128)).save(root/'textures/blocks/space_doors/medium/glass_tile.png')
    (root/'models/block/packed').mkdir()
    (root/'models/block/packed/materials.mtl').write_text('map_Kd vandorlabs:blocks/packed_surface\n')
    Image.new('RGBA',(256,256),(80,120,160,255)).save(root/'textures/blocks/packed_surface.png')
    original={'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{'up':{'texture':'#surface'}}}], 'textures':{'surface':'minecraft:blocks/iron_block'}}
    model=root/'models/block/test.json';model.write_text(json.dumps(original))
    stage(root,True)
    first={p.relative_to(root/'models').as_posix():json.loads(p.read_text()) for p in (root/'models').rglob('*.json')}
    stage(root,True)
    second={p.relative_to(root/'models').as_posix():json.loads(p.read_text()) for p in (root/'models').rglob('*.json')}
    assert first==second,'Restaging changed shared model inheritance'
    assert Image.open(root/'textures/blocks/packed_surface.png').size==(256,256),'Packed OBJ atlas was mistaken for one block face'
    assert Image.open(root/'textures/blocks/space_doors/medium/glass_tile.png').size==(128,128),'Ordinary block artwork was not compacted'
    inherited=second['block/test.json']['parent'].split(':',1)[1]+'.json'
    assert second[inherited]['elements']==original['elements'] and 'parent' not in second[inherited]
print('PASS: repeated staged-model compaction preserves geometry and acyclic inheritance')
