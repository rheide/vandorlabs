"""Trim co-oriented coplanar Pilot Seat triangles, retaining the later detail surface."""
from pathlib import Path

EPS = 1e-9

def area(poly, axes):
    u, v = axes
    return sum(a[u]*b[v]-b[u]*a[v] for a,b in zip(poly,poly[1:]+poly[:1]))/2

def subtract(poly, cut, axes):
    u,v=axes
    sign=1 if area(cut,axes)>0 else -1
    remaining=poly; pieces=[]
    for a,b in zip(cut,cut[1:]+cut[:1]):
        def distance(p):return sign*((b[u]-a[u])*(p[v]-a[v])-(b[v]-a[v])*(p[u]-a[u]))
        inside=[];outside=[]
        for p,q in zip(remaining,remaining[1:]+remaining[:1]):
            d,e=distance(p),distance(q)
            (inside if d>=-EPS else outside).append(p)
            if d>EPS and e<-EPS or d<-EPS and e>EPS:
                t=d/(d-e);point=tuple(x+t*(y-x) for x,y in zip(p,q));inside.append(point);outside.append(point)
        if len(outside)>=3 and abs(area(outside,axes))>EPS:pieces.append(outside)
        remaining=inside
        if len(remaining)<3 or abs(area(remaining,axes))<=EPS:return [poly]
    return pieces

def clean(path, check=False):
    lines=path.read_text().splitlines();vertices=[];uvs=[];normals=[];faces=[]
    for line in lines:
        if line.startswith('v '):vertices.append(tuple(map(float,line.split()[1:])))
        elif line.startswith('vt '):uvs.append(tuple(map(float,line.split()[1:])))
        elif line.startswith('vn '):normals.append(tuple(map(float,line.split()[1:])))
    for index,line in enumerate(lines):
        if line.startswith('f '):
            refs=[tuple(int(n)-1 for n in ref.split('/')) for ref in line.split()[1:]]
            poly=[vertices[v]+uvs[t] for v,t,n in refs];normal=normals[refs[0][2]]
            axis=max(range(3),key=lambda i:abs(normal[i]));axes=tuple(i for i in range(3) if i!=axis)
            faces.append((index,poly,normal,axis,axes,refs[0][2]))
    replacements={};overlap=0;next_v=len(vertices)+1;next_uv=len(uvs)+1
    for i,(line,poly,normal,axis,axes,n) in enumerate(faces):
        pieces=[poly]
        for _,cut,other,other_axis,_,_ in faces[i+1:]:
            if normal!=other or other_axis!=axis or any(abs(sum(normal[k]*(p[k]-poly[0][k]) for k in range(3)))>EPS for p in cut):continue
            pieces=[part for piece in pieces for part in subtract(piece,cut,axes)]
        before=abs(area(poly,axes));after=sum(abs(area(p,axes)) for p in pieces)
        if abs(before-after)<EPS:continue
        overlap+=1;output=[]
        for piece in pieces:
            refs=[]
            for p in piece:
                output.append('v '+' '.join(f'{x:.9f}' for x in p[:3]));output.append('vt '+' '.join(f'{x:.9f}' for x in p[3:]));refs.append(f'{next_v}/{next_uv}/{n+1}');next_v+=1;next_uv+=1
            for j in range(1,len(refs)-1):output.append('f '+' '.join((refs[0],refs[j],refs[j+1])))
        replacements[line]=output
    if check:
        assert overlap==0,f'{path.name}: {overlap} coplanar overlapping triangles'
    elif overlap:
        output=[line for line in lines if line.startswith(('v ','vt ','vn '))]
        for replacement in replacements.values():output.extend(line for line in replacement if line.startswith(('v ','vt ')))
        for index,line in enumerate(lines):
            if not line.startswith(('v ','vt ','vn ')):output.extend(part for part in replacements.get(index,[line]) if not part.startswith(('v ','vt ')))
        path.write_text('\n'.join(output)+'\n')
    return overlap

if __name__=='__main__':
    import sys
    check='--check' in sys.argv
    paths=sorted(Path('src/main/resources/assets/vandorlabs/models/block').glob('pilot_seat*.obj'))
    count=sum(clean(path,check) for path in paths)
    print(f'Pilot Seat surfaces: {len(paths)} height/armrest meshes, '+('no coplanar overlaps' if check else f'trimmed {count} overlapping triangles'))
