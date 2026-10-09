"""Replace spacecraft assets with full-detail models and one machinery size/tier."""
import argparse
import ast
import copy
import json
import math
import shutil
from collections import defaultdict
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from kit_textures import import_textures
from machine_accents import apply_accents
from planar_surfaces import clean, overlap_count

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT/'src/main/resources/assets/vandorlabs'
TEXTURES = ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'
SETS = [('canopy-kit','canopykit','canopy_meshes.json','canopy'),
        ('ship-systems-kit','shipsystems','ship_system_meshes.json','ship_systems/systems'),
        ('vektor-hadron-kit','vhtech','vh_system_meshes.json','ship_systems/vhtech'),
        ('rivet-dynamics-kit','rivet','rivet_system_meshes.json','ship_systems/rivet'),
        ('external-sensors-kit','shipsensors','external_sensor_meshes.json','ship_systems/external')]


def collisions(model):
    parts = defaultdict(list)
    for f in model['faces']:
        if f['base_material'] == 'glass':
            continue
        parts[f['part']].extend(f['v'])
    boxes = [[min(v[i] for v in vs) for i in range(3)]+[max(v[i] for v in vs) for i in range(3)]
             for vs in parts.values()]
    boxes = [b for b in boxes if all(b[i+3]>b[i] for i in range(3))]
    for f in model['faces']:
        if f['base_material'] != 'glass':
            continue
        low = [min(v[i] for v in f['v']) for i in range(3)]
        high = [max(v[i] for v in f['v']) for i in range(3)]
        for i in range(3):
            if low[i] == high[i]:
                low[i] = max(0,low[i]-1/128)
                high[i] = min(model['occupancy_xyz'][i],high[i]+1/128)
        boxes.append(low+high)
    return boxes


