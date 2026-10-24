"""Validate imported machine mesh bounds, materials, animations and state coverage."""
import json,math,sys,io
from zipfile import ZipFile
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
TEX=ROOT/'texture-packs/additional/assets/vandorlabs/textures'
specs=json.loads((ASSETS/'data/frame_machines.json').read_text())
models=0;animations=0
for spec in specs:
    paths=spec.get('state_models') or (['complete.obj','active/complete.obj'] if spec.get('chip_anchor') else ['complete.obj'])
    topology=None
    for path in paths:
        obj=ASSETS/'models/block'/spec['model']/path
        lines=obj.read_text().splitlines();vertices=[];faces=[]
        for line in lines:
            values=line.split()
            if not values:continue
            if values[0]=='v':vertices.append(tuple(map(float,values[1:4])))
            if values[0]=='vt':assert all(-1e-6<=float(x)<=1.000001 for x in values[1:3]),obj
            if values[0]=='f':assert len(values)==5,obj;faces.append(line)
        assert vertices and faces,obj
        for vertex in vertices:
            assert all(math.isfinite(x) for x in vertex),obj
            assert all(-1e-5<=v<=limit+1e-5 for v,limit in zip(vertex,[spec['width'],spec['height'],spec['depth']])),(obj,vertex)
        if topology is None:topology=(vertices,faces)
        else:assert topology==(vertices,faces),(obj,'state geometry changed')
        mtl=(obj.parent/'materials.mtl').read_text();assert 'mctrek:' not in mtl
        for line in mtl.splitlines():
            if not line.startswith('map_Kd '):continue
            texture=TEX/(line.split('vandorlabs:',1)[1]+'.png');assert texture.is_file(),texture
            with Image.open(texture) as image:
                assert image.width<=256 and image.height%image.width==0,texture
                metadata=texture.with_suffix('.png.mcmeta')
                if image.height!=image.width:
                    animation=json.loads(metadata.read_text())['animation'];assert animation['frametime']>0
                    assert all(0<=int(frame)<image.height//image.width for frame in animation['frames']);animations+=1
        models+=1
    core=json.loads((ASSETS/'models/item'/(spec['block']+'.json')).read_text())
    assert max(core['display']['gui']['scale'])<=.5,spec['block']
for kind in ['flat','angled']:
    states=json.loads((ASSETS/'blockstates'/('navigation_table_'+kind+'.json')).read_text())['variants']
    for facing in ['north','east','south','west']:
        for mask in range(16):
            for chip in ['false','true']:
                variants=states[f'chip={chip},connections={mask},facing={facing}']
                for variant in variants:
                    path=ASSETS/'models/block'/(variant['model'].split(':',1)[1]);assert path.is_file(),path
    models+=len(list((ASSETS/'models/block/navigation_table'/kind).glob('*.obj')))
print(f'Bridge model assets PASS designs={len(specs)} OBJ states/variants={models} animated materials={animations} bounded geometry shared state topology complete navigation variants padded Core items')
if len(sys.argv)>1:
    with ZipFile(sys.argv[1]) as archive:
        prefix='assets/vandorlabs/';aliases=json.loads(archive.read(prefix+'data/texture_aliases.json'))
        checked=0
        for family in ['dimension_drive','longburn_drive','navigation_table','navigation_computer_core']:
            for source in (TEX/'blocks'/family).glob('*.png'):
                relative='textures/'+source.relative_to(TEX).as_posix()
                with Image.open(source) as before,Image.open(io.BytesIO(archive.read(prefix+aliases.get(relative,relative)))) as after:
                    assert before.size==after.size and before.convert('RGBA').tobytes()==after.convert('RGBA').tobytes(),source
                checked+=1
        print(f'Packaged model atlases PASS {checked} native atlases/animation strips preserve authored dimensions and every RGBA pixel')
