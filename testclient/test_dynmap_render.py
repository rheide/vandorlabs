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
    if text.count('Block placed')<len(labels)+(0 if all(v['block']=='module_rack' for v in labels) else 4):raise AssertionError('Incomplete block fixture')
    files=[p for p in (output/'dynmap/web/tiles/world').rglob('*.png') if 'fixture' in str(p)]
    if not files:raise AssertionError('Dynmap produced no fixture PNG tiles')
    richest=0
    for path in files:
        if path.name.startswith('z'):continue
        with Image.open(path) as image:richest=max(richest,len(image.convert('RGB').getcolors(1000000) or []))
    if richest<300:raise AssertionError('Map contains no sufficiently detailed textured fixture tile')
    if labels and all(v["block"]=="module_rack" for v in labels):validate_battery_render(output,labels)
    return len(files)


def validate_battery_render(output,labels):
    """Project actual fixture blocks into PNGs; require red art and increasing green charge.

    CPU goldens count four red faces and zero through four green faces on all six
    orientations. This SE view tests the visible south/east/up fronts; rear-facing
    fronts are deliberately omitted from color assertions because the shell hides them.
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
        def model(path):
            data=json.loads(archive.read('assets/vandorlabs/models/'+path+'.json'))
            return data if 'elements' in data else model(data['parent'].replace('vandorlabs:',''))
        bars=[part for part in model('block/module_rack/slot_seated_battery_0_4')['elements'] if part.get('name','').startswith('charge_')]
    if len(bars)!=4:raise AssertionError('Candidate lacks four Battery charge quads')
    def project(point,fixture):
        a,b,c=(v/16 for v in point)
        if fixture['facing']=='east':a,c=c,1-a
        elif fixture['facing']=='up':b,c=c,1-b
        a+=fixture['x'];b+=fixture['y'];c+=fixture['z']
        return matrix[0]*a+matrix[1]*b+matrix[2]*c,matrix[3]*a+matrix[4]*b+matrix[5]*c
    def inside(point,polygon):
        crosses=[(b[0]-a[0])*(point[1]-a[1])-(b[1]-a[1])*(point[0]-a[0]) for a,b in zip(polygon,polygon[1:]+polygon[:1])]
        return all(v>=-1e-7 for v in crosses) or all(v<=1e-7 for v in crosses)
    def green_pixel(color):
        r,g,b=color;return g>=45 and g>r*1.6 and g>b*1.2
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
        red=green=0
        for u in range(left,right+1):
            for v in range(bottom,top+1):
                r,g,b=pixel(u,v)
                green+=g>=45 and g>r*1.6 and g>b*1.2
                red+=r>=45 and r>g*2 and r>b*1.6
        segments=[]
        for bar in bars:
            lo,hi=bar['from'],bar['to'];polygon=[project(point,fixture) for point in ((lo[0],hi[1],hi[2]),(lo[0],lo[1],hi[2]),(hi[0],lo[1],hi[2]),(hi[0],hi[1],hi[2]))]
            colored=sampled=0
            for u in range(math.floor(min(p[0] for p in polygon)),math.ceil(max(p[0] for p in polygon))+1):
                for v in range(math.floor(min(p[1] for p in polygon)),math.ceil(max(p[1] for p in polygon))+1):
                    if inside((u+.5,v+.5),polygon):sampled+=1;colored+=green_pixel(pixel(u,v))
            segments.append(dict(samples=sampled,green_pixels=colored))
        samples.append(dict(label=fixture['label'],facing=fixture['facing'],energy=fixture['energy'],red_pixels=red,green_pixels=green,segments=segments))
    (output/'battery-render-analysis.json').write_text(json.dumps(samples,indent=2)+'\n')
    for facing in ('south','east','up'):
        group=sorted((v for v in samples if v['facing']==facing),key=lambda v:v['energy'])
        assert len(group)==5,'Missing projected battery front '+facing
        greens=[v['green_pixels'] for v in group]
        assert greens[0]==0 and greens[-1]>0,f'Battery empty/full green contrast {facing}: {greens}'
        for fixture in group:
            expected=fixture['energy']//2500
            for index,segment in enumerate(fixture['segments']):
                assert segment['samples']>0 and (segment['green_pixels']>0)==(index<expected),f'Battery projected segment {fixture["label"]}/{index}: {segment}, expected lit={index<expected}'
        assert sum(v['red_pixels'] for v in group)>0,'Missing red battery lightning '+facing


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
    assert len(utilities)==16,'Incomplete Utility Door map corpus'
    for i,(choice,entry) in enumerate(utilities):
        x,y,z=30+3*i,12,-8
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


def place_frame_machines(command, labels, root):
    """Form native map footprints with the same relative owner/corner fields as gameplay."""
    import json
    specs=json.loads((root/'src/main/resources/assets/vandorlabs/data/frame_machines.json').read_text())
    new={'oculus_drive','survey_dish','sentinel_array','standalone_dish','standalone_antenna'}
    drives={'longburn_drive','meridian_core','oculus_drive'}
    layouts=[(spec,facing,None) for spec in specs if spec['design'] not in drives
             for facing in (('north','east','south','west') if spec['design'] in new else ('north',))]
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
            bx=34+(ordinary%4)*8;bz=(ordinary//4)*8;ordinary+=1
        else:
            bx=-32+(drive_index%4)*8;bz=(drive_index//4)*8;drive_index+=1
        w,h,d=(spec[k] for k in ('width','height','depth'));cx,cz=rotate(0,0,w,d,facing)
        core=(bx+cx,12,bz+cz)
        powered=True if active is None else active
        drive='' if active is None else f',NavigationRedstone:{0 if active else 1}b,DriveSignalLevels:0b,DriveInverted:0b,DriveParticles:0,DriveParticleThreshold:8,DriveLevel:{15 if active else 0}'
        command(f"setblock {core[0]} 12 {core[2]} vandorlabs:{spec['block']} facing={facing},formed=true replace {{ConstructorCoreX:0,ConstructorCoreZ:0,NavigationScreenOn:{int(powered)}b,MachineActivity:2{drive}}}")
        label={'x':core[0],'y':12,'z':core[2],'block':spec['block'],'facing':facing,'formed':True}
        if active is not None:label.update(drive_active=active,drive_level=15 if active else 0,map_animation='steady-active' if active else 'off')
        labels.append(label)
        for y in range(h):
            for x in range(w):
                for z in range(d):
                    if (x,y,z)==(0,0,0):continue
                    px,pz=rotate(x,z,w,d,facing);px+=bx;pz+=bz
                    offset=packed(core[0]-px,-y,core[2]-pz)
                    command(f'setblock {px} {12+y} {pz} vandorlabs:constructor_part 0 replace {{ConstructorOwner:{offset}L,ConstructorMatter:0b}}')
                    labels.append({'x':px,'y':12+y,'z':pz,'block':'constructor_part','owner':spec['block'],'facing':facing})


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
            labels.append(dict(x=x,y=y,z=z,block='module_rack',facing=facing,mask=1,module='battery',energy=energy,expected_green_segments=level,expected_red_faces=4,label=f'battery-{facing}-{energy}'))


def validate_fixture_matrix(labels):
    """Fail before launching a map if any authored group or drive state is absent."""
    from collections import Counter
    if labels and all(label['block'] == 'module_rack' for label in labels):
        blanks=[v for v in labels if v.get('module')=='blank'];batteries=[v for v in labels if v.get('module')=='battery']
        assert len(blanks)==96 and {(v['facing'], v['mask']) for v in blanks} == {(f, m) for f in ('north','east','south','west','up','down') for m in range(16)}, 'Module Rack facing/mask coverage missing'
        expected={(f,e) for f in ('north','east','south','west','up','down') for e in (0,2500,5000,7500,10000)}
        assert len(batteries)==30 and {(v['facing'],v['energy']) for v in batteries}==expected,'Battery facing/charge coverage missing'
        assert all(v['mask']==1 and v['expected_green_segments']==v['energy']//2500 and v['expected_red_faces']==4 for v in batteries),'Battery fixture differs from emitted-geometry goldens'
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
    parser.add_argument('--focus',choices=('module-rack',),help='Only the selected changed block family')
    parser.add_argument('--mod',action='append',default=[],type=Path,help='Additional installed compatibility JARs')
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    output=Path(tempfile.mkdtemp(prefix='dynmap-render-',dir=root/'build'))
    artifact=ArtifactRun(output,disposable_paths=('mods','world','dynmap','logs','config'))
    with artifact:
        run_fixture(args,root,output,artifact)

def run_fixture(args,root,output,artifact):
    print('Isolated Dynmap fixture:',output,flush=True)
    (output/'mods').mkdir();(output/'dynmap').mkdir()
    for mod in (args.jar,args.dynmap,*args.mod):shutil.copyfile(mod,output/'mods'/mod.name)
    (output/'artifact.sha256').write_text(hashlib.sha256(args.jar.read_bytes()).hexdigest()+'\n')
    with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
    (output/'eula.txt').write_text('eula=true\n')
    (output/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nlevel-type=FLAT\ngenerate-structures=false\nspawn-animals=false\nspawn-monsters=false\nview-distance=3\nallow-nether=false\nmax-tick-time=120000\n')
    with ZipFile(args.dynmap) as archive:config=archive.read('configuration.txt').decode()
    config=config.replace('disable-webserver: false','disable-webserver: true').replace('image-format: jpg-q90','image-format: png')
    config=config.replace('org.dynmap.InternalClientUpdateComponent','org.dynmap.JsonFileClientUpdateComponent')
    config+='\nparallelrendercnt: 1\nupdate-min-tps: 0\nfullrender-min-tps: 0\n'
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
    if args.focus=='module-rack':
        (output/'dynmap/custom-perspectives.txt').write_text('perspectives:\n  - class: org.dynmap.hdmap.IsoHDPerspective\n    name: rack_charge_closeup\n    azimuth: 135\n    inclination: 30\n    scale: 64\n    minimumheight: 10\n    maximumheight: 16\n')
        with (output/'dynmap/worlds.txt').open('a') as extra:
            extra.write('      - class: org.dynmap.hdmap.HDMap\n        name: battery_fixture\n        title: Battery Charge Fixture\n        prefix: battery_fixture\n        perspective: rack_charge_closeup\n        shader: stdtexture\n        lighting: default\n        image-format: png\n')
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
                    command('fill -34 11 -2 64 11 64 minecraft:stone')
                    labels=[]
                    if args.focus == 'module-rack':
                        place_module_racks(command, labels)
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
                    command('dynmap pause none');command('dynmap radiusrender world 24 24 64 fixture')
                    wait('render of map',timeout=240)
                    # Completion wording differs between Forge builds; require the explicit completion.
                    wait('completed -',timeout=240)
                    if args.focus=='module-rack':
                        start=len(logpath.read_text(errors='replace'));command('dynmap radiusrender world -2 36 16 battery_fixture')
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
