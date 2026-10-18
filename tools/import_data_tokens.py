"""Import the supplied card/chip item PNGs at the standard 128-pixel resolution.

Usage: python tools/import_data_tokens.py path/to/cards-chips
The artwork is used directly, preserving transparency and its authored perspective.
"""
import argparse
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DESIGNS = ('unbranded', 'pinnacle', 'ashiro', 'paladyne', 'vektor', 'seyr', 'vandor')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    sources = [(kind, name, args.directory/(kind+('' if name == 'unbranded' else '-'+name)+'.png'))
               for kind in ('card', 'chip') for name in DESIGNS]
    for _, _, path in sources:
        if not path.is_file():
            raise FileNotFoundError(path)
    textures = ROOT/'texture-packs/additional/assets/vandorlabs/textures/items'
    models = ROOT/'src/main/resources/assets/vandorlabs/models/item'
    textures.mkdir(parents=True, exist_ok=True)
    for kind, name, path in sources:
        source = Image.open(path).convert('RGBA')
        bounds = source.getchannel('A').getbbox()
        if bounds is None:
            raise ValueError('Empty artwork: '+str(path))
        art = source.crop(bounds).convert('RGBa')
        art.thumbnail((108, 108), Image.Resampling.LANCZOS)
        art = art.convert('RGBA')
        image = Image.new('RGBA', (128, 128))
        image.paste(art, ((128-art.width)//2, (128-art.height)//2))
        identifier = 'data_'+kind+'_'+name
        image.save(textures/(identifier+'.png'), optimize=True)
        model = {'parent': 'item/generated',
                 'textures': {'layer0': 'vandorlabs:items/'+identifier}}
        (models/(identifier+'.json')).write_text(json.dumps(model, indent=2)+'\n')


if __name__ == '__main__':
    main()
