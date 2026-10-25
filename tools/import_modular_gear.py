#!/usr/bin/env python3
"""Import native modular gear geometry and pixel textures into the existing mod."""
import argparse, copy, json, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
IDS=('landing_plate','landing_ski','gear_extender','gear_swing_arm')

def correct_swing_model(data):
    # Native bars bow south; folding north would push the elbow into the level
    # foot. Turn just the moving linkage around its Y axis, keeping the housing.
    for element in data.get('elements', []):
        if element.get('name') == 'lower_swivel_housing':
            element['faces']['down'] = copy.deepcopy(element['faces']['up'])
        if element.get('name') not in ('upper_arm', 'lower_arm', 'elbow_cover'):
            continue
        lo, hi = element['from'][:], element['to'][:]
        for axis in (0, 2):
            element['from'][axis], element['to'][axis] = 16-hi[axis], 16-lo[axis]
        rotation = element.get('rotation')
        if rotation:
            for axis in (0, 2): rotation['origin'][axis] = 16-rotation['origin'][axis]
            if rotation['axis'] in ('x', 'z'): rotation['angle'] = -rotation['angle']
        opposite = {'north':'south', 'south':'north', 'east':'west', 'west':'east'}
        element['faces'] = {opposite.get(face,face): value for face,value in element['faces'].items()}
        for face in ('up','down'):
            if face in element['faces']:
                entry=element['faces'][face]; entry['rotation']=(entry.get('rotation',0)+180)%360
    return data

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
                if rel in ('models/block/gear/swing_linkage.json','models/block/gear/swing_arm.json','models/block/gear/swivel.json'): correct_swing_model(data)
                if rel=='models/block/gear/socket.json':
                    # Keep the moving socket's side faces inside the sleeve collar.
                    for element in data['elements']:
                        element['faces']['down']=copy.deepcopy(element['faces']['up'])
                        for axis in (0,2):
                            element['from'][axis] += .25
                            element['to'][axis] -= .25
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
