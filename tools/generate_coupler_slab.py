#!/usr/bin/env python3
"""Close duplicate coupler front faces and derive UV-faithful half-height native models."""
import argparse,copy,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'

def output(path,data,check):
    content=json.dumps(data,indent=2)+'\n'
    if check:assert path.read_text()==content,'Stale coupler model '+str(path.relative_to(ROOT))
    else:path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)

def flatten(model,top):
    result=copy.deepcopy(model)
    for element in result['elements']:
        for key in ('from','to'):element[key][1]=element[key][1]*.5+(8 if top else 0)
        for direction,face in element['faces'].items():
            if direction in ('up','down'):continue
            # The physical vertical span halves; select half of its original UV
            # region instead of compressing the source pixels vertically.
            axis=0 if face.get('rotation',0)%180 else 1
            lo,hi=face['uv'][axis],face['uv'][axis+2];center=(lo+hi)/2
            face['uv'][axis]=center+(lo-center)*.5;face['uv'][axis+2]=center+(hi-center)*.5
    return result

def generate(check=False):
    models=ASSETS/'models/block';base=json.loads((models/'square_coupler_base.json').read_text());heads=[]
    for state in ('off','on'):
        path=models/f'square_coupler_head_{state}.json';head=json.loads(path.read_text())
        for element in head['elements']:
            if element['name']=='sliding_shaft':element['faces'].pop('north',None)
        output(path,head,check);heads.append(head)
    endpoints=[]
    for locked,head in zip((False,True),heads):
        model=copy.deepcopy(head);moving=copy.deepcopy(head['elements'])
        if locked:
            for element in moving:
                for key in ('from','to'):element[key][2]-=4
        model['elements']=copy.deepcopy(base['elements'])+moving;endpoints.append(model)
        output(models/('square_coupler_'+('locked' if locked else 'released')+'.json'),model,check)
    for top in (False,True):
        half='top' if top else 'bottom'
        for name,model in [('base',base),('head_off',heads[0]),('head_on',heads[1]),('released',endpoints[0]),('locked',endpoints[1])]:output(models/f'vehicle_coupler_slab_{name}_{half}.json',flatten(model,top),check)
    output(ASSETS/'models/item/square_coupler.json',{'parent':'vandorlabs:block/square_coupler_released','display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.6,.6,.6]}}},check)
    variants={}
    for face,yaw in [('north',0),('east',90),('south',180),('west',270),('up',0),('down',0)]:
        for half in ('bottom','top'):
            for locked in (False,True):variants[f'facing={face},half={half},locked={str(locked).lower()}']={'model':f'vandorlabs:vehicle_coupler_slab_{"locked" if locked else "released"}_{half}','y':yaw}
    output(ASSETS/'blockstates/vehicle_coupler_slab.json',{'variants':variants},check)
    output(ASSETS/'models/item/vehicle_coupler_slab.json',{'parent':'vandorlabs:block/vehicle_coupler_slab_released_bottom','display':{'gui':{'rotation':[30,225,0],'translation':[0,2,0],'scale':[.65,.65,.65]}}},check)
    print('Coupler native models PASS hidden duplicate front removed; slab24states native cropped vertical UVs and padded item')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');generate(parser.parse_args().check)
