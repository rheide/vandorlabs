"""Native stationary seating reuses authored JSON/OBJ geometry and Source-sized artwork."""
import copy,json,shutil

KINDS=('programmable_chair','luxury_seat','military_seat','pilot_seat','chair_siderests')
STYLES=('command','companion','operator','conference','mess_hall')
PARTS=('single','left','middle','right')

def generate(root,out):
    source=root/'src/main/resources/assets/vandorlabs';assets=out/'assets/vandorlabs';textures=set();written=set()
    def write(relative,value):
        p=assets/relative;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,separators=(',',':'))+'\n')
    def texture(name):
        if name.startswith('#') or not name.startswith('vandorlabs:'):return name
        if name in textures:return name
        relative=name.split(':',1)[1];candidates=[root/f'texture-packs/{pack}/assets/vandorlabs/textures/{relative}.png' for pack in ('default','additional')]+[source/f'textures/{relative}.png']
        p=next((p for p in candidates if p.is_file()),None)
        if p is None:raise ValueError('Missing seating artwork '+name)
        target=assets/f'textures/{relative}.png';target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
        meta=p.with_suffix('.png.mcmeta')
        if meta.exists():shutil.copyfile(meta,target.with_suffix('.png.mcmeta'))
        textures.add(name);return name
    def model(name,offset=0):
        p=source/f'models/block/{name}.json';value=json.loads(p.read_text())
        if value.get('parent','').startswith('block/'):value['parent']='minecraft:'+value['parent']
        value['textures']={key:texture(value) for key,value in value.get('textures',{}).items()}
        if offset:
            for element in value.get('elements',[]):
                if 'rotation' in element:raise ValueError('Connected seat height needs authored vertex transform for rotated elements')
                for key in ('from','to'):
                    if element[key][1]>=5-1e-6:element[key][1]+=offset
        return value
    names=[]
    for style,name in enumerate(STYLES):
        for height,suffix in enumerate(('_low','','_high')):
            key=f'chair_{style}_{height}';write(f'models/block/native_seating/{key}.json',model('programmable_chair_'+name+suffix));names.append(key)
    for kind in ('luxury_seat','military_seat'):
        for height in range(3):
            for part,name in enumerate(PARTS):
                key=f'{kind}_{height}_{part}';write(f'models/block/native_seating/{key}.json',model('seating/'+kind+'_'+name,(height-1)*2+1));names.append(key)
    for height in range(4):
        for arms in range(4):
            name='pilot_seat'+(f'_height_{height}' if height else '')+(f'_arms_{arms}' if arms else '')
            path=source/f'models/block/{name}.obj';target=assets/f'models/block/native_seating/{name}.obj';target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,target)
            for line in path.read_text().splitlines():
                if line.startswith('mtllib '):
                    mtl=line.split(None,1)[1];shutil.copyfile(path.parent/mtl,target.parent/mtl)
                    for entry in (path.parent/mtl).read_text().splitlines():
                        if entry.startswith('map_Kd '):texture(entry.split(None,1)[1])
            key=f'pilot_{height}_{arms}';write(f'models/block/native_seating/{key}.json',{'loader':'neoforge:obj','model':f'vandorlabs:models/block/native_seating/{name}.obj','automatic_culling':False,'shade_quads':True,'flip_v':True,'emissive_ambient':False,'textures':{'particle':texture('vandorlabs:blocks/pilot_seat/fabric')}});names.append(key)
    advanced=source/'models/block/cockpits/chair_siderests'
    native=assets/'models/block/native_seating'
    for model,name in [('static','advanced_chair'),('slider_left','advanced_left'),('slider_right','advanced_right')]:
        (native/f'{name}.obj').write_text((advanced/f'{model}.obj').read_text().replace('mtllib materials.mtl','mtllib advanced_chair.mtl',1))
        write(f'models/block/native_seating/{name}.json',
              {'loader':'neoforge:obj','model':f'vandorlabs:models/block/native_seating/{name}.obj',
               'automatic_culling':False,'shade_quads':True,'flip_v':True,'emissive_ambient':False,
               'textures':{'particle':texture('vandorlabs:models/cockpits/sidewall')}})
    shutil.copyfile(advanced/'materials.mtl',native/'advanced_chair.mtl')
    for entry in (advanced/'materials.mtl').read_text().splitlines():
        if entry.startswith('map_Kd '):
            name=entry.split(None,1)[1]
            # The native Programmer resource set already packages this shared opaque sprite.
            if name!='vandorlabs:models/cockpits/blank':texture(name)
            else:textures.add(name)
    for name in ['assembly','rig']:
        asset=f'cockpits/chair_siderests/{name}.json'
        write(asset,json.loads((source/asset).read_text()))
    names.extend(['advanced_chair','advanced_left','advanced_right'])
    write('models/block/native_seating/empty.json',{'parent':'minecraft:block/block','textures':{'particle':texture('vandorlabs:blocks/bridge_chair_atlas')},'elements':[]})
    for kind in KINDS:
        base={'programmable_chair':'chair_0_1','pilot_seat':'pilot_0_0','chair_siderests':'advanced_chair'}.get(kind,kind+'_1_0')
        states={f'facing={face},upper={str(upper).lower()}':{'model':'vandorlabs:block/native_seating/'+('empty' if upper else base)} for face in ('north','south','west','east') for upper in (False,True)}
        if kind=='chair_siderests':
            states={f'facing={face},side={side},upper={str(upper).lower()}':
                    {'model':'vandorlabs:block/native_seating/'+('empty' if upper or side else base)}
                    for face in ('north','south','west','east') for upper in (False,True) for side in range(3)}
        write(f'blockstates/{kind}.json',{'variants':states})
        if kind=='programmable_chair':item=json.loads((source/'models/item/configured/programmable_chair_command.json').read_text())
        elif kind in ('pilot_seat','chair_siderests'):item={'display':json.loads((source/'blockstates/pilot_seat.json').read_text())['variants']['inventory'][0]['transform']}
        else:item=json.loads((source/f'models/item/{kind}.json').read_text())
        item['parent']='vandorlabs:block/native_seating/'+base
        for setting in item.get('display',{}).values():
            if isinstance(setting.get('scale'),(float,int)):setting['scale']=[setting['scale']]*3
        write(f'models/item/{kind}.json',item)
        recipe=json.loads((source/f'recipes/{kind}.json').read_text())
        for ingredient in recipe.get('key',{}).values():
            if ingredient.get('item')=='minecraft:wool':
                color={11:'blue',13:'green',14:'red'}.get(ingredient.pop('data',-1))
                if color is None:raise ValueError('Unsupported legacy seating wool ingredient')
                ingredient['item']='minecraft:'+color+'_wool'
            elif 'data' in ingredient:raise ValueError('Unsupported seating ingredient metadata')
        recipe['result']['id']=recipe['result'].pop('item');recipe['neoforge:conditions']=[{'type':'neoforge:item_exists','item':'vandorlabs:'+kind}]
        p=out/f'data/vandorlabs/recipe/{kind}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(recipe,indent=2)+'\n')
    yoke=source/'models/block/cockpits/yoke_screen'
    output=assets/'models/block/cockpits/yoke_screen'
    output.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(yoke/'off.obj',output/'off.obj')
    shutil.copyfile(yoke/'materials.mtl',output/'materials.mtl')
    for line in (yoke/'materials.mtl').read_text().splitlines():
        if line.startswith('map_Kd '):
            name=line.split(None,1)[1]
            if name!='vandorlabs:models/cockpits/blank':texture(name)
    write('models/block/cockpits/yoke_screen.json',
          {'loader':'neoforge:obj','model':'vandorlabs:models/block/cockpits/yoke_screen/off.obj',
           'automatic_culling':False,'shade_quads':True,'flip_v':True,'emissive_ambient':False,
           'textures':{'particle':texture('vandorlabs:models/cockpits/sidewall')}})
    write('blockstates/yoke_screen.json',{'variants':{
        'facing='+face:{'model':'vandorlabs:block/cockpits/yoke_screen','y':angle}
        for face,angle in (('north',0),('east',90),('south',180),('west',270))
    }})
    write('models/item/yoke_screen.json',{'parent':'vandorlabs:block/cockpits/yoke_screen',
        'display':{'gui':{'rotation':[30,225,0],'scale':[.62,.62,.62]},
                   'ground':{'translation':[0,3,0],'scale':[.3,.3,.3]},
                   'fixed':{'scale':[.6,.6,.6]},
                   'firstperson_righthand':{'rotation':[0,45,0],'scale':[.55,.55,.55]}}})
    for name in ['assembly','surfaces']:
        asset=f'cockpits/yoke_screen/{name}.json'
        write(asset,json.loads((source/asset).read_text()))
    recipe=json.loads((source/'recipes/yoke_screen.json').read_text())
    recipe['result']['id']=recipe['result'].pop('item')
    p=out/'data/vandorlabs/recipe/yoke_screen.json'
    p.write_text(json.dumps(recipe,indent=2)+'\n')
    write('data/native_seat_models.json',{'models':names})
    return [{'type':'minecraft:single','resource':name} for name in sorted(textures)]
