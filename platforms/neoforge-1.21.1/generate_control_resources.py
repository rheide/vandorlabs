"""Translate the canonical mounted-button/rocker artwork without an independent model catalog."""
import json
import shutil


def generate(root, resources):
    assets=resources/'assets/vandorlabs'
    def write(path,data):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(data,indent=2)+'\n')
    for family in ('push_button','rocker_switch'):
        states=json.loads((root/f'src/main/resources/assets/vandorlabs/blockstates/{family}.json').read_text())
        for variant in states['variants'].values():
            variant['model']=variant['model'].replace('vandorlabs:', 'vandorlabs:block/', 1)
        write(assets/f'blockstates/{family}.json',states)
        for source in (root/f'src/main/resources/assets/vandorlabs/models/block/{family}').glob('*.json'):
            model=json.loads(source.read_text())
            for key,value in model.get('textures',{}).items():
                if not value.startswith('vandorlabs:blocks/'):
                    raise ValueError(value)
                relative=value.removeprefix('vandorlabs:blocks/')
                candidates=[root/f'texture-packs/{pack}/assets/vandorlabs/textures/blocks/{relative}.png' for pack in ('default','additional')]+[root/f'src/main/resources/assets/vandorlabs/textures/blocks/{relative}.png']
                texture=next((p for p in candidates if p.exists()),None)
                if texture is None:raise FileNotFoundError(value)
                target=assets/f'textures/block/{relative}.png';target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(texture,target)
                model['textures'][key]=f'vandorlabs:block/{relative}'
            write(assets/f'models/block/{family}/{source.name}',model)
        write(assets/f'models/item/{family}.json',json.loads((root/f'src/main/resources/assets/vandorlabs/models/item/{family}.json').read_text()))
        recipe=json.loads((root/f'src/main/resources/assets/vandorlabs/recipes/{family}.json').read_text());recipe['result']['id']=recipe['result'].pop('item');write(resources/f'data/vandorlabs/recipe/{family}.json',recipe)
    for tag in ('mineable/pickaxe','needs_stone_tool'):
        path=resources/f'data/minecraft/tags/block/{tag}.json';data=json.loads(path.read_text());data['values']=list(dict.fromkeys(data['values']+['vandorlabs:push_button','vandorlabs:rocker_switch']));write(path,data)
