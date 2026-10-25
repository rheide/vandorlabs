#!/usr/bin/env python3
"""Clip the existing native hexagonal OBJ engines into seamless 2x2 and 3x3 assembly cells."""
import json
import math
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'generated-resources/assets/vandorlabs'
MODELS=ASSETS/'models/block'
FAMILIES=('rocket_thruster','ion_drive','plasma_vent','impulse_engine')
PARTS=((0,0,'bottom_left'),(1,0,'bottom_right'),(0,1,'top_left'),(1,1,'top_right'))
SIZES=(2,3)
def parts(size):return PARTS if size==2 else tuple((x,y,f's{size}_x{x}_y{y}') for y in range(size) for x in range(size))
ROTATIONS={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270},'up':{'x':270},'down':{'x':90}}

def read_faces(path,size=2):
    vertices=[];uvs=[];normals=[];faces=[];material=''
    for line in path.read_text().splitlines():
        bits=line.split()
        if not bits:continue
        if bits[0]=='v':vertices.append(tuple(map(float,bits[1:4])))
        elif bits[0]=='vt':uvs.append(tuple(map(float,bits[1:3])))
        elif bits[0]=='vn':normals.append(tuple(map(float,bits[1:4])))
        elif bits[0]=='usemtl':material=bits[1]
        elif bits[0]=='f':
            polygon=[]
            for token in bits[1:]:
                v,t,n=map(int,token.split('/'));x,y,z=vertices[v-1];u,w=uvs[t-1];nx,ny,nz=normals[n-1]
                polygon.append((x*size,y*size,z,u,w,nx/size,ny/size,nz))
            faces.append((material,polygon))
    return faces

def clip(polygon,axis,plane,lower):
    if not polygon:return []
    output=[];last=polygon[-1];before=last[axis]>=plane-1e-10 if lower else last[axis]<=plane+1e-10
    for point in polygon:
        after=point[axis]>=plane-1e-10 if lower else point[axis]<=plane+1e-10
        if before!=after:
            amount=(plane-last[axis])/(point[axis]-last[axis]);output.append(tuple(a+(b-a)*amount for a,b in zip(last,point)))
        if after:output.append(point)
        last=point;before=after
    return output

def area(polygon):
    total=0
    for i in range(1,len(polygon)-1):
        a=[polygon[i][j]-polygon[0][j] for j in range(3)];b=[polygon[i+1][j]-polygon[0][j] for j in range(3)]
        total+=math.sqrt(sum(v*v for v in (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])))/2
    return total

def clipped(faces,x,y,name,mode):
    lines=['# Generated from the existing native hexagonal engine; model units are blocks.',f'mtllib {name}_hexagonal_{mode}.mtl','o connected_hexagonal_cell'];index=1;covered=0
    for material,source in faces:
        polygon=source
        for axis,plane,lower in ((0,x,True),(0,x+1,False),(1,y,True),(1,y+1,False)):polygon=clip(polygon,axis,plane,lower)
        if len(polygon)<3 or area(polygon)<1e-12:continue
        covered+=area(polygon)
        # Forge's OBJ quad builder accepts both true quads and degenerate quad triangles.
        pieces=[polygon] if len(polygon)==4 else [[polygon[0],polygon[i],polygon[i+1],polygon[i+1]] for i in range(1,len(polygon)-1)]
        for face in pieces:
            if area(face)<1e-12:continue
            for p in face:
                lines.append('v %.10f %.10f %.10f'%(p[0]-x,p[1]-y,p[2]))
                lines.append('vt %.10f %.10f'%(p[3],p[4]));length=math.sqrt(sum(n*n for n in p[5:]));lines.append('vn %.10f %.10f %.10f'%tuple(n/length for n in p[5:]))
            lines.append('usemtl '+material);lines.append('f '+' '.join(f'{index+i}/{index+i}/{index+i}' for i in range(4)));index+=4
    return '\n'.join(lines)+'\n',covered

def generate(check=False):
    outputs={}
    for name in FAMILIES:
        for mode in ('off','on','stream'):
            for size in SIZES:
                faces=read_faces(MODELS/f'{name}_hexagonal_{mode}.obj',size);native=sum(area(face) for _,face in faces);covered=0
                for x,y,part in parts(size):
                    content,amount=clipped(faces,x,y,name,mode);covered+=amount;outputs[MODELS/f'{name}_hexagonal_{mode}_{part}.obj']=content
                if abs(native-covered)>native*1e-7:raise AssertionError(f'{name}/{mode}/{size}: clipped surface area {covered} != native {native}')
        variants={}
        for facing,rotation in ROTATIONS.items():
            for powered in (False,True):
                for particles in (False,True):
                    mode='stream' if powered and particles else 'on' if powered else 'off'
                    for part in ('single',)+tuple(part for size in SIZES for _,_,part in parts(size)):
                        suffix='' if part=='single' else '_'+part
                        variants[f'facing={facing},part={part},particles={str(particles).lower()},powered={str(powered).lower()}']={'model':f'vandorlabs:{name}_hexagonal_{mode}{suffix}.obj',**rotation}
        # Configured picks use this exact single-engine inventory mesh, never a joined quadrant.
        inventory=MODELS/f'{name}_hexagonal_inventory.obj'
        assert inventory.is_file(),f'Missing original single-engine inventory OBJ: {inventory}'
        inventory_faces=read_faces(inventory,1)
        assert inventory_faces and all(0<v[axis]<1 for _,face in inventory_faces for v in face for axis in range(3)),f'Original inventory OBJ must remain padded: {inventory}'
        variants['inventory']=[{'model':f'vandorlabs:{name}_hexagonal_inventory.obj','y':0,'transform':'forge:default-block'}]
        outputs[ASSETS/'blockstates'/f'{name}_hexagonal.json']=json.dumps({'forge_marker':1,'defaults':{'textures':{'particle':'vandorlabs:blocks/thrusters/side'},'custom':{'flip-v':True}},'variants':variants},indent=2)+'\n'
    for path,content in outputs.items():
        if check:
            if not path.exists() or path.read_text()!=content:raise AssertionError(f'Stale connected hexagonal asset: {path.relative_to(ROOT)}')
        else:path.write_text(content)
    print('Connected hexagonal thrusters PASS: 156 native clipped OBJ cells (2x2+3x3), original solo inventory entries, four family blockstates, exact surface-area conservation')
if __name__=='__main__':generate('--check' in sys.argv)
