"""Import full-detail canopy assets; retired machinery families stay excluded."""
import argparse
import json
import shutil
from pathlib import Path
from kit_textures import import_textures
from planar_surfaces import clean, overlap_count
from curate_machinery import main as curate

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT/'src/main/resources/assets/vandorlabs'
TEXTURES = ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('collection', type=Path)
    source = parser.parse_args().collection/'canopy-kit'
    models = json.loads((source/'model_catalog.json').read_text())
    # Imported kit names are authoring keys, never registry compatibility aliases.
    ids={'opening_slide_1x2':'canopy','large_front_canopy_glass':'front_canopy_glass',
         'opening_slide_2x2':'canopy_mounts_double'}
    retained={'angled_front_1x1','angled_front_1x2','regular_canopy_glass',
              'space_visor_2x1','space_visor_3x1','space_visor_5x1','space_visor_5x1_double_height'}
    models=[model for model in models if model['id'] in retained or model['id'] in ids
            or model['id'].startswith(('regular_1x1_','angled_front_1x1_','angled_front_1x2_'))]
    for model in models:
        model['id']=ids.get(model['id'],model['id'])
        if model['id']=='canopy_mounts_double':model['faces']=[face for face in model['faces'] if face['group']=='mount']
    clipped = clean(models)
    assert overlap_count(models) == 0
    target = (TEXTURES/'canopy').resolve()
    assert target.is_relative_to(ROOT.resolve()) and target.parent == TEXTURES.resolve()
    if target.exists():
        shutil.rmtree(target)
    target.mkdir()
    import_textures(models, source/'forge/resources/assets/canopykit/textures/blocks', target)
    (ASSETS/'data/canopy_meshes.json').write_text(json.dumps(models, separators=(',', ':'))+'\n', encoding='utf-8')
    curate()
    print('Imported', len(models), 'canopy meshes;', clipped, 'coplanar overlaps clipped.')


if __name__ == '__main__':
    main()
