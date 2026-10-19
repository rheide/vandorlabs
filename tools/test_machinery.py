"""All imported machinery families remain retired."""
import json
from pathlib import Path
import unittest
from curate_machinery import removed
ROOT=Path(__file__).resolve().parents[1]
PREFIXES=('aster_','rivet_','vektor_','kestrel_','hadron_')
class MachineryTests(unittest.TestCase):
 def test_curation_rejects_all_families(self):
  for prefix in PREFIXES:
   self.assertTrue(removed(prefix+'console'))
   self.assertTrue(removed(prefix+'future_model'))
  self.assertFalse(removed('programmable_block'))
 def test_assets_are_absent(self):
  for directory in ('src/main/resources/assets/vandorlabs','generated-resources/assets/vandorlabs','texture-packs/additional/assets/vandorlabs'):
   for path in (ROOT/directory).rglob('*'):
    if path.is_file():
     relative=path.relative_to(ROOT/directory).as_posix()
     # Vektor company art is independent of the retired Vektor machinery kit.
     company_art=(relative.startswith(('models/item/armor/','textures/items/armor/','textures/models/armor/companies/','textures/blocks/corp/')) and path.stem.startswith('vektor_'))
     if not company_art:self.assertFalse(any(part.startswith(PREFIXES) for part in path.parts),str(path.relative_to(ROOT)))
  for name in ('ship_system_meshes','vh_system_meshes','rivet_system_meshes','external_sensor_meshes','rivet_reference_meshes'):
   self.assertFalse((ROOT/'src/main/resources/assets/vandorlabs/data'/(name+'.json')).exists())
 def test_generated_catalog_excludes_retired_blocks(self):
  for block in json.loads((ROOT/'generated-resources/assets/vandorlabs/data/blocks.json').read_text()):
   self.assertFalse(block['id'].startswith(PREFIXES),block['id'])
if __name__=='__main__':unittest.main()
