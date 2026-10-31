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
    if text.count('Block placed')<len(labels)+4:raise AssertionError('Incomplete block fixture')
    files=[p for p in (output/'dynmap/web/tiles/world').rglob('*.png') if 'fixture' in str(p)]
    if not files:raise AssertionError('Dynmap produced no fixture PNG tiles')
    richest=0
    for path in files:
        if path.name.startswith('z'):continue
        with Image.open(path) as image:richest=max(richest,len(image.convert('RGB').getcolors(1000000) or []))
    if richest<300:raise AssertionError('Map contains no sufficiently detailed textured fixture tile')
    return len(files)


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


def validate_fixture_matrix(labels):
    """Fail before launching a map if any authored group or drive state is absent."""
    from collections import Counter
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
    positions=[(label['x'],label['y'],label['z']) for label in labels]
    assert len(set(positions))==len(positions), 'Overlapping Dynmap fixtures hide coverage'
    assert all((x-24)**2+(z-24)**2<=64**2 for x,_,z in positions), 'Fixture outside map radius'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--forge',required=True,type=Path)
    parser.add_argument('--dynmap',required=True,type=Path)
    parser.add_argument('--jar',required=True,type=Path)
    parser.add_argument('--java',default='java')
    parser.add_argument('--mod',action='append',default=[],type=Path,help='Additional installed compatibility JARs')
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    output=Path(tempfile.mkdtemp(prefix='dynmap-render-',dir=root/'build'))
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
    for phase in ('prepare','render'):
        logpath=output/(phase+'.log')
        with logpath.open('w') as log:
            process=subprocess.Popen([args.java,'-Xmx2G','-jar',str(args.forge.resolve()),'nogui'],cwd=output,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
            def command(line):process.stdin.write(line+'\n');process.stdin.flush()
            def wait(marker,timeout=180):
                deadline=time.monotonic()+timeout
                while time.monotonic()<deadline:
                    content=logpath.read_text(errors='replace')
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
                    count=validate_render(output)
                    print('Dynmap render PASS:',count,'PNG tiles;',output,flush=True)
                command('stop');process.wait(timeout=45)
                if process.returncode:raise RuntimeError('Unclean server shutdown')
            finally:
                if process.poll() is None:
                    try:command('stop');process.wait(timeout=30)
                    except Exception:process.kill();process.wait(timeout=10)

if __name__=='__main__':main()
