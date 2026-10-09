"""Preserve source texels while trimming unused atlas space and remapping UVs."""
import math
from collections import defaultdict
from PIL import Image


def import_textures(models, source, destination, prefix=''):
    destination.mkdir(parents=True, exist_ok=True)
    references = defaultdict(list)
    for model in models:
        for face in model['faces']:
            material = face['material']
            references[material[len(prefix):] if material.startswith(prefix) else material].append(face)
    total_before = total_after = 0
    for name, faces in references.items():
        with Image.open(source / (name+'.png')) as original:
            bounds = [max(uv[i] for f in faces for uv in f['uv']) for i in range(2)]
            assert all(-1e-7 <= x <= 1+1e-7 for f in faces for uv in f['uv'] for x in uv), name
            size = [min(original.size[i], max(16, 2**math.ceil(math.log2(max(1, math.ceil(bounds[i]*original.size[i])+1))))) for i in range(2)]
            # Minecraft 1.12 treats non-square sprites as animation strips and
            # rejects them without animation metadata. Static sprites must be square.
            size = [max(size)] * 2
            original.crop((0, 0, *size)).save(destination / (name+'.png'))
            assert size[0] == size[1]
            for face in faces:
                mapped = [[uv[i]*original.size[i]/size[i] for i in range(2)] for uv in face['uv']]
                # Detailed catalogs use image coordinates; the host renderer uses OBJ V.
                face['uv'] = [[u, 1-v] for u, v in mapped] if 'base_material' in face else mapped
            total_before += original.width*original.height
            total_after += size[0]*size[1]
    # Keep authored base swatches used by particle textures and overview maps.
    for texture in source.glob('*.png'):
        if texture.stem not in references and '_' not in texture.stem:
            with Image.open(texture) as original:
                original.save(destination / texture.name)
    print(f'Imported {len(references)} textures: {total_before} -> {total_after} atlas texels, without resampling')
