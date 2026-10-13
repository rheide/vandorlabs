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
