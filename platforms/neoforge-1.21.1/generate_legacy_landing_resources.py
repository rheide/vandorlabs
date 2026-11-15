"""Authored retired landing-gear meshes and exact Source collision cuboids; no recipes or new artwork."""
import copy,json,math,shutil
KINDS=('landing_gear','ski_landing_gear','landing_strut','angled_landing_strut')
SIZES=('small','medium','large','extra_large')
def generate(root,out):
    source=root/'src/main/resources/assets/vandorlabs';assets=out/'assets/vandorlabs';textures=set();names=[];geometry={}
    # Canonical arm, storage and frame generators already own these exact sprites.
    shared={f'vandorlabs:blocks/landing_gear/{name}' for name in ('metal','strut','panel','rubber','hub')}|{'vandorlabs:blocks/matter/casing','vandorlabs:frame-default/metal_side'}
    stale=[f'textures/blocks/landing_gear/{name}.png' for name in ('metal','strut','panel','rubber','hub')]+['textures/blocks/matter/casing.png','textures/blocks/programmable_glass/metal_side.png']
    for relative in stale:
        (assets/relative).unlink(missing_ok=True)
        (assets/(relative+'.mcmeta')).unlink(missing_ok=True)
    def write(path,value):
        target=assets/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(value,separators=(',',':'))+'\n')
    def texture(name):
        if name=='vandorlabs:blocks/programmable_glass/metal_side':return 'vandorlabs:frame-default/metal_side'
        if name in shared or name.startswith('#') or not name.startswith('vandorlabs:'):return name
        if name in textures:return name
        relative=name.split(':',1)[1]
        candidates=[root/f'texture-packs/{pack}/assets/vandorlabs/textures/{relative}.png' for pack in ('default','additional')]+[source/f'textures/{relative}.png']
        original=next((p for p in candidates if p.is_file()),None)
        if original is None:raise ValueError('Missing legacy landing artwork '+name)
        target=assets/f'textures/{relative}.png';target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(original,target);textures.add(name)
        if original.with_suffix('.png.mcmeta').exists():shutil.copyfile(original.with_suffix('.png.mcmeta'),target.with_suffix('.png.mcmeta'))
        return name
    def resolve(name,depth=0):
        if depth>16:raise ValueError('Cyclic legacy landing model inheritance')
        value=json.loads((source/f'models/{name}.json').read_text());parent=value.get('parent','')
        inherited={}
        if parent.startswith('vandorlabs:'):inherited=resolve(parent.split(':',1)[1],depth+1)
        result=copy.deepcopy(inherited);result.update({k:v for k,v in value.items() if k not in ('parent','textures')});result['textures']={**inherited.get('textures',{}),**value.get('textures',{})};result['parent']='minecraft:block/block'
        for key,name in list(result['textures'].items()):result['textures'][key]=texture(name)
        return result
    def box(element):
        lo=[v/16 for v in element['from']];hi=[v/16 for v in element['to']]
        if 'rotation' not in element:return lo+hi
        rotation=element['rotation'];origin=[v/16 for v in rotation['origin']];angle=math.radians(rotation['angle']);c=math.cos(angle);s=math.sin(angle);points=[]
        for x in (lo[0],hi[0]):
            for y in (lo[1],hi[1]):
                for z in (lo[2],hi[2]):
                    x-=origin[0];y-=origin[1];z-=origin[2]
                    if rotation['axis']=='x':q=(x,y*c-z*s,y*s+z*c)
                    elif rotation['axis']=='y':q=(x*c+z*s,y,-x*s+z*c)
                    else:q=(x*c-y*s,x*s+y*c,z)
                    points.append(tuple(q[i]+origin[i] for i in range(3)))
        return [min(p[i] for p in points) for i in range(3)]+[max(p[i] for p in points) for i in range(3)]
    for kind in KINDS:
        rows=[]
        for size in SIZES:
            groups=[]
            for group in ('fixed','wheel','piston'):
                name=f'{kind}_{size}_{group}';value=resolve('item/'+name);write(f'models/block/native_legacy_landing/{name}.json',value);names.append(name);groups.append([box(element) for element in value.get('elements',[])])
            if kind=='ski_landing_gear':
                for end in ('front','rear'):
                    name=f'{kind}_{size}_nose_{end}';write(f'models/block/native_legacy_landing/{name}.json',resolve('item/'+name));names.append(name)
            item=resolve('item/'+kind+'_'+size)
            for setting in item.get('display',{}).values():
                if isinstance(setting.get('scale'),(int,float)):setting['scale']=[setting['scale']]*3
            write(f'models/block/native_legacy_landing/{kind}_{size}_item.json',item);names.append(kind+'_'+size+'_item');rows.append(groups)
        geometry[kind]=rows
        variants={f'facing={face},lower={str(lower).lower()},extended={str(extended).lower()}':{'model':'vandorlabs:block/native_legacy_landing/empty'} for face in ('north','east','south','west') for lower in (False,True) for extended in (False,True)}
        write(f'blockstates/{kind}.json',{'variants':variants});write(f'models/item/{kind}.json',{'parent':f'vandorlabs:block/native_legacy_landing/{kind}_small_item'})
    write('models/block/native_legacy_landing/empty.json',{'parent':'minecraft:block/block','textures':{'particle':texture('vandorlabs:blocks/landing_gear/metal')},'elements':[]})
    texture('vandorlabs:blocks/programmable_glass/metal_side')
    write('data/native_legacy_landing_geometry.json',geometry);write('data/native_legacy_landing_models.json',{'models':names})
    return [{'type':'minecraft:single','resource':name} for name in sorted(textures)]
