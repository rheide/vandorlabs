#!/usr/bin/env python3
"""Render a saved programmable-block fixture with a real, isolated Forge/Dynmap server."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import socket
import subprocess
import tempfile
import time
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from test_artifacts import ArtifactRun
from zipfile import ZipFile

def validate_render(output):
    from PIL import Image
    import json
    for phase in ('prepare','render'):
        text=(output/(phase+'.log')).read_text(errors='replace')
        for error in ('Format error','not enough textures','Invalid modellist','Invalid texture block name','Error loading custom renderer'):
            if error in text:raise AssertionError(f'{error}: {output}/{phase}.log')
    labels=json.loads((output/'fixtures.json').read_text())
    validate_fixture_matrix(labels)
    text=(output/'render.log').read_text(errors='replace')
    extra_placements=0 if all(v['block']=='module_rack' or v.get('frame_fixture') or v.get('appliance_fixture') or v.get('surface_fixture') for v in labels) else 4
    if text.count('Block placed')<len(labels)+extra_placements:raise AssertionError('Incomplete block fixture')
    files=[p for p in (output/'dynmap/web/tiles/world').rglob('*.png') if 'fixture' in str(p)]
    if not files:raise AssertionError('Dynmap produced no fixture PNG tiles')
    if labels and all(v["block"]=="module_rack" for v in labels):
        validate_battery_render(output,labels)
    elif labels and all(v.get("surface_fixture") for v in labels):
        validate_surface_render(output,labels)
    else:
        richest=0
        for path in files:
            if path.name.startswith('z'):continue
            with Image.open(path) as image:richest=max(richest,len(image.convert('RGB').getcolors(1000000) or []))
        if richest<300:raise AssertionError('Map contains no sufficiently detailed textured fixture tile')
    if labels and all(v.get('frame_fixture') for v in labels):validate_core_render(output,labels)
    return len(files)

def validate_surface_render(output,labels):
    """Check the bright and dark landmarks of the asymmetric Copier Input artwork."""
    import json
    import math
    from PIL import Image
    config=json.loads((output/'dynmap/web/standalone/dynmap_config.json').read_text())
    world=next(v for v in config['worlds'] if v['name']=='world')
    mapping=next(v for v in world['maps'] if v['name']=='fixture')
    matrix=mapping['worldtomap']
    tiles={}
    def sample(fixture,u,v):
        x,y,z=fixture['x']+u,fixture['y']+1,fixture['z']+v
        px=math.floor(matrix[0]*x+matrix[1]*y+matrix[2]*z)
        py=math.floor(matrix[3]*x+matrix[4]*y+matrix[5]*z)
        tx,ty=px//128,py//128
        if (tx,ty) not in tiles:
            path=output/f'dynmap/web/tiles/world/fixture/{tx>>5}_{ty>>5}/{tx}_{ty}.png'
            with Image.open(path) as image:tiles[tx,ty]=image.convert('RGB')
        return sum(tiles[tx,ty].getpixel((px%128,127-py%128)))
    def similarity(a,b):
        mean_a,mean_b=sum(a)/len(a),sum(b)/len(b)
        da=[value-mean_a for value in a]
        db=[value-mean_b for value in b]
        length=math.sqrt(sum(value*value for value in da)*sum(value*value for value in db))
        return sum(x*y for x,y in zip(da,db))/length if length else 0
    from io import BytesIO
    with ZipFile(next((output/'mods').glob('vandorlabs-*.jar'))) as archive:
        source=Image.open(BytesIO(archive.read('assets/vandorlabs/textures/blocks/surface/copier-input.png'))).convert('RGB').resize((32,32))
    interior=[(u,v) for u in range(4,28) for v in range(4,28)]
    expected=[sum(source.getpixel((u,v))) for u,v in interior]
    flipped=[sum(source.getpixel((u,31-v))) for u,v in interior]
    for fixture in labels:
        actual=[sample(fixture,(u+.5)/32,(v+.5)/32) for u,v in interior]
        normal,reverse=similarity(expected,actual),similarity(flipped,actual)
        assert normal>.5 and normal>reverse+.12,f'Surface artwork vertically flipped or missing: {normal:.2f}/{reverse:.2f}'
    print('Dynmap Surface artwork orientation PASS',flush=True)


def core_projection_reference(output, jar, java):
    """Use this candidate's pure Core geometry, independently of Dynmap patches."""
    import json,subprocess,hashlib,os
    source=Path(__file__).resolve().parent/'java/com/vandorlabs/dynmap/CoreProjectionOracle.java'
    classes=output/'core-projection-classes';classes.mkdir(exist_ok=True)
    subprocess.run([str(Path(java).with_name('javac'+Path(java).suffix)),'-cp',str(jar.resolve()),'-d',str(classes),str(source)],check=True,capture_output=True)
    raw=subprocess.check_output([str(java),'-cp',str(classes)+os.pathsep+str(jar.resolve()),'com.vandorlabs.dynmap.CoreProjectionOracle'],text=True)
    with ZipFile(jar) as archive:catalog=json.loads(archive.read('assets/vandorlabs/data/dynmap_frame_models.json'))
    value={'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'meshes':json.loads(raw),'blocks':{k:v['core'] for k,v in catalog['designs'].items()}}
    (output/'core-projection-reference.json').write_text(json.dumps(value))

def validate_core_render(output,labels):
    """Real map pixels must show colored open scaffolds, not full seed cubes."""
    import json,math
    from PIL import Image,ImageDraw
    reference=json.loads((output/'core-projection-reference.json').read_text())
    config=json.loads((output/'dynmap/web/standalone/dynmap_config.json').read_text())
    world=next(v for v in config['worlds'] if v['name']=='world')
    mapping=next(v for v in world['maps'] if v['name']=='core_fixture')
    matrix=mapping['worldtomap'];prefix=mapping['prefix'];tiles={};records=[]
    sheet=Image.new('RGB',(8*112,7*116),(20,24,32));draw=ImageDraw.Draw(sheet)
    def project(x,y,z):return matrix[0]*x+matrix[1]*y+matrix[2]*z,matrix[3]*x+matrix[4]*y+matrix[5]*z
    def cross(o,a,b):return (a[0]-o[0])*(b[1]-o[1])-(a[1]-o[1])*(b[0]-o[0])
    def hull(points):
        points=sorted(set(points));lower=[];upper=[]
        for p in points:
            while len(lower)>1 and cross(lower[-2],lower[-1],p)<=0:lower.pop()
            lower.append(p)
        for p in reversed(points):
            while len(upper)>1 and cross(upper[-2],upper[-1],p)<=0:upper.pop()
            upper.append(p)
        return lower[:-1]+upper[:-1]
    def pixel(u,v):
        tx,ty=u//128,v//128;key=tx,ty
        if key not in tiles:
            path=output/f'dynmap/web/tiles/world/{prefix}/{tx>>5}_{ty>>5}/{tx}_{ty}.png'
            if not path.is_file():raise AssertionError('Missing Core closeup tile '+str(path))
            with Image.open(path) as image:tiles[key]=image.convert('RGB')
        return tiles[key].getpixel((u%128,127-v%128))
    for seed in (v for v in labels if v.get('core_seed')):
        x,y,z=(seed[k] for k in ('x','y','z'))
        polygon=hull([project(x+a,y+b,z+c) for a in (.108,.892) for b in (.108,.892) for c in (.108,.892)])
        left,right=math.floor(min(p[0] for p in polygon)),math.ceil(max(p[0] for p in polygon))
        bottom,top=math.floor(min(p[1] for p in polygon)),math.ceil(max(p[1] for p in polygon))
        occupied=Image.new('L',(right-left+5,top-bottom+5),0);occupied_draw=ImageDraw.Draw(occupied)
        core=reference['blocks'][seed['block']]
        for face in reference['meshes'][core]:
            occupied_draw.polygon([(project(x+p[0],y+p[1],z+p[2])[0]-left+2,top-project(x+p[0],y+p[1],z+p[2])[1]+2) for p in face],fill=255)
        # Keep ray boundaries out of the comparison to allow pixel rounding and filtering.
        from PIL import ImageFilter
        occupied=occupied.filter(ImageFilter.MaxFilter(5))
        colored=empty=expected_empty=filled_holes=0;hole_samples=[]
        for u in range(left,right+1):
            for v in range(bottom,top+1):
                if not all(cross(a,b,(u+.5,v+.5))>=2*math.hypot(b[0]-a[0],b[1]-a[1]) for a,b in zip(polygon,polygon[1:]+polygon[:1])):continue
                rgb=pixel(u,v)
                if max(rgb)>35 and max(rgb)-min(rgb)>20:colored+=1
                else:empty+=1
                if not occupied.getpixel((u-left+2,top-v+2)):
                    expected_empty+=1
                    if len(hole_samples)<8:hole_samples.append((u,v,rgb))
                    if max(rgb)>35 and max(rgb)-min(rgb)>20:filled_holes+=1
        if colored<12 or filled_holes>.10*expected_empty or core in ('paladyne_navigation_computer','seyr_navigation_computer') and expected_empty<20:
            raise AssertionError(f'Core map differs from projected scaffold: {seed} colored={colored} background={empty} expected_holes={expected_empty} filled_holes={filled_holes} samples={hole_samples}')
        crop=Image.new('RGB',(right-left+7,top-bottom+7))
        for px,u in enumerate(range(left-3,right+4)):
            for py,v in enumerate(range(top+3,bottom-4,-1)):crop.putpixel((px,py),pixel(u,v))
        index=len(records);sx,sy=(index%8)*112,(index//8)*116
        if crop.width>108 or crop.height>100:raise AssertionError('Unexpected Core closeup projection bounds')
        sheet.paste(crop,(sx+(112-crop.width)//2,sy))
        draw.text((sx+2,sy+102),seed['block'].removesuffix('_core')[:15],fill=(230,230,230))
        records.append(dict(block=seed['block'],facing=seed['facing'],colored_pixels=colored,background_pixels=empty,expected_holes=expected_empty,filled_holes=filled_holes))
    assert {(v['block'],v['facing']) for v in records}=={(block,facing) for block in reference['blocks'] for facing in ('north','east','south','west')},'Every catalog Core and all four facings must render'
    (output/'core-render-analysis.json').write_text(json.dumps(records,indent=2)+'\n')
    sheet.save(output/'core-map-contact-sheet.png')
    print(f'Actual Core map pixels PASS {len(records)} colored scaffolds with exact projected hole checks',flush=True)


def battery_charge_regions(archive):
    """Read the candidate's continuous bar and check every clipped geometry/UV level."""
    import json
    import math

    def model(path, depth=0):
        if depth >= 16:
            raise AssertionError('Battery model parent chain is too deep')
        data=json.loads(archive.read('assets/vandorlabs/models/'+path+'.json'))
        return data if 'elements' in data else model(data['parent'].replace('vandorlabs:',''),depth+1)

    models=[model('block/module_rack/slot_seated_battery_0_'+str(level)) for level in range(5)]
    backgrounds=[];overlays=[]
    for level,data in enumerate(models):
        off=[part for part in data['elements'] if part.get('name')=='power_port_bar_off']
        on=[part for part in data['elements'] if part.get('name')=='power_port_bar_on']
        assert len(off)==1 and len(on)==(0 if level==0 else 1),'Battery requires one background and one nonempty fill quad'
        backgrounds.append(off[0]);overlays.append(on[0] if on else None)
    full=overlays[4];lo,hi=full['from'],full['to'];uv=full['faces']['south']['uv']
    assert all(math.isfinite(v) for v in lo+hi+uv) and hi[0]>lo[0] and hi[1]>lo[1],'Invalid Battery bar bounds'
    for level,(off,on) in enumerate(zip(backgrounds,overlays)):
        assert off==backgrounds[0],'Battery background must not change with charge'
        assert off['faces']['south']['texture']=='#bar_off','Battery background artwork alias'
        if on is None:
            continue
        expected_to=list(hi);expected_to[0]=lo[0]+(hi[0]-lo[0])*level/4
        expected_uv=list(uv);expected_uv[2]=uv[0]+(uv[2]-uv[0])*level/4
        assert on['from']==lo and on['to']==expected_to,'Battery fill must clip continuously from the left'
        assert on['faces']['south']=={'texture':'#bar_on','uv':expected_uv},'Battery fill must preserve authored UV scale'
        assert lo[2]>off['to'][2],'Battery fill must stay ahead of its background'
    # Sample inside each quarter, away from the shared edges where rasterization
    # can bleed a lit pixel into the next quarter of the continuous rectangle.
    regions=[]
    step=(hi[0]-lo[0])/4;inset=step/4;vertical=(hi[1]-lo[1])/8
    for quarter in range(4):
        regions.append({'from':[lo[0]+quarter*step+inset,lo[1]+vertical,lo[2]],
                        'to':[lo[0]+(quarter+1)*step-inset,hi[1]-vertical,hi[2]]})
    return regions


def validate_battery_render(output,labels):
    """Check continuous native power-port artwork at all five fill levels.

    The authored on-bar is bright red; the off-bar is dark red. These are the
    same native textures used in game, replacing the older green segments.
    South/east/up expose the front in this SE view. Other facings are covered
    by the CPU geometry matrix because their fronts are occluded in this view.
    """
    import json
    import math
    from PIL import Image
    config=json.loads((output/'dynmap/web/standalone/dynmap_config.json').read_text())
    world=next(v for v in config['worlds'] if v['name']=='world')
    mapping=next(v for v in world['maps'] if v['name']=='battery_fixture')
    matrix=mapping['worldtomap'];prefix=mapping['prefix'];tiles={};samples=[]
    jars=list((output/'mods').glob('vandorlabs-*.jar'))
    if len(jars)!=1:raise AssertionError('Battery analysis requires exactly one candidate JAR')
    with ZipFile(jars[0]) as archive:
        bars=battery_charge_regions(archive)
    def project(point,fixture):
        a,b,c=(v/16 for v in point)
        if fixture['facing']=='east':a,c=c,1-a
        elif fixture['facing']=='up':b,c=c,1-b
        a+=fixture['x'];b+=fixture['y'];c+=fixture['z']
        return matrix[0]*a+matrix[1]*b+matrix[2]*c,matrix[3]*a+matrix[4]*b+matrix[5]*c
    def inside(point,polygon):
        crosses=[(b[0]-a[0])*(point[1]-a[1])-(b[1]-a[1])*(point[0]-a[0]) for a,b in zip(polygon,polygon[1:]+polygon[:1])]
        return all(v>=-1e-7 for v in crosses) or all(v<=1e-7 for v in crosses)
    def lit_pixel(color):
        r,g,b=color;return r>=70 and r>g*1.6 and r>b*1.4
    def pixel(u,v):
        tx,ty=u//128,v//128;key=tx,ty
        if key not in tiles:
            path=output/f'dynmap/web/tiles/world/{prefix}/{tx>>5}_{ty>>5}/{tx}_{ty}.png'
            if not path.exists():raise AssertionError('Missing battery closeup tile: '+str(path))
            with Image.open(path) as source:tiles[key]=source.convert('RGB')
        return tiles[key].getpixel((u%128,127-v%128))
    for fixture in labels:
        if fixture.get('module')!='battery' or fixture['facing'] not in ('south','east','up'):continue
        x,y,z=(fixture[k] for k in ('x','y','z'))
        points=[(matrix[0]*(x+a)+matrix[1]*(y+b)+matrix[2]*(z+c),matrix[3]*(x+a)+matrix[4]*(y+b)+matrix[5]*(z+c)) for a in (0,1) for b in (0,1) for c in (0,1)]
        left,right=math.floor(min(p[0] for p in points)),math.ceil(max(p[0] for p in points))
        bottom,top=math.floor(min(p[1] for p in points)),math.ceil(max(p[1] for p in points))
        lit=0
        for u in range(left,right+1):
            for v in range(bottom,top+1):
                lit+=lit_pixel(pixel(u,v))
        quarters=[]
        for bar in bars:
            lo,hi=bar['from'],bar['to'];polygon=[project(point,fixture) for point in ((lo[0],hi[1],hi[2]),(lo[0],lo[1],hi[2]),(hi[0],lo[1],hi[2]),(hi[0],hi[1],hi[2]))]
            colored=sampled=0
            for u in range(math.floor(min(p[0] for p in polygon)),math.ceil(max(p[0] for p in polygon))+1):
                for v in range(math.floor(min(p[1] for p in polygon)),math.ceil(max(p[1] for p in polygon))+1):
                    if inside((u+.5,v+.5),polygon):sampled+=1;colored+=lit_pixel(pixel(u,v))
            quarters.append(dict(samples=sampled,lit_pixels=colored))
        samples.append(dict(label=fixture['label'],facing=fixture['facing'],energy=fixture['energy'],lit_pixels=lit,quarters=quarters))
    (output/'battery-render-analysis.json').write_text(json.dumps(samples,indent=2)+'\n')
    for facing in ('south','east','up'):
        group=sorted((v for v in samples if v['facing']==facing),key=lambda v:v['energy'])
        assert len(group)==5,'Missing projected battery front '+facing
        for fixture in group:
            expected=fixture['energy']//2500
            for index,quarter in enumerate(fixture['quarters']):
                assert quarter['samples']>0 and (quarter['lit_pixels']>0)==(index<expected),f'Battery projected fill {fixture["label"]}/{index}: {quarter}, expected lit={index<expected}'


def place_material_finishes(command, labels, jar):
    """Exercise new saved palette slots using indices from the exact artifact."""
    import json
    prefix='assets/vandorlabs/data/'
    with ZipFile(jar) as archive:
        entries=json.loads(archive.read(prefix+'unified_textures.json'))
        legacy=len(json.loads(archive.read(prefix+'dynmap_material_aspects.json')))-len(entries)
    choices={entry['id']:legacy+i for i,entry in enumerate(entries)}
    names=('satin_silver','cool_steel','pale_titanium')
    if not any('material_'+name in choices for name in names):return
    occupied={(label['x'],label['y'],label['z']) for label in labels}
    for i,name in enumerate(names):
        choice=choices['material_'+name];x=60+2*i;y=12;z=60
        assert (x,y,z) not in occupied,'Material fixture overlaps another assembly'
        command(f'setblock {x} {y} {z} vandorlabs:programmable_block 0 replace {{housingTexture:{choice},HousingTextureVersion:1}}')
        labels.append(dict(x=x,y=y,z=z,block='programmable_block',material=name,choice=choice))


def place_utility_doors(command,labels,jar):
    import json
    with ZipFile(jar) as archive:
        entries=json.loads(archive.read('assets/vandorlabs/data/unified_textures.json'))
        legacy=len(json.loads(archive.read('assets/vandorlabs/data/dynmap_material_aspects.json')))-len(entries)
    utilities=[(legacy+i,e) for i,e in enumerate(entries) if e['id'].startswith('utility_door_')]
    required = {"blue","blue_modern","green","green_modern","warm","warm_modern","factory","industrial",
                "industrial_modern","reactor","warehouse_detailed","warehouse_futuristic","bathroom_white",
                "modern_oak_windowed","windowed_utility","windowed_white","bathroom","stall"}
    actual = {entry['id'][len('utility_door_'):] for _,entry in utilities}
    assert required <= actual and len(actual)==len(utilities), 'Incomplete or duplicate Utility Door map corpus: '+str(required-actual)
    for i,(choice,entry) in enumerate(utilities):
        x,y,z=30+3*(i%9),12,-8-3*(i//9)
        command(f'setblock {x} {y} {z} vandorlabs:programmable_door facing=south,half=lower,hinge=left,open=false replace {{SpaceDesign:2,SpaceFramed:1b,DoorFaceTexture:{choice}}}')
        command(f'setblock {x} {y+1} {z} vandorlabs:programmable_door facing=south,half=upper,hinge=left,open=false replace')
        labels.append(dict(x=x,y=y,z=z,block='programmable_door',utility=entry['id'],choice=choice))


def place_utility_models(command, labels):
    """Place all programmer cells and persistent coupler endpoints in the real map fixture."""
    for facing, x, direction in (('north', 0, (1, 0)), ('east', 5, (0, 1)),
                                 ('south', 10, (-1, 0)), ('west', 15, (0, -1))):
        for part in range(4):
            px=x+direction[0]*(part&1);py=12+(part>>1);pz=30+direction[1]*(part&1)
            command(f'setblock {px} {py} {pz} vandorlabs:data_chip_programmer facing={facing},part={part} replace')
            labels.append({'x':px,'y':py,'z':pz,'block':'data_chip_programmer','facing':facing,'part':part})
    for i,facing in enumerate(('north','east','south','west','up','down')):
        for locked,z in ((False,25),(True,27)):
            x=i*3;state=str(locked).lower();invert=1 if locked else 0
            command(f'setblock {x} 12 {z} vandorlabs:square_coupler facing={facing},locked={state} replace {{CouplerInverted:{invert}b}}')
            labels.append({'x':x,'y':12,'z':z,'block':'square_coupler','facing':facing,'locked':locked})
    for column,name in enumerate(('compact_track','armoured_track','bogie_track')):
        for row,facing in enumerate(('north','east','south','west')):
            x=20+column*3;z=row*3
            command(f'setblock {x} 12 {z} vandorlabs:{name} assembled=false,facing={facing} replace')
            labels.append({'x':x,'y':12,'z':z,'block':name,'facing':facing})

    for column, facing in enumerate(('north', 'east', 'south', 'west')):
        for row, half in enumerate(('bottom', 'top')):
            x = column * 4
            z = 45 + row * 3
            command(f'setblock {x} 12 {z} vandorlabs:vehicle_coupler_slab facing={facing},half={half},locked=false replace')
            labels.append({'x':x,'y':12,'z':z,'block':'vehicle_coupler_slab','facing':facing,'half':half})

    for index, facing in enumerate(('north', 'east', 'south', 'west')):
        x = index * 5
        command(f'setblock {x} 12 35 vandorlabs:copy_controller facing={facing} replace')
        labels.append({'x':x,'y':12,'z':35,'block':'copy_controller','facing':facing})
    for index, pressed in enumerate((False, True)):
        x = index * 5
        command(f'setblock {x} 12 38 vandorlabs:programmable_pressure_plate power={15 if pressed else 0} replace {{PlateTexture:1,PlateMode:1}}')
        labels.append({'x':x,'y':12,'z':38,'block':'programmable_pressure_plate','powered':pressed})
    for index, artwork in enumerate(('landing', 'maintenance-grey', 'copier-input', 'copier-output')):
        x = index * 5
        command(f'setblock {x} 12 41 vandorlabs:turntable_surface facing=up replace {{SurfaceOn:"{artwork}",SurfaceOff:"{artwork}",SurfaceLayout:0,SurfaceGroupSize:1,SurfaceGroupNormal:1}}')
        labels.append({'x':x,'y':12,'z':41,'block':'turntable_surface','artwork':artwork})

    for size in (2, 3):
        # Keep 3x3 groups in their own negative-X field: the native machine
        # matrix occupies the positive-X region beside the existing 2x2 groups.
        for column, family in enumerate(('rocket_thruster', 'ion_drive', 'plasma_vent', 'impulse_engine')):
            for row, facing in enumerate(('north', 'east', 'south', 'west', 'up', 'down')):
                for u in range(size):
                    for v in range(size):
                        x, y, z = (-24 if size == 3 else 0) + column * 5, 12 + (row // 3) * 5, 52 + (row % 3) * 4
                        if facing in ('up', 'down'): x, z = x + u, z + v
                        elif facing in ('north', 'south'): x, y = x + u, y + v
                        else: z, y = z + u, y + v
                        name = family + '_hexagonal'
                        command(f'setblock {x} {y} {z} vandorlabs:{name} facing={facing},powered=true,particles=false replace {{PropulsionJoin:1b,ParticleLevel:0,ParticleStream:0b,SignalBrightness:0b,Channel:0,ManualOn:1b,LightInitialized:1b}}')
                        labels.append({'x':x,'y':y,'z':z,'block':name,'facing':facing,'joined':True,'joined_size':size,'u':u,'v':v})


def place_frame_machines(command, labels, root,all_facings=False):
    """Form native map footprints with the same relative owner/corner fields as gameplay."""
    import json
    specs=json.loads((root/'src/main/resources/assets/vandorlabs/data/frame_machines.json').read_text())
    new={'oculus_drive','survey_dish','sentinel_array','standalone_dish','standalone_antenna'}
    drives={'longburn_drive','meridian_core','oculus_drive'}
    layouts=[(spec,facing,None) for spec in specs if spec['design'] not in drives
             for facing in (('north','east','south','west') if all_facings or spec['design'] in new else ('north',))]
    if all_facings:
        for spec in specs:
            if spec['design'] not in ('standalone_dish','standalone_antenna'):continue
            large=dict(spec)
            for axis in ('width','height','depth'):large[axis]*=2
            large['observer_width']=large['width']
            layouts.extend((large,facing,None) for facing in ('north','east','south','west'))
    # Drive On/Off models occupy a separate field so both saved variants remain
    # visible simultaneously. A static map uses the active model as a steady
    # approximation of the in-game cyan pulse; it has no animation clock.
    layouts += [(spec,facing,active) for spec in specs if spec['design'] in drives
                for active in (False,True) for facing in ('north','east','south','west')]
    def rotate(x,z,width,depth,facing):
        return (depth-1-z,x) if facing=='east' else (width-1-x,depth-1-z) if facing=='south' else (z,width-1-x) if facing=='west' else (x,z)
    def packed(x,y,z):
        value=((x&0x3ffffff)<<38)|((y&0xfff)<<26)|(z&0x3ffffff)
        return value-(1<<64) if value>=(1<<63) else value
    ordinary=drive_index=0
    for spec,facing,active in layouts:
        if active is None:
            columns=6 if all_facings else 4
            bx=34+(ordinary%columns)*8;bz=(ordinary//columns)*8;ordinary+=1
        else:
            bx=-32+(drive_index%4)*8;bz=(drive_index//4)*8;drive_index+=1
        w,h,d=(spec[k] for k in ('width','height','depth'));cx,cz=rotate(0,0,w,d,facing)
        core=(bx+cx,12,bz+cz)
        powered=True if active is None else active
        drive='' if active is None else f',NavigationRedstone:{0 if active else 1}b,DriveSignalLevels:0b,DriveInverted:0b,DriveParticles:0,DriveParticleThreshold:8,DriveLevel:{15 if active else 0}'
        if spec['design']=='longburn_drive':drive+=f',MaxwellActiveLevel:{15 if active else 0}'
        observer=f",ObserverWidth:{spec['observer_width']}" if 'observer_width' in spec else ''
        command(f"setblock {core[0]} 12 {core[2]} vandorlabs:{spec['block']} facing={facing},formed=true replace {{ConstructorCoreX:0,ConstructorCoreZ:0,NavigationScreenOn:{int(powered)}b,MachineActivity:2{drive}{observer}}}")
        label={'x':core[0],'y':12,'z':core[2],'block':spec['block'],'facing':facing,'formed':True,'frame_fixture':True}
        if 'observer_width' in spec:label['observer_width']=spec['observer_width']
        if active is not None:label.update(drive_active=active,drive_level=15 if active else 0,map_animation='steady-active' if active else 'off')
        labels.append(label)
        for y in range(h):
            for x in range(w):
                for z in range(d):
                    if (x,y,z)==(0,0,0):continue
                    px,pz=rotate(x,z,w,d,facing);px+=bx;pz+=bz
                    offset=packed(core[0]-px,-y,core[2]-pz)
                    command(f'setblock {px} {12+y} {pz} vandorlabs:constructor_part 0 replace {{ConstructorOwner:{offset}L,ConstructorMatter:0b}}')
                    labels.append({'x':px,'y':12+y,'z':pz,'block':'constructor_part','owner':spec['block'],'facing':facing,'frame_fixture':True})

def place_appliances(command,labels,root):
    import json
    catalog=json.loads((root/'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())
    legacy=len(re.findall(r'new Finish\(', (root/'src/portable/java/com/vandorlabs/tiles/HousingFinishCatalog.java').read_text()))
    choices={entry['source']:legacy+i for i,entry in enumerate(catalog)}
    ids=['indoor_ac_slab_dark_cyan','indoor_ac_slab_silver','outdoor_ac_dark_cyan','outdoor_ac_silver','printer','scanner'];faces=['bottom','top','front','back','left','right'];number=0
    for kind,identifier in enumerate(ids):
        for facing in ('north','east','south','west'):
            for upper in ((False,True) if kind<2 else (False,)):
                x,z=3+(number%8)*5,3+(number//8)*5;number+=1
                block='programmable_slab' if kind<2 else 'programmable_block' if kind<4 else identifier
                state='facing='+facing+(',half='+('top' if upper else 'bottom') if kind<2 else '')
                values=[choices['appliances/'+identifier+'/'+face] for face in faces]
                settings=(' {housingTexture:'+str(values[2])+',HousingTextureVersion:1,MainTextureLayout:0,SlabTileSides:0b,FaceTexturesEnabled:1b,FaceTextures:[I;'+','.join(map(str,values))+']}') if kind<4 else ''
                command(f'setblock {x} 12 {z} vandorlabs:{block} {state} replace{settings}')
                labels.append(dict(x=x,y=12,z=z,block=block,appliance=identifier,facing=facing,upper=upper,appliance_fixture=True))

def place_core_seeds(command,labels,root):
    import json
    specs=json.loads((root/'src/main/resources/assets/vandorlabs/data/frame_machines.json').read_text())
    index=0
    for spec in specs:
        for facing in ('north','east','south','west'):
            x,z=2+3*(index%8),2+3*(index//8);index+=1
            command(f"setblock {x} 12 {z} vandorlabs:{spec['block']} facing={facing},formed=false")
            labels.append(dict(x=x,y=12,z=z,block=spec['block'],facing=facing,core_seed=True,frame_fixture=True))


def place_segmented_doors(command, labels):
    for index,facing in enumerate(('north','east','south','west')):
        for opened in (False,True):
            x=10+index*5;z=72+(8 if opened else 0)
            for row in range(5):
                command(f'setblock {x} {12+row} {z} vandorlabs:segmented_door facing={facing} replace {{SegmentedTopOffset:{4-row},SegmentedHeight:5,SegmentedFormed:1b,SegmentedOpen:{int(opened)}b,SegmentedFrom:{float(opened)}d,SegmentedLocalRedstone:0b}}')
                labels.append(dict(x=x,y=12+row,z=z,block='segmented_door',facing=facing,opened=opened,row=row))


def place_segmented_supplied(command, labels, jar):
    """Actual saved model choices use authored material patches, not their picker thumbnails."""
    import json
    with ZipFile(jar) as archive:
        entries=json.loads(archive.read('assets/vandorlabs/data/unified_textures.json'))
        legacy=len(json.loads(archive.read('assets/vandorlabs/data/dynmap_material_aspects.json')))-len(entries)
    choices={entry['id']:legacy+i for i,entry in enumerate(entries)}
    names=('picket','industrial','lattice')
    if not any('segmented_gate_'+name in choices for name in names):return
    occupied={(label['x'],label['y'],label['z']) for label in labels}
    for style,name in enumerate(names):
        selected=choices['segmented_gate_'+name]
        for column in range(3):
            for row in range(3):
                x,y,z=-16+style*5+column,12+row,72
                assert (x,y,z) not in occupied,'Supplied gate fixture overlaps another assembly'
                tag=f'{{SegmentedTopOffset:{2-row},SegmentedHeight:3,SegmentedColumn:{column},SegmentedColumns:3,SegmentedFormed:1b,SegmentedConfigured:1b,SegmentedTexture:{selected},SegmentedOpen:0b,SegmentedFrom:0d,SegmentedLocalRedstone:0b,SegmentedSlide:0,SegmentedWindow:1b,SegmentedShape:2,SegmentedJoin:1b,SegmentedSettings:{{Direction:0,Straight:0b,Layout:1,Activation:0,Window:1b,Shape:2,Join:1b}}}}'
                command(f'setblock {x} {y} {z} vandorlabs:segmented_door facing=north replace '+tag)
                labels.append(dict(x=x,y=y,z=z,block='segmented_door',facing='north',opened=False,row=row,supplied=name,choice=selected))


def place_module_racks(command, labels):
    """All native facing/mask combinations plus physical saved battery charge levels."""
    for row, facing in enumerate(('north', 'east', 'south', 'west', 'up', 'down')):
        for mask in range(16):
            x, y, z = -8 + 3 * mask, 12, 4 + 3 * row
            items = ','.join('{Slot:%db,id:"vandorlabs:blank_module",Count:1b,Damage:0s,tag:{RackMapSlot:%d}}' % (slot, slot)
                             for slot in range(4) if mask & (1 << slot))
            command(f'setblock {x} {y} {z} vandorlabs:module_rack facing={facing} replace {{Items:[{items}]}}')
            labels.append(dict(x=x, y=y, z=z, block='module_rack', facing=facing, mask=mask, module='blank', label=f'blank-{facing}-{mask}'))
        for level,energy in enumerate((0,2500,5000,7500,10000)):
            x,y,z=-8+3*level,12,28+3*row
            item='{Slot:0b,id:"vandorlabs:battery_module",Count:1b,Damage:0s,tag:{BatteryEnergy:%d}}'%energy
            command(f'setblock {x} {y} {z} vandorlabs:module_rack facing={facing} replace {{Items:[{item}]}}')
            labels.append(dict(x=x,y=y,z=z,block='module_rack',facing=facing,mask=1,module='battery',energy=energy,expected_lit_quarters=level,expected_background_quads=1,label=f'battery-{facing}-{energy}'))


def validate_fixture_matrix(labels):
    """Fail before launching a map if any authored group or drive state is absent."""
    from collections import Counter
    if labels and all(v.get('surface_fixture') for v in labels):
        assert len(labels)==3 and {v['layout'] for v in labels}=={0,1,2}
        return
    if labels and all(v.get('appliance_fixture') for v in labels):
        assert len(labels)==32 and len({(v['appliance'],v['facing'],v['upper']) for v in labels})==32,'All appliance/facing/half combinations'
        return
    if labels and all(v.get('frame_fixture') for v in labels):
        import json
        specs=json.loads((Path(__file__).resolve().parents[1]/'src/main/resources/assets/vandorlabs/data/frame_machines.json').read_text())
        expected={(v['block'],f) for v in specs for f in ('north','east','south','west')}
        assert {(v['block'],v['facing']) for v in labels if v.get('core_seed')}==expected,'Core seed coverage'
        assert {(v['block'],v['facing']) for v in labels if v.get('formed')}==expected,'Formed machine facing coverage'
        assert {(v['block'],v['facing'],v['observer_width']) for v in labels if 'observer_width' in v}=={(block,f,width) for block,width in [('standalone_dish_core',6),('standalone_antenna_core',4)] for f in ('north','east','south','west')},'Double-size observer field coverage'
        positions=[(v['x'],v['y'],v['z']) for v in labels]
        assert len(set(positions))==len(positions),'Overlapping frame/Core fixtures'
        assert all((x-24)**2+(z-24)**2<=64**2 for x,_,z in positions),'Frame fixture outside map radius'
        return
    if labels and all(label['block'] == 'module_rack' for label in labels):
        blanks=[v for v in labels if v.get('module')=='blank'];batteries=[v for v in labels if v.get('module')=='battery']
        assert len(blanks)==96 and {(v['facing'], v['mask']) for v in blanks} == {(f, m) for f in ('north','east','south','west','up','down') for m in range(16)}, 'Module Rack facing/mask coverage missing'
        expected={(f,e) for f in ('north','east','south','west','up','down') for e in (0,2500,5000,7500,10000)}
        assert len(batteries)==30 and {(v['facing'],v['energy']) for v in batteries}==expected,'Battery facing/charge coverage missing'
        assert all(v['mask']==1 and v['expected_lit_quarters']==v['energy']//2500 and v['expected_background_quads']==1 for v in batteries),'Battery fixture differs from emitted-geometry goldens'
        assert len(labels)==126 and len({v['label'] for v in labels})==126,'Duplicate rack coverage labels'
        assert len({(v['x'],v['y'],v['z']) for v in labels})==126,'Overlapping rack fixtures'
        assert all((v['x']-24)**2+(v['z']-24)**2<=64**2 for v in labels),'Rack fixture outside map radius'
        return
    families=('rocket_thruster','ion_drive','plasma_vent','impulse_engine')
    groups=Counter((label['block'],label.get('facing'),label.get('joined_size'))
                   for label in labels if label.get('joined'))
    for family in families:
        for facing in ('north','east','south','west','up','down'):
            for size in (2,3):
                assert groups[(family+'_hexagonal',facing,size)]==size*size, (family,facing,size)
    drives=Counter((label['block'],label.get('facing'),label.get('drive_active'))
                   for label in labels if 'drive_active' in label)
    for family in ('longburn_drive_core','meridian_drive_core','oculus_drive_core'):
        for facing in ('north','east','south','west'):
            for active in (False,True):
                assert drives[(family,facing,active)]==1, (family,facing,active)
    segmenteds=Counter((v.get("facing"),v.get("opened")) for v in labels if v["block"]=="segmented_door" and "supplied" not in v)
    assert len(segmenteds)==8 and all(n==5 for n in segmenteds.values()), "Segmented endpoint/facing coverage missing"
    positions=[(label['x'],label['y'],label['z']) for label in labels]
    assert len(set(positions))==len(positions), 'Overlapping Dynmap fixtures hide coverage'
    assert all((x-24)**2+(z-24)**2<=64**2 for x,_,z in positions), 'Fixture outside map radius'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--forge',required=True,type=Path)
    parser.add_argument('--dynmap',required=True,type=Path)
    parser.add_argument('--jar',required=True,type=Path)
    parser.add_argument('--java',default='java')
    parser.add_argument('--focus',choices=('module-rack','frame-machines','appliances','surfaces'),help='Only the selected changed block family')
    parser.add_argument('--mod',action='append',default=[],type=Path,help='Additional installed compatibility JARs')
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    output=Path(tempfile.mkdtemp(prefix='dynmap-render-',dir=root/'build'))
    artifact=ArtifactRun(output,disposable_paths=('mods','world','dynmap','logs','config','core-projection-classes'))
    with artifact:
        run_fixture(args,root,output,artifact)

def run_fixture(args,root,output,artifact):
    print('Isolated Dynmap fixture:',output,flush=True)
    if args.focus=='frame-machines':core_projection_reference(output,args.jar,args.java)
    (output/'mods').mkdir();(output/'dynmap').mkdir()
    for mod in (args.jar,args.dynmap,*args.mod):shutil.copyfile(mod,output/'mods'/mod.name)
    (output/'artifact.sha256').write_text(hashlib.sha256(args.jar.read_bytes()).hexdigest()+'\n')
    with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
    (output/'eula.txt').write_text('eula=true\n')
    (output/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nlevel-type=FLAT\ngenerate-structures=false\nspawn-animals=false\nspawn-monsters=false\nview-distance=3\nallow-nether=false\nmax-tick-time=120000\n')
    with ZipFile(args.dynmap) as archive:config=archive.read('configuration.txt').decode()
    config=config.replace('disable-webserver: false','disable-webserver: true').replace('image-format: jpg-q90','image-format: png')
    config=config.replace('org.dynmap.InternalClientUpdateComponent','org.dynmap.JsonFileClientUpdateComponent')
    config+=f'\nparallelrendercnt: {4 if args.focus in ("module-rack","surfaces") else 1}\nupdate-min-tps: 0\nfullrender-min-tps: 0\n'
    (output/'dynmap/configuration.txt').write_text(config)
    (output/'dynmap/worlds.txt').write_text('''worlds:
  - name: world
    enabled: true
    maps:
      - class: org.dynmap.hdmap.HDMap
        name: fixture
        title: Fixture
        prefix: fixture
        perspective: iso_SE_30_hires
        shader: stdtexture
        lighting: default
        image-format: png
''')
    if args.focus=='surfaces':
        (output/'dynmap/custom-perspectives.txt').write_text('perspectives:\n  - class: org.dynmap.hdmap.IsoHDPerspective\n    name: surface_closeup\n    azimuth: 135\n    inclination: 90\n    scale: 64\n    minimumheight: 10\n    maximumheight: 16\n')
        worlds=output/'dynmap/worlds.txt'
        worlds.write_text(worlds.read_text().replace('perspective: iso_SE_30_hires','perspective: surface_closeup'))
    if args.focus=='module-rack':
        (output/'dynmap/custom-perspectives.txt').write_text('perspectives:\n  - class: org.dynmap.hdmap.IsoHDPerspective\n    name: rack_charge_closeup\n    azimuth: 135\n    inclination: 30\n    scale: 64\n    minimumheight: 10\n    maximumheight: 16\n')
        with (output/'dynmap/worlds.txt').open('a') as extra:
            extra.write('      - class: org.dynmap.hdmap.HDMap\n        name: battery_fixture\n        title: Battery Charge Fixture\n        prefix: battery_fixture\n        perspective: rack_charge_closeup\n        shader: stdtexture\n        lighting: default\n        image-format: png\n')
    if args.focus=='frame-machines':
        (output/'dynmap/custom-perspectives.txt').write_text('perspectives:\n  - class: org.dynmap.hdmap.IsoHDPerspective\n    name: core_closeup\n    azimuth: 135\n    inclination: 90\n    scale: 64\n    minimumheight: 10\n    maximumheight: 16\n')
        with (output/'dynmap/worlds.txt').open('a') as extra:
            extra.write('      - class: org.dynmap.hdmap.HDMap\n        name: core_fixture\n        title: Open Core Fixture\n        prefix: core_fixture\n        perspective: core_closeup\n        shader: stdtexture\n        lighting: default\n        image-format: png\n')
    for phase in ('prepare','render'):
        logpath=output/(phase+'.log')
        with logpath.open('w') as log:
            process=subprocess.Popen([args.java,'-Xmx2G','-jar',str(args.forge.resolve()),'nogui'],cwd=output,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
            artifact.track_process(process)
            def command(line):process.stdin.write(line+'\n');process.stdin.flush()
            def wait(marker,timeout=180,start=0):
                deadline=time.monotonic()+timeout
                while time.monotonic()<deadline:
                    content=logpath.read_text(errors='replace')[start:]
                    if any(error in content for error in ('NoSuchMethodError','Exception in server tick loop','Encountered an unexpected exception','Error loading custom renderer','Cannot load native Copy Controller map geometry')):raise RuntimeError('Dynmap/server error: '+str(logpath))
                    if marker in content:return
                    if process.poll() is not None:raise RuntimeError('Server exited: '+str(logpath))
                    time.sleep(.25)
                raise TimeoutError(f'{marker}: {logpath}')
            try:
                wait('Done (');command('dynmap pause all')
                if phase=='prepare':command('setworldspawn 8 12 8')
                else:
                    floor_max=80 if args.focus=='frame-machines' else 64
                    command(f'fill -34 11 -2 {floor_max} 11 64 minecraft:stone')
                    labels=[]
                    if args.focus == 'appliances':
                        place_appliances(command,labels,root)
                    elif args.focus == 'surfaces':
                        for layout in range(3):
                            x,z=9+3*layout,12
                            command(f'setblock {x} 12 {z} vandorlabs:turntable_surface facing=up replace {{SurfaceOn:"copier-input",SurfaceOff:"copier-input",SurfaceAppearanceConfigured:1b,SurfaceLayout:{layout}}}')
                            labels.append(dict(x=x,y=12,z=z,block='turntable_surface',surface_fixture=True,layout=layout))
                    elif args.focus == 'module-rack':
                        place_module_racks(command, labels)
                    elif args.focus=='frame-machines':
                        place_frame_machines(command,labels,root,all_facings=True)
                        place_core_seeds(command,labels,root)
                    else:
                        for i,facing in enumerate(('north','east','south','west')):
                            x=i*5
                            for z,block,properties,tag in (
                                (0,'programmable_slab','half=bottom', '{housingTexture:1}'),
                                (3,'programmable_stairs','half=bottom,shape=straight','{housingTexture:17,HousingTextureVersion:1,StairTread:4}'),
                                (6,'programmable_diagonal_wall','inverted=false,depth=0','{housingTexture:1,DiagonalFullWidth:1b,DiagonalFill:3}'),
                                (9,'programmable_wall','inverted=false,depth=0','{housingTexture:3}'),
                                (12,'programmable_door','half=lower,open=false','{SpaceDesign:2,SpaceFramed:1b}'),
                                (16,'regular_canopy_glass','', '{CanopyShade:1,CanopyTint:1}'),
                                (20,'programmable_slab','half=top','{housingTexture:84,HousingTextureVersion:1}'),
                                (23,'programmable_diagonal_wall','inverted=true,depth=0','{housingTexture:1,DiagonalHalfHeight:1b,DiagonalFill:3}')):
                                state='facing='+facing+(','+properties if properties else '')
                                command(f'setblock {x} 12 {z} vandorlabs:{block} {state} replace {tag}')
                                if block=='programmable_door':command(f'setblock {x} 13 {z} vandorlabs:{block} half=upper,hinge=left')
                                labels.append({'x':x,'y':12,'z':z,'block':block,'facing':facing})
                        place_utility_models(command,labels)
                        place_frame_machines(command,labels,root)
                        place_material_finishes(command,labels,args.jar)
                        place_utility_doors(command,labels,args.jar)
                        place_segmented_doors(command,labels)
                        place_segmented_supplied(command,labels,args.jar)
                    validate_fixture_matrix(labels)
                    command('save-all flush');command('say VANDOR_DYNMAP_FIXTURE_SAVED');wait('VANDOR_DYNMAP_FIXTURE_SAVED')
                    content=logpath.read_text(errors='replace')
                    if any(error in content for error in ('Cannot place block','Invalid block state','Invalid block property','is not a valid state','Data tag parsing failed')):raise RuntimeError('Fixture placement failed: '+str(logpath))
                    import json
                    (output/'fixtures.json').write_text(json.dumps(labels,indent=2)+'\n')
                    command('dynmap pause none');command('dynmap radiusrender world 12 12 8 fixture' if args.focus=='surfaces' else 'dynmap radiusrender world 24 24 64 fixture')
                    wait('render of map',timeout=240)
                    # Completion wording differs between Forge builds; require the explicit completion.
                    wait('completed -',timeout=240)
                    if args.focus=='module-rack':
                        start=len(logpath.read_text(errors='replace'));command('dynmap radiusrender world -2 36 16 battery_fixture')
                        wait('completed -',timeout=240,start=start)
                    if args.focus=='frame-machines':
                        start=len(logpath.read_text(errors='replace'));command('dynmap radiusrender world 12 12 16 core_fixture')
                        wait('completed -',timeout=240,start=start)
                    count=validate_render(output)
                    print('Dynmap render PASS:',count,'PNG tiles;',output,flush=True)
                command('stop');process.wait(timeout=45)
                if process.returncode:raise RuntimeError('Unclean server shutdown')
            finally:
                if process.poll() is None:
                    try:command('stop');process.wait(timeout=30)
                    except Exception:process.kill();process.wait(timeout=10)

if __name__=='__main__':main()
