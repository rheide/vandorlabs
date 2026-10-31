#!/usr/bin/env python3
"""Generate conservative LabPBR 1.3 specular companions after runtime compaction.

The diffuse artwork is never modified. Normal/height maps are deliberately not
inferred from brightness: painted markings and baked shadows are not geometry.
"""
import argparse
import collections
import io
import json
import re
from pathlib import Path
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
PROFILES = {
    # perceptual smoothness, dielectric F0, porosity, exposed neutral metal ID,
    # maximum smoothness variation (wear, not displacement)
    'neutral': (0, 10, 0, None, 0),
    # Uniform coatings share maps even when their diffuse colors differ.
    'paint': (74, 10, 0, None, 0),
    'matte': (28, 10, 0, None, 0),
    'satin': (112, 10, 0, None, 0),
    'satin_silver': (160, 10, 0, 237, 4),
    'titanium': (150, 10, 0, 230, 4),
    'fabric': (12, 10, 38, None, 0),
    'rubber': (24, 10, 0, None, 0),
    'stone': (32, 10, 48, None, 5),
    'composite': (92, 10, 0, None, 0),
    'brushed_steel': (138, 10, 0, 230, 10),
    'brushed_alloy': (154, 10, 0, 232, 8),
    'polished_metal': (204, 10, 0, 230, 6),
    'frame': (102, 10, 0, 230, 7),
    'glass': (228, 10, 0, None, 0),
    'display': (158, 10, 0, None, 0),
}