def extend_consoles(models, source, staging):
    staging.mkdir(parents=True, exist_ok=True)
    for p in (source/'vektor-hadron-kit/forge/resources/assets/vhtech/textures/blocks').glob('*.png'):
        shutil.copyfile(p,staging/p.name)
    tree = ast.parse((source/'source/build_collection.py').read_text(encoding='utf-8'))
    nodes = [n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='palette'
             or isinstance(n,ast.ClassDef) and n.name=='Surfaces'
             or isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id in ('HUES','BRANDS') for t in n.targets)]
    yy,xx = np.mgrid[:256,:256]
    scope = {'np':np,'Image':Image,'ImageDraw':ImageDraw,'math':math,
             '_NOISE':(((xx//2*73856093)^(yy//2*19349663)^((xx//2)*(yy//2)*83492791))%9)-4}
    exec(compile(ast.Module(body=nodes,type_ignores=[]),'<console surfaces>','exec'),scope)
    painter = scope['Surfaces'](ROOT/'build/full-detail-console-art','vhtech')
    generated=[]
    for base in models:
        if base.get('manufacturer')!='vektor' or not base.get('console_width'):
            continue
        for depth in (2,3):
            m=copy.deepcopy(base)
            width=m['console_width']
            m['stem']=f'vektor_console_{width}_depth_{depth}'
            m['id']=m['stem']+('_on' if m['active'] else '_off')
            m.pop('block_id',None)
            m['console_depth']=depth
            m['occupancy_xyz']=m['bounds_xyz']=[width,1,depth]
            def stretch(z):
                return z if z<=.25 else z+depth-1 if z>=.75 else .25+(z-.25)*(depth-.5)/.5
            faces=[]
            for f in m['faces']:
                vs=[[x,y,stretch(z)] for x,y,z in f['v']]
                # Triangles remain planar after the piecewise depth extension.
                for i in range(1,len(vs)-1):
                    tri=[vs[0],vs[i],vs[i+1]]
                    a,b=np.array(tri[1])-tri[0],np.array(tri[2])-tri[0]
                    normal=np.cross(a,b);length=np.linalg.norm(normal)
                    if length<1e-9:continue
                    face=dict(f,v=tri,normal=(normal/length).tolist(),material=f['base_material'].removesuffix('_off'))
                    faces.append(face)
            m['faces']=faces
            painter.apply(faces,'vektor','console',m['active'])
            m['collision']=collisions(m)
            generated.append(m)
    for p in painter.dirs[1].glob('*.png'):
        shutil.copyfile(p,staging/p.name)
    models.extend(generated)
    return staging


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('collection',type=Path)
    source=parser.parse_args().collection
    old=[]
    for _,_,catalog,_ in SETS:
        if catalog!='canopy_meshes.json':old+=json.loads((ASSETS/'data'/catalog).read_text())
    reference=json.loads((ASSETS/'data/rivet_reference_meshes.json').read_text())
    old+=reference
    old_ids={m['block_id'] for m in old if 'block_id' in m}
    reference=[m for m in reference if '_large' in m['stem']]
    preserved={f['material'] for m in reference for f in m['faces']}
    backup=ROOT/'build/full-detail-reference-textures'
    for name in preserved:
        p=backup/(name+'.png');p.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(TEXTURES/'ship_systems'/(name+'.png'),p)
    for name in old_ids:
        for folder in ('blockstates','models/item','recipes'):
            (ASSETS/folder/(name+'.json')).unlink(missing_ok=True)
    for folder in ('canopy','ship_systems'):
        target=(TEXTURES/folder).resolve()
        assert target.is_relative_to(ROOT.resolve()) and target.parent==TEXTURES.resolve()
        shutil.rmtree(target)
        target.mkdir()
    for name in preserved:
        p=TEXTURES/'ship_systems'/(name+'.png');p.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(backup/(name+'.png'),p)
    items=[]
    for kit,domain,catalog,destination in SETS:
        models=json.loads((source/kit/'model_catalog.json').read_text())
        if kit!='canopy-kit':
            models=[m for m in models if '_small' not in m['stem'] and '_g32' not in m['stem']]
            for m in models:
                m.pop('block_id',None)
                m['collision']=collisions(m)
                if kit=='external-sensors-kit':
                    for f in m['faces']:
                        f['v']=[[x,1-z,y] for x,y,z in f['v']]
                        nx,ny,nz=f['normal'];f['normal']=[nx,-nz,ny]
                    m['collision']=[[x0,1-z1,y0,x1,1-z0,y1] for x0,y0,z0,x1,y1,z1 in m['collision']]
                    m['occupancy_xyz']=m['bounds_xyz']=[2,1,2]
                    m['category']='analysis'
                m['footprint']=[m['occupancy_xyz'][0],m['occupancy_xyz'][2]]
                if not m['active']:
                    if m.get('console_width',0)>1:continue
                    m['block_id']=m['manufacturer']+'_console' if m.get('console_width') else m['stem']
                    items.append(m)
        texture_source=source/kit/f'forge/resources/assets/{domain}/textures/blocks'
        if kit=='vektor-hadron-kit':
            texture_source=extend_consoles(models,source,ROOT/'build/full-detail-console-textures')
        clipped=clean(models)
        assert overlap_count(models)==0
        target=TEXTURES/destination
        import_textures(models,texture_source,target)
        if kit not in ('canopy-kit','rivet-dynamics-kit'):
            apply_accents(models,target)
        prefix=destination.removeprefix('ship_systems/')+'/' if kit!='canopy-kit' else ''
        for m in models:
            for f in m['faces']:f['material']=prefix+f['material']
        (ASSETS/'data'/catalog).write_text(json.dumps(models,separators=(',',':'))+'\n',encoding='utf-8')
        print(kit,len(models),'meshes;',clipped,'coplanar overlaps clipped')
    items += [m for m in reference if not m['active']]
    (ASSETS/'data/rivet_reference_meshes.json').write_text(json.dumps(reference,separators=(',',':'))+'\n',encoding='utf-8')
    lang=ROOT/'generated-resources/assets/vandorlabs/lang/en_us.lang'
    lines=[line for line in lang.read_text(encoding='utf-8').splitlines()
           if not any(line.startswith('tile.'+name+'.') for name in old_ids)]
    for index,m in enumerate(items):
        name=m['block_id']
        for folder,data in [('blockstates',{'variants':{'normal':{'model':'vandorlabs:ship_system_empty'}}}),('models/item',{'parent':'block/block'})]:
            (ASSETS/folder/(name+'.json')).write_text(json.dumps(data)+'\n',encoding='utf-8')
        # Unique direct recipes replace all removed size/tier upgrade chains.
        ingredients=[{'item':'vandorlabs:programmable_matter_ingot'},{'item':'minecraft:quartz'},
                     {'item':'minecraft:dye','data':index%16}]
        ingredients += [{'item':'minecraft:iron_ingot'}]*(index//16+1)
        (ASSETS/'recipes'/(name+'.json')).write_text(json.dumps({'type':'minecraft:crafting_shapeless','ingredients':ingredients,'result':{'item':'vandorlabs:'+name}})+'\n',encoding='utf-8')
        label=m['label'].replace(' · OFF','').replace(' · ',' ').replace('Large','').replace('large','').replace('G64','').strip()
        if name.startswith('rivet_ref_'):label='Rivet Dynamics Reference '+m['category'].title()
        elif name.startswith('rivet_'):label='Rivet Dynamics '+label
        if m.get('console_width'):label=m['manufacturer'].capitalize()+' Navigation Console'
        lines.append('tile.'+name+'.name='+' '.join(label.split()))
    lang.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    used=set()
    for _,_,catalog,_ in SETS:
        if catalog=='canopy_meshes.json':continue
        used.update(f['material']+'.png' for m in json.loads((ASSETS/'data'/catalog).read_text()) for f in m['faces'])
    used.update(f['material']+'.png' for m in reference for f in m['faces'])
    for p in (TEXTURES/'ship_systems').rglob('*.png'):
        if p.relative_to(TEXTURES/'ship_systems').as_posix() not in used:p.unlink()
    print('Registered',len(items),'single-size machinery items; removed obsolete variants and textures.')


if __name__=='__main__':main()
