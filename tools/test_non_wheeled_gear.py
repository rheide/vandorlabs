"""Check every generated ski/strut item with element and GUI rotations applied."""
import itertools,json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/vandorlabs'
recipes=set()
for family in ['ski_landing_gear','landing_strut','angled_landing_strut']:
 recipe=json.loads((ROOT/('recipes/'+family+'.json')).read_text());key=json.dumps((recipe['pattern'],recipe['key']),sort_keys=True);assert key not in recipes,'ambiguous gear recipes';recipes.add(key)
 for size in ['small','medium','large','extra_large']:
  model=json.loads((ROOT/('models/block/'+family+'_'+size+'_retracted.json')).read_text());item=json.loads((ROOT/('models/item/'+family+'_'+size+'.json')).read_text());gui=item['display']['gui'];points=[]
  for e in model['elements']:
   for f in e['faces'].values():assert all(0<=v<=16 for v in f['uv'])
   for p in itertools.product(*zip(e['from'],e['to'])):
    x,y,z=p
    if 'rotation' in e:
     r=e['rotation'];ox,oy,oz=r['origin'];x-=ox;y-=oy;z-=oz;a=math.radians(r['angle']);c,s=math.cos(a),math.sin(a)
     if r['axis']=='x':y,z=y*c-z*s,y*s+z*c
     elif r['axis']=='y':x,z=x*c+z*s,-x*s+z*c
     else:x,y=x*c-y*s,x*s+y*c
     x+=ox;y+=oy;z+=oz
    x-=8;y-=8;z-=8;rx,ry,rz=map(math.radians,gui['rotation']);x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry);y=y*math.cos(rx)-z*math.sin(rx)
    points.append((x*gui['scale'][0]+gui['translation'][0],y*gui['scale'][1]+gui['translation'][1]))
  bounds=[min(p[a] for p in points) for a in [0,1]]+[max(p[a] for p in points) for a in [0,1]]
  assert max(map(abs,bounds))<7,(family,size,bounds)
  for group in ['fixed','wheel','piston','nose_front','nose_rear']:assert (ROOT/('models/item/'+family+'_'+size+'_'+group+'.json')).is_file()
  print(family,size,'PASS padded projected bounds',[round(v,2) for v in bounds])
print('PASS all 12 generated gear variants, explicit UVs, motion groups and distinct recipes')
