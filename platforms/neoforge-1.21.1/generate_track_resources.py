"""Authored track geometry is shared verbatim; dynamic selection uses the portable mesh policy."""
import json,shutil

def generate(root,out):
    source=root/'src/main/resources/assets/vandorlabs'
    def write(path,value):
        target=out/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(value,indent=2)+'\n')
    for file in (source/'models/block/track').glob('*.json'):
        value=json.loads(file.read_text());write('assets/vandorlabs/models/block/track/'+file.name,value)
    for name in ('compact_track','armoured_track','bogie_track'):
        value=json.loads((source/f'models/block/{name}.json').read_text());write(f'assets/vandorlabs/models/block/{name}.json',value)
        write(f'assets/vandorlabs/blockstates/{name}.json',{'variants':{'':{'model':'vandorlabs:block/gear/empty'}}})
        write(f'assets/vandorlabs/models/item/{name}.json',json.loads((source/f'models/item/{name}.json').read_text()))
    recipe=json.loads((source/'recipes/compact_track.json').read_text());recipe['result']['id']=recipe['result'].pop('item');recipe['neoforge:conditions']=[{'type':'neoforge:item_exists','item':'vandorlabs:compact_track'}];write('data/vandorlabs/recipe/compact_track.json',recipe)
    atlas=[];seen=set()
    for pack in ('additional','default'):
        directory=root/f'texture-packs/{pack}/assets/vandorlabs/textures/blocks/track'
        if not directory.is_dir():continue
        for file in directory.glob('*'):
            target=out/'assets/vandorlabs/textures/blocks/track'/file.name;target.parent.mkdir(parents=True,exist_ok=True)
            if file.name in seen:continue
            seen.add(file.name)
            shutil.copy2(file,target)
            if file.suffix=='.png':atlas.append({'type':'minecraft:single','resource':'vandorlabs:blocks/track/'+file.stem})
    return atlas
