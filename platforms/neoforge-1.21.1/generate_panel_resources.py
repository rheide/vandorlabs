#!/usr/bin/env python3
"""Current plain-wall render assets; geometry is emitted from shared kernels at model build time."""
import json
import re
from PIL import Image
from pathlib import Path
import shutil
from generate_shape_resources import converted

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/main/resources/assets/vandorlabs'
OUT = Path(__file__).resolve().parent / 'build/generated/panel-resources'


def metal(value):
    if isinstance(value, dict):
        return {key: metal(item) for key, item in value.items()}
    if isinstance(value, list):
        return [metal(item) for item in value]
    return 'vandorlabs:frame-default/metal_side' if value == 'vandorlabs:blocks/programmable_glass/metal_side' else value


def generate_glass_palette(root, out):
    """Project the existing GlassTextures IDs at the standard runtime pixel resolution.

    compact_runtime_assets.standardize aliases low glass to the standardized medium
    pixels. Use that same canonical source and premultiplied Lanczos conversion.
    """
    root, out = Path(root), Path(out)
    source = (root / 'src/portable/java/com/vandorlabs/tiles/GlassTextures.java').read_text()
    match = re.search(r'String\[\] IDS=\{([^}]+)\}', source)
    if not match:
        raise ValueError('Missing canonical GlassTextures IDs')
    identifiers = re.findall(r'"([a-z_]+)"', match.group(1))
    if not identifiers or len(set(identifiers)) != len(identifiers):
        raise ValueError('Invalid canonical glass IDs')
    atlas, seen = [], set()
    for shade, name in enumerate(identifiers):
        for tint in range(4):
            suffix = '_opaque' if tint == 3 else '_strong' if tint == 2 else '' if shade == 0 else '_mild' if tint == 0 else ''
            stem = name + suffix
            if stem in seen:
                continue
            seen.add(stem)
            relative = Path('assets/vandorlabs/textures/blocks/programmable_glass/medium') / (stem + '.png')
            original = next((root / directory / relative for directory in
                             ('texture-packs/additional', 'texture-packs/default', 'src/main/resources')
                             if (root / directory / relative).is_file()), None)
            if original is None:
                raise ValueError('Missing canonical glass artwork: ' + str(relative))
            target = out / 'assets/vandorlabs/textures/glass-palette' / (stem + '.png')
            target.parent.mkdir(parents=True, exist_ok=True)
            with Image.open(original) as image:
                image = image.convert('RGBA')
                if image.size != (128, 128):
                    if image.width != image.height:
                        raise ValueError('Non-square canonical glass artwork: ' + str(original))
                    if image.width > 128:
                        image = image.convert('RGBa').resize((128, 128), Image.Resampling.LANCZOS).convert('RGBA')
                image.save(target, optimize=True)
            atlas.append(dict(type='minecraft:single', resource='vandorlabs:glass-palette/' + stem))
    return atlas


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    for name in ['programmable_wall','programmable_porthole_wall','programmable_diagonal_wall']:
        for relative in ['blockstates/'+name+'.json','models/block/'+name+'.json','models/item/'+name+'.json']:
            value = metal(converted(json.loads((SOURCE / relative).read_text())))
            if name=='programmable_diagonal_wall' and relative.startswith('blockstates/'):
                # Legacy diagonal metadata always reads depth0; the other source variants are identical.
                variants={}
                for key,variant in value['variants'].items():
                    key=','.join(part for part in key.split(',') if not part.startswith('depth='))
                    if key in variants and variants[key]!=variant:raise ValueError('Non-identical diagonal depth variants')
                    variants[key]=variant
                value['variants']=variants
            if relative.startswith('models/item/'):

                if name!='programmable_porthole_wall':
                    # Solid wall items do not use their inherited glass slot.
                    value['textures'].pop('glass', None)
                else:
                    value['textures']['glass']='vandorlabs:glass-palette/clear'
            target = OUT / 'assets/vandorlabs' / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps(value, indent=2) + '\n')
        recipe=json.loads((SOURCE/('recipes/'+name+'.json')).read_text())
        recipe['result']['id']=recipe['result'].pop('item')
        target=OUT/('data/vandorlabs/recipe/'+name+'.json');target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(recipe)+'\n')
        loot={'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:'+name,'functions':[{'function':'minecraft:copy_components','source':'block_entity','include':['minecraft:block_entity_data']}]}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]}
        target=OUT/('data/vandorlabs/loot_table/blocks/'+name+'.json');target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(loot)+'\n')
    print('Native panel assets: 56 canonical variants, current item geometry; existing housing/metal atlas reused')


if __name__ == '__main__':
    main()
