"""Extend Vektor console surfaces in depth, retaining the authored perimeter."""
import copy
import importlib.util
import json
import shutil
from planar_surfaces import clean, overlap_count


def extend_consoles(models, kit, staging):
    source = kit / 'forge/resources/assets/vhtech/textures/blocks'
    staging.mkdir(parents=True, exist_ok=True)
    for texture in source.glob('*.png'):
        shutil.copyfile(texture, staging / texture.name)
    spec = importlib.util.spec_from_file_location('console_detail_assets', kit / 'detail_assets.py')
    details = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(details)
    painter = details.Textures(json.loads((kit / 'palette.json').read_text(encoding='utf-8')), [staging])
    generated = []
    for base in models:
        if base['manufacturer'] != 'vektor' or not base['console_width']:
            continue
        for depth in (2, 3):
            model = copy.deepcopy(base)
            width = model['console_width']
            model['console_depth'] = depth
            model['stem'] = f'vektor_console_{width}_depth_{depth}'
            model['id'] = model['stem'] + ('_on' if model['active'] else '_off')
            model['bounds_xyz'] = model['occupancy_xyz'] = [width, 1, depth]
            model['label'] = f'Vektor navigation console {width} wide, {depth} deep'
            def extend(z):
                if z <= .25:
                    return z
                if z >= .75:
                    return z + depth - 1
                return .25 + (z-.25)*(depth-.5)/.5
            for face in model['faces']:
                for vertex in face['v']:
                    vertex[2] = extend(vertex[2])
            for box in model['collision']:
                box[2], box[5] = extend(box[2]), extend(box[5])
            # Regenerate texel coordinates and artwork rather than stretching pixels.
            painter.apply(model['faces'])
            generated.append(model)
    clean(generated)
    assert overlap_count(generated) == 0
    models.extend(generated)
    return staging
