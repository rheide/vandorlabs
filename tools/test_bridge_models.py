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
# Inspect emitted OBJ faces, not just layout metadata: joined edges reach the
# cell border, while exposed edges retain the authored two-pixel frame.
for kind in ['flat','angled']:
    folder=ASSETS/'models/block/navigation_table'/kind
    for path in folder.glob('mask_*.obj'):
        if '_chip' in path.stem:continue
        mask=int(path.stem.split('_')[1]);vertices=[];surface=[];group=''
        for line in path.read_text().splitlines():
            parts=line.split()
            if not parts:continue
            if parts[0]=='v':vertices.append(tuple(map(float,parts[1:4])))
            elif parts[0]=='g':group=parts[1]
            elif parts[0]=='f' and group.endswith('__display_surface'):
                surface.extend(vertices[int(index.split('/')[0])-1] for index in parts[1:])
        assert len(surface)==4,(path,'one screen quad')
        assert abs(min(v[0] for v in surface)-(0 if mask&8 else .125))<1e-8,path
        assert abs(max(v[0] for v in surface)-(1 if mask&2 else .875))<1e-8,path
        assert abs(min(v[2] for v in surface)-(0 if kind=='flat' and mask&1 else .125))<1e-8,path
        assert abs(max(v[2] for v in surface)-(1 if kind=='flat' and mask&4 else .875))<1e-8,path
    native=json.loads((ASSETS/'models/block/token_slots/data_chip_slot_north_0.json').read_text())
    for path in folder.glob('*_chip.obj'):
        holder=path.read_text().split('# Native Data Chip Mount: 6 x 6 pixels')[1]
        points=[tuple(map(float,line.split()[1:4])) for line in holder.splitlines() if line.startswith('v ')]
        assert len(points)==len(native['elements'])*6*4,path
        extents=[max(p[axis] for p in points)-min(p[axis] for p in points) for axis in range(3)]
        expected=[(max(e['to'][axis] for e in native['elements'])-min(e['from'][axis] for e in native['elements']))/16 for axis in range(3)]
        assert all(abs(a-b)<1e-8 for a,b in zip(extents,expected)),(path,extents,expected)
        assert max(p[2] for p in points)<0,(path,'mount must clear the front casing')
        top=(14 if kind=='flat' else 8)/16
        assert abs(max(p[1] for p in points)-(top-.5/16))<1e-8,path
print('Navigation emitted geometry PASS joined display edges and 12 native-scale mounts with top clearance')
for spec in specs:
    surfaces=spec.get('surfaces',[])
    assert len(surfaces)<=3 and len({s['id'] for s in surfaces})==len(surfaces),spec['design']
    for surface in surfaces:
        points=surface['corners'];normal=surface['normal'];assert len(points)==4 and len(normal)==3
        assert abs(sum(n*n for n in normal)-1)<1e-6,spec['design']
        for point in points:
            assert len(point)==3 and all(math.isfinite(v) and -.001<=v<=limit+.001 for v,limit in zip(point,[spec['width'],spec['height'],spec['depth']])),(spec['design'],point)
            assert abs(sum((point[i]-points[0][i])*normal[i] for i in range(3)))<1e-6,spec['design']
    if spec.get('chip_anchor'):
        for path in ['static.obj','active/static.obj']:
            text=(ASSETS/'models/block'/spec['model']/path).read_text();assert 'g card_holder' not in text,(spec['design'],path)
print('Machine display metadata PASS authored finite coplanar planes and holder-free body meshes')
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