def catalog_hints():
    """Retain names of generic imported filenames and all resolution variants."""
    entries = json.loads((ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())
    menu = json.loads((ROOT/'src/main/resources/assets/vandorlabs/data/texture_menu.json').read_text())
    java = (ROOT/'src/main/java/com/vandorlabs/tiles/ScreenHousingTextures.java').read_text()
    entries += [dict(source=source, id=identifier, **menu.get(identifier, {}))
                for identifier, source in re.findall(r'new Finish\("([^"]+)",\s*"([^"]+)"\)', java)]
    result = {}
    for entry in entries:
        hint = ' '.join(str(entry.get(key, '')) for key in ('id', 'label', 'category', 'menuGroup'))
        for key in ('source', 'unlit'):
            if entry.get(key):
                result.setdefault('blocks/'+entry[key]+'.png', []).append(hint)
    return {key: ' '.join(values) for key, values in result.items()}


def classify(relative, hint=''):
    name = relative.lower()
    text = re.sub(r'[_/.-]+', ' ', name+' '+hint.lower())
    if not name.startswith(('blocks/', 'block/', 'items/', 'models/')) or '/dynmap/' in name:
        return 'neutral'  # GUI/icon/particle art is not a physical block finish.
    if '/armor/roles/' in name and not any(word in text for word in ('helmet', 'hazmat', 'security')):
        return 'fabric'
    if 'satin silver' in text:
        return 'satin_silver'
    if 'titanium' in text:
        return 'titanium'
    if any(word in text for word in ('carpet', 'padding', 'upholstery', 'fabric', 'seat cushion')):
        return 'fabric'
    if 'rubber' in text:
        return 'rubber'
    # Explicit finish names take precedence over broad category defaults.
    if any(word in text for word in ('matte', 'weathered', 'worn', 'rust')):
        return 'matte'
    if 'satin' in text:
        return 'satin'
    if any(word in text for word in ('basalt', 'concrete', 'ancient relief')):
        return 'stone'
    if any(word in text for word in ('nano fiber', 'composite', 'carbon')):
        return 'composite'
    if any(word in text for word in ('polished', 'chrome')):
        return 'polished_metal'
    # Native panes only; glass-door atlases also contain opaque metal frames.
    if re.search(r'/programmable_glass/(low|medium)/', name) or '/security_glass/' in name or name.endswith('/glass.png'):
        return 'glass'
    if any(word in text for word in ('screen', 'console inputs', 'indicator atlas')):
        return 'display'
    if any(word in text for word in ('frame', 'glass door', 'window', 'door side', 'metal side')):
        return 'frame'
    if 'alloy' in text or 'aluminium' in text or 'aluminum' in text:
        return 'brushed_alloy'
    if any(word in text for word in ('steel', 'gunmetal', 'metal floor', 'metal cabinet', 'metal drawers', 'metal overhead', 'wall pipes', 'perforated', 'grate', 'ribbed wall')):
        return 'brushed_steel'
    # Uncertain surfaces stay coated/dielectric instead of inventing bare metal.
    return 'paint'


def companion(image, profile):
    smoothness, f0, porosity, metal, variation = PROFILES[profile]
    rgba = image.convert('RGBA')
    red, green, blue, alpha = rgba.split()
    luminance = rgba.convert('L')
    smooth = luminance.point([max(0, min(254, smoothness+round((v-128)*variation/128))) for v in range(256)])
    reflectance = Image.new('L', image.size, f0)
    if metal is not None:
        # Bare neutral metal may reflect as a conductor. Coloured paint, lit
        # accents and black recesses retain their dielectric response.
        spread = ImageChops.subtract(ImageChops.lighter(ImageChops.lighter(red, green), blue),
                                    ImageChops.darker(ImageChops.darker(red, green), blue))
        # Blue-grey gunmetal is still bare metal; avoid splitting its subtly
        # tinted panels into a noisy mixture of conductor and dielectric.
        neutral = spread.point([255 if v <= 48 else 0 for v in range(256)])
        visible = luminance.point([255 if v >= 36 else 0 for v in range(256)])
        mask = ImageChops.multiply(neutral, visible)
        reflectance = Image.composite(Image.new('L', image.size, metal), reflectance, mask)
    occupied = alpha.point([255 if v else 0 for v in range(256)])
    smooth = ImageChops.multiply(smooth, occupied)
    reflectance = ImageChops.multiply(reflectance, occupied)
    porous = ImageChops.multiply(Image.new('L', image.size, porosity), occupied)
    # 255 is LabPBR's explicit NO-emission sentinel, not diffuse opacity.
    return Image.merge('RGBA', (smooth, reflectance, porous, Image.new('L', image.size, 255)))


def png(image):
    output = io.BytesIO()
    image.save(output, format='PNG', optimize=True)
    return output.getvalue()


def stage(directory):
    textures = directory/'textures'
    hints = catalog_hints()
    paths = sorted(p for p in textures.rglob('*.png') if not p.stem.endswith(('_s', '_n')))
    profiles = collections.Counter()
    entries = {}
    for path in paths:
        relative = path.relative_to(textures).as_posix()
        profile = classify(relative, hints.get(relative, ''))
        with Image.open(path) as source:
            result = companion(source, profile)
            data = png(result)
            size = list(source.size)
        target = path.with_name(path.stem+'_s.png')
        target.write_bytes(data)
        metadata = path.with_suffix('.png.mcmeta')
        if metadata.exists():
            target.with_suffix('.png.mcmeta').write_bytes(metadata.read_bytes())
        profiles[profile] += 1
        entries['textures/'+relative] = dict(profile=profile, size=size)
    manifest = directory/'data/pbr_materials.json'
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text(json.dumps(dict(format='lab-pbr/1.3', maps=entries), sort_keys=True, separators=(',', ':'))+'\n')
    print('LabPBR specular companions: '+str(len(paths))+' textures; '+str(dict(sorted(profiles.items()))))


def check_jar(path):
    from zipfile import ZipFile
    with ZipFile(path) as jar:
        prefix = 'assets/vandorlabs/'
        aliases = json.loads(jar.read(prefix+'data/texture_aliases.json'))
        entries = json.loads(jar.read(prefix+'data/pbr_materials.json'))['maps']
        def read(name):
            return jar.read(prefix+aliases.get(name, name))
        base_bytes = companion_bytes = pixels = 0
        profiles = collections.Counter()
        for name, entry in entries.items():
            diffuse = Image.open(io.BytesIO(read(name))).convert('RGBA')
            spec_name = name[:-4]+'_s.png'
            spec = Image.open(io.BytesIO(read(spec_name))).convert('RGBA')
            assert spec.size == diffuse.size == tuple(entry['size']), name
            assert spec.tobytes() == companion(diffuse, entry['profile']).tobytes(), name
            assert spec.getchannel('A').getextrema() == (255, 255), name
            metadata = prefix+name+'.mcmeta'
            if metadata in jar.namelist():
                assert jar.read(prefix+spec_name+'.mcmeta') == jar.read(metadata), name
            profiles[entry['profile']] += 1
            pixels += spec.width*spec.height
        # Cover physical sources AND logical compatibility/duplicate paths.
        all_names = {n[len(prefix):] for n in jar.namelist() if n.startswith(prefix+'textures/')}
        all_names.update(aliases)
        bases = {n for n in all_names if n.endswith('.png') and not n.endswith(('_s.png', '_n.png'))}
        assert bases == set(entries), (len(bases), len(entries), sorted(bases-set(entries))[:5])
        for info in jar.infolist():
            if info.filename.startswith(prefix+'textures/') and info.filename.endswith('.png'):
                if info.filename.endswith('_s.png'): companion_bytes += info.file_size
                else: base_bytes += info.file_size
        report = dict(logical_textures=len(entries), profiles=dict(sorted(profiles.items())),
                      physical_companion_pngs=sum(1 for i in jar.infolist() if i.filename.startswith(prefix+'textures/') and i.filename.endswith('_s.png')),
                      shared_companion_paths=sum(1 for n in aliases if n.endswith('_s.png')),
                      base_png_bytes=base_bytes, companion_png_bytes=companion_bytes,
                      companion_zip_bytes=sum(i.compress_size for i in jar.infolist() if i.filename.endswith('_s.png')),
                      logical_rgba_bytes=pixels*4,
                      logical_rgba_mipmapped_bytes=round(pixels*4*4/3), jar_bytes=path.stat().st_size)
        print(json.dumps(report, indent=2))
        print('LabPBR companions PASS: complete coverage, semantic pixel data, dimensions, animation metadata, no accidental emission')
        return report


def preview_jar(path, destination):
    """Review encoded channels; this is explicitly not a shader render."""
    from zipfile import ZipFile
    from PIL import ImageDraw
    samples = ['light_alloy_hull', 'dark_gunmetal_hull', 'midnight_matte_hull',
               'midnight_satin_hull', 'modular_polished_metal_floor_1',
               'bluegray_carpet', 'intricate_composite_wall_1',
               'frames/frames-door-window/vandor_bg', 'programmable_glass/medium/cyan']
    with ZipFile(path) as jar:
        prefix = 'assets/vandorlabs/'
        aliases = json.loads(jar.read(prefix+'data/texture_aliases.json'))
        entries = json.loads(jar.read(prefix+'data/pbr_materials.json'))['maps']
        def load(name):
            return Image.open(io.BytesIO(jar.read(prefix+aliases.get(name, name)))).convert('RGBA')
        sheet = Image.new('RGB', (720, 50+len(samples)*108), (26, 32, 39))
        draw = ImageDraw.Draw(sheet)
        draw.text((12, 8), 'Material data review (not a shader render)', fill='white')
        for x, title in [(270, 'Diffuse'), (410, 'Smoothness'), (550, 'Metal mask')]:
            draw.text((x, 30), title, fill='white')
        for index, source in enumerate(samples):
            name = 'textures/blocks/'+source+'.png'
            material = load(name[:-4]+'_s.png')
            channels = [load(name), material.getchannel('R').convert('RGBA'),
                        material.getchannel('G').point([255 if v>=230 else 0 for v in range(256)]).convert('RGBA')]
            y = 50+index*108
            draw.text((12, y+28), source.split('/')[-1][:32], fill='white')
            draw.text((12, y+45), entries[name]['profile'], fill=(170, 190, 210))
            for x, image in zip((270, 410, 550), channels):
                image.thumbnail((96, 96), Image.Resampling.NEAREST)
                if image.width < 96 and image.height < 96:
                    scale = min(96//image.width, 96//image.height)
                    image = image.resize((image.width*scale, image.height*scale), Image.Resampling.NEAREST)
                sheet.paste(image, (x, y), image)
        destination.parent.mkdir(parents=True, exist_ok=True)
        sheet.save(destination)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', type=Path)
    parser.add_argument('--check-jar', type=Path)
    parser.add_argument('--preview', type=Path, help='Channel-review PNG for --check-jar')
    args = parser.parse_args()
    if args.stage: stage(args.stage)
    if args.check_jar: check_jar(args.check_jar)
    if args.preview:
        if not args.check_jar: parser.error('--preview requires --check-jar')
        preview_jar(args.check_jar, args.preview)
    if not args.stage and not args.check_jar: parser.error('provide --stage or --check-jar')
