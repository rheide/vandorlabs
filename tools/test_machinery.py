"""Machinery curation and coplanar display continuity contracts."""
import json,unittest
from collections import defaultdict
from pathlib import Path
from curate_machinery import removed,micro_parts
ROOT=Path(__file__).resolve().parents[1]
class MachineryTests(unittest.TestCase):
 def test_catalog_and_assets(self):
  ids=set()
  for path in (ROOT/'src/main/resources/assets/vandorlabs/data').glob('*meshes.json'):
   if path.name not in ['ship_system_meshes.json','vh_system_meshes.json','rivet_system_meshes.json','external_sensor_meshes.json','rivet_reference_meshes.json']:continue
   for m in json.loads(path.read_text()):
    self.assertFalse(removed(m.get('block_id',m['id'].removesuffix('_on').removesuffix('_off'))),m['id'])
    if m.get('block_id'):ids.add(m['block_id'])
    if m['id'].startswith('rivet_'):self.assertEqual(micro_parts(m),set())
  self.assertEqual(len(ids),10)
  for name in ids:self.assertTrue((ROOT/'src/main/resources/assets/vandorlabs/recipes'/(name+'.json')).exists())
 def test_navigation_display_uv_continuity(self):
  models=json.loads((ROOT/'src/main/resources/assets/vandorlabs/data/vh_system_meshes.json').read_text())
  tested=0
  for m in models:
   if not m['id'].startswith('vektor_console'):continue
   groups=defaultdict(list)
   for f in m['faces']:
    if f['base_material'] in ('display','display_off'):groups[(f['part'],tuple(round(n,5) for n in f['normal']))].append(f)
   for faces in groups.values():
    vertices={};materials=set()
    for f in faces:
     materials.add(f['material'])
     for v,uv in zip(f['v'],f['uv']):
      key=tuple(v)
      if key in vertices:
       self.assertAlmostEqual(vertices[key][0],uv[0],places=6)
       self.assertAlmostEqual(vertices[key][1],uv[1],places=6)
      vertices[key]=uv
    self.assertEqual(len(materials),1);tested+=1
  self.assertGreater(tested,30)
if __name__=='__main__':unittest.main()
