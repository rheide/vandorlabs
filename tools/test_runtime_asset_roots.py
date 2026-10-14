"""An image is removable only when model, catalog, OBJ and Dynmap roots are absent."""
from prune_unused_runtime_assets import unused

files={
    'textures/blocks/quiet_unused.png':'',
    'textures/blocks/quiet_model.png':'',
    'textures/blocks/quiet_catalog.png':'',
    'textures/blocks/quiet_material.png':'',
    'textures/blocks/quiet_map.png':'',
    'models/block/example.json':'{"textures":{"face":"vandorlabs:blocks/quiet_model"}}',
    'data/example.json':'{"source":"quiet_catalog"}',
    'models/block/example.mtl':'map_Kd vandorlabs:blocks/quiet_material',
    'dynmap-texture.txt':'texture:id=audited_map,filename=assets/vandorlabs/textures/blocks/quiet_map.png,xcount=1,ycount=1\nblock:id=%example,state=*,patch0=0:audited_map\n',
}
assert unused(files)==['textures/blocks/quiet_unused.png']
files.pop('textures/blocks/quiet_unused.png')
assert unused(files)==[]
print('PASS: runtime resource pruning retains model, catalog, OBJ and live Dynmap roots')
