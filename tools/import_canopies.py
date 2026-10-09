"""Update canopy geometry and detailed textures, preserving registered model IDs."""
import argparse
import json
from pathlib import Path
from planar_surfaces import clean, overlap_count
from kit_textures import import_textures

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kit', type=Path)
    kit = parser.parse_args().kit
    target = ROOT / 'src/main/resources/assets/vandorlabs/data/canopy_meshes.json'
    models = json.loads((kit / 'model_catalog.json').read_text(encoding='utf-8'))
    old = json.loads(target.read_text(encoding='utf-8'))
    assert {m['id'] for m in models} == {m['id'] for m in old}
    overlaps = clean(models)
    assert overlap_count(models) == 0
    textures = kit / 'forge/resources/assets/canopykit/textures/blocks'
    materials = {f['material'] for m in models for f in m['faces']}
    assert all((textures / (m+'.png')).is_file() for m in materials)
    destination = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/canopy'
    destination.mkdir(parents=True, exist_ok=True)
    import_textures(models, textures, destination)
    target.write_text(json.dumps(models, separators=(',', ':'))+'\n', encoding='utf-8')
    print(f'Imported {len(models)} canopies; clipped {overlaps} coplanar overlaps')


if __name__ == '__main__':
    main()
