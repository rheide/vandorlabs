"""Prove generated-map pruning respects live references and native alias targets."""
import json
from prune_unused_runtime_assets import unused
files={
 'textures/blocks/dynmap/retired.png':'',
 'textures/blocks/dynmap/retired_full.png':'',
 'textures/blocks/dynmap/live.png':'',
 'textures/blocks/dynmap/native_shared.png':'',
 'textures/blocks/dynmap/model_shared.png':'',
 'textures/blocks/dynmap/retired_s.png':'',
 'textures/blocks/unknown_dynamic_art.png':'',
 'data/pbr_materials.json':json.dumps({'textures/blocks/dynmap/retired.png':{}}),
 'data/unified_textures.json':json.dumps([{'id':'retired'}]),
 'data/texture_aliases.json':json.dumps({'textures/blocks/dynmap/retired_full.png':'textures/blocks/dynmap/retired.png','textures/blocks/native.png':'textures/blocks/dynmap/native_shared.png'}),
 'models/block/compat.json':json.dumps({'textures':{'all':'vandorlabs:blocks/dynmap/model_shared'}}),
 'dynmap-texture.txt':'texture:id=alive,filename=assets/vandorlabs/textures/blocks/dynmap/live.png\ntexture:id=dead,filename=assets/vandorlabs/textures/blocks/dynmap/retired.png\nblock:id=%fixture,allfaces=0:alive\n'
}
assert set(unused(files))=={'textures/blocks/dynmap/retired.png','textures/blocks/dynmap/retired_full.png'}
files['dynmap-texture.txt']+='block:id=%fixture2,allfaces=0:dead\n'
assert set(unused(files))=={'textures/blocks/dynmap/retired_full.png'}
print('Unused artwork graph PASS live Dynmap IDs/model references/native alias targets retained; inventories and retired export aliases are not usage roots; companions and unknown dynamic art preserved')
