"""Verify a full-size straight rear rim in every hexagonal propulsion state."""
from flatten_thruster_rears import MODELS, FAMILIES
for family in FAMILIES:
    for state in ('off', 'on', 'stream'):
        vertices=[]
        material=None
        rear=[]
        side=[]
        for line in (MODELS/f'{family}_hexagonal_{state}.obj').read_text().splitlines():
            if line.startswith('v '):vertices.append(tuple(map(float,line.split()[1:])))
            elif line.startswith('usemtl '):material=line.split()[1]
            elif line.startswith('f '):
                points=[vertices[int(t.split('/')[0])-1] for t in line.split()[1:]]
                if material=='rear':rear.extend(points)
                if material=='side':side.extend(points)
                assert not (material=='dark_trim' and min(p[2] for p in points)>.96), (family,state)
        assert len(rear)==18 and len(side)==24, (family,state)
        assert all(p[2]==1 for p in rear), (family,state)
        assert min(p[0] for p in rear)==0 and max(p[0] for p in rear)==1, (family,state)
        assert max(p[2] for p in side)==1, (family,state)
        assert abs(min(p[1] for p in rear)-(.5-3**.5/4))<1e-7, (family,state)
print('PASS: four hexagonal propulsion families, three states, straight full-size rear rim')
