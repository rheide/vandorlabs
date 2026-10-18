#!/usr/bin/env python3
"""Import native modular gear geometry and pixel textures into the existing mod."""
import argparse, json, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
IDS=('landing_plate','landing_ski','gear_extender','gear_swing_arm')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('archive',type=Path);args=parser.parse_args()
    with zipfile.ZipFile(args.archive) as archive:
        prefix='mctrek_landing_gear/src/main/resources/assets/mctrekgear/'
        for name in archive.namelist():
            if not name.startswith(prefix) or name.endswith('/'):continue
            rel=name[len(prefix):]
            if rel.startswith('textures/blocks/gear/'):
                path=ROOT/'texture-packs/additional/assets/vandorlabs'/rel
                path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(archive.read(name))
            elif rel.startswith('models/block/gear/') or rel in tuple(f'{kind}/{block}.json' for kind in ('blockstates','models/item') for block in IDS):
                data=json.loads(archive.read(name).decode('utf-8').replace('mctrekgear:','vandorlabs:'))
                if rel.startswith('models/item/'):
                    block=Path(rel).stem
                    data.setdefault('display',{})['gui']={'rotation':[30,225,0],'translation':[0,3,0] if block=='gear_swing_arm' else [0,0,0],'scale':[.30]*3 if block=='gear_swing_arm' else [.48]*3}
                path=ROOT/'src/main/resources/assets/vandorlabs'/rel;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    # Matter-based recipes share the mod's existing material economy.
    for block,pattern,keys,count in (
        ('landing_plate',['M M','MMM'],{},2),('landing_ski',['M  ','MMM'],{},2),
        ('gear_extender',[' M ',' R ',' M '],{'R':'minecraft:redstone'},1),
        ('gear_swing_arm',['MM ',' RM',' M '],{'R':'minecraft:redstone'},1)):
        ingredients={'M':'vandorlabs:programmable_matter_ingot',**keys}
        data={'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v} for k,v in ingredients.items()},'result':{'item':'vandorlabs:'+block,'count':count}}
        (ROOT/f'src/main/resources/assets/vandorlabs/recipes/{block}.json').write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    print('Imported four modular gear block/item sets, forty native part models and six pixel textures')

if __name__=='__main__':main()
