"""Canonical Copy Controller artwork and padded Source item; registration remains independently gated."""
import json
import shutil


def generate(root,out):
    source=root/'src/main/resources/assets/vandorlabs'
    assets=out/'assets/vandorlabs'
    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,indent=2)+'\n')
    write(assets/'models/block/copy_controller.json',json.loads((source/'models/block/copy_controller.json').read_text()))
    write(assets/'models/item/copy_controller.json',json.loads((source/'models/item/copy_controller.json').read_text()))
    # The Native listed active value has no effect on this Source housing; output Surface artwork conveys copying.
    facing={'north':0,'east':90,'south':180,'west':270}
    variants={f'facing={face},active={active}':dict(model='vandorlabs:block/copy_controller',y=angle) for face,angle in facing.items() for active in ('false','true')}
    write(assets/'blockstates/copy_controller.json',{'variants':variants})
    recipe=json.loads((source/'recipes/copy_controller.json').read_text())
    recipe['result']['id']=recipe['result'].pop('item')
    recipe['neoforge:conditions']=[{'type':'neoforge:item_exists','item':'vandorlabs:copy_controller'}]
    write(out/'data/vandorlabs/recipe/copy_controller.json',recipe)
    atlas=[]
    for path in (root/'texture-packs/additional/assets/vandorlabs/textures/blocks/copy_controller').glob('*.png'):
        target=assets/'textures/blocks/copy_controller'/path.name
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(path,target)
        atlas.append({'type':'minecraft:single','resource':'vandorlabs:blocks/copy_controller/'+path.stem})
    return atlas
