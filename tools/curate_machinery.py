"""Remove retired machinery families and obsolete variants reproducibly."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
REMOVED={'rivet_deck_large_g64','rivet_gravity_large_g64','rivet_shield_large_g64','rivet_sensor_large_g64','rivet_external_sensor_g64','rivet_ref_reactor_large','rivet_ref_crossflow_large','rivet_ref_dampener_large','hadron_tractor_large','hadron_core_large','hadron_dampener_large','hadron_gravity_large','hadron_external_sensor_g64','kestrel_hull_plate_large','kestrel_shield_capacitor_large','kestrel_external_sensor_g64','kestrel_power_deck_large','kestrel_reactor_block_large'}
RETIRED_PREFIXES = ('rivet_', 'vektor_', 'kestrel_', 'hadron_')
CATALOGS = ('ship_system_meshes.json', 'vh_system_meshes.json', 'rivet_system_meshes.json',
            'external_sensor_meshes.json', 'rivet_reference_meshes.json')


def removed(name):
 return name in REMOVED or name.startswith(('aster_',) + RETIRED_PREFIXES)


def main():
 removed_ids=set()
 for name in CATALOGS:
  p=ASSETS/'data'/name
  if not p.exists():continue
  models=json.loads(p.read_text());keep=[]
  for m in models:
   if removed(m.get('block_id',m['id'].removesuffix('_on').removesuffix('_off'))):
    if m.get('block_id'):removed_ids.add(m['block_id'])
    continue
   keep.append(m)
  if keep:p.write_text(json.dumps(keep,separators=(',',':'))+'\n')
  else:p.unlink()
 # Also remove stale assets whose catalog entry was already removed.
 for folder in ['blockstates','models/block','models/item','recipes']:
  for p in (ASSETS/folder).glob('*.json'):
   if removed(p.stem):p.unlink()
 textures=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/ship_systems'
 for p in textures.rglob('*.png'):
  if any(part.startswith(RETIRED_PREFIXES) for part in p.relative_to(textures).parts):p.unlink()
 for langroot in (ASSETS, ROOT/'generated-resources/assets/vandorlabs'):
  for p in (langroot/'lang').glob('*.lang'):
   lines=p.read_text(encoding='utf-8').splitlines()
   keep=[line for line in lines if not line.startswith(tuple('tile.'+prefix for prefix in RETIRED_PREFIXES))]
   if keep!=lines:p.write_text('\n'.join(keep)+'\n',encoding='utf-8')
 catalog=ROOT/'generated-resources/assets/vandorlabs/data/blocks.json';data=json.loads(catalog.read_text())
 print(type(data),len(data),len(removed_ids))
 if isinstance(data,list):data=[b for b in data if not removed(b.get('name',b.get('id','')))]
 else:
  for key in list(data):
   if removed(key):del data[key]
 catalog.write_text(json.dumps(data,indent=2)+'\n')
 print('Removed',sorted(removed_ids))
if __name__=='__main__':main()
