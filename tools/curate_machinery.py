"""Curate industrial machinery and remove decorative micro fasteners reproducibly."""
import json
from collections import defaultdict
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
REMOVED={'rivet_deck_large_g64','rivet_gravity_large_g64','rivet_shield_large_g64','rivet_sensor_large_g64','rivet_external_sensor_g64','rivet_ref_reactor_large','rivet_ref_crossflow_large','rivet_ref_dampener_large','hadron_tractor_large','hadron_core_large','hadron_dampener_large','hadron_gravity_large','hadron_external_sensor_g64','kestrel_hull_plate_large','kestrel_shield_capacitor_large','kestrel_external_sensor_g64','kestrel_power_deck_large','kestrel_reactor_block_large'}
def removed(name):return name in REMOVED or name.startswith('aster_') or name.startswith('vektor_') and not name.startswith('vektor_console')
def micro_parts(model):
 parts=defaultdict(list)
 for f in model['faces']:parts[f['part']].append(f)
 result=set()
 for part,faces in parts.items():
  ext=[max(v[i] for f in faces for v in f['v'])-min(v[i] for f in faces for v in f['v']) for i in range(3)]
  mats={f['base_material'] for f in faces}
  # Authored hexagonal grey heads and black caps: at most four source pixels.
  if max(ext)<=4/64+1e-7 and mats <= {'edge','dark'}:result.add(part)
 return result
def repair_display(model):
 if not model['id'].startswith('vektor_console'):return
 groups=defaultdict(list)
 for f in model['faces']:
  if f['base_material'] in ('display','display_off'):groups[(f['part'],tuple(round(n,5) for n in f['normal']))].append(f)
 for faces in groups.values():
  ref=faces[0];axes=sorted(range(3),key=lambda i:abs(ref['normal'][i]))[:2]
  a,b,c=ref['v'][:3];u,v,w=ref['uv'][:3]
  d1=[b[i]-a[i] for i in axes];d2=[c[i]-a[i] for i in axes];det=d1[0]*d2[1]-d1[1]*d2[0]
  if abs(det)<1e-12:continue
  for f in faces[1:]:
   f['material']=ref['material'];f['uv']=[]
   for p in f['v']:
    q=[p[i]-a[i] for i in axes];s=(q[0]*d2[1]-q[1]*d2[0])/det;t=(d1[0]*q[1]-d1[1]*q[0])/det
    f['uv'].append([u[i]+s*(v[i]-u[i])+t*(w[i]-u[i]) for i in range(2)])
def main():
 removed_ids=set()
 for name in ['ship_system_meshes.json','vh_system_meshes.json','rivet_system_meshes.json','external_sensor_meshes.json','rivet_reference_meshes.json']:
  p=ASSETS/'data'/name;models=json.loads(p.read_text());keep=[]
  for m in models:
   if removed(m.get('block_id',m['id'].removesuffix('_on').removesuffix('_off'))):
    if m.get('block_id'):removed_ids.add(m['block_id'])
    continue
   if m['id'].startswith('rivet_'):
    parts=micro_parts(m);m['faces']=[f for f in m['faces'] if f['part'] not in parts]
   repair_display(m);keep.append(m)
  p.write_text(json.dumps(keep,separators=(',',':'))+'\n')
 for name in removed_ids:
  for folder in ['blockstates','models/item','recipes']:(ASSETS/folder/(name+'.json')).unlink(missing_ok=True)
 catalog=ROOT/'generated-resources/assets/vandorlabs/data/blocks.json';data=json.loads(catalog.read_text())
 print(type(data),len(data),len(removed_ids))
 if isinstance(data,list):data=[b for b in data if not removed(b.get('name',b.get('id','')))]
 else:
  for key in list(data):
   if removed(key):del data[key]
 catalog.write_text(json.dumps(data,indent=2)+'\n')
 print('Removed',sorted(removed_ids))
if __name__=='__main__':main()
