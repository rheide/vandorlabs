"""Canonical glass palette projection, independent of panel model resources."""
import re
from pathlib import Path
from PIL import Image


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

