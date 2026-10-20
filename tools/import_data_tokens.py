"""Import the supplied card/chip item PNGs at the standard 128-pixel resolution.

Usage: python tools/import_data_tokens.py path/to/cards-chips
The supplied artwork keeps its original transparent sprite shape with opaque side materials.
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
        art.thumbnail((108, 108), Image.Resampling.NEAREST)
        art = art.convert('RGBA')
        art.putalpha(art.getchannel('A').point(lambda value:255 if value>=128 else 0))
        image = Image.new('RGBA', (128, 128))
        image.paste(art, ((128-art.width)//2, (128-art.height)//2))
        identifier = 'data_'+kind+'_'+name
        image.save(textures/(identifier+'.png'), optimize=True)
        display={'gui':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
                 'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1.13,3.2,1.13],'scale':[.68,.68,.68]},
                 'firstperson_lefthand':{'rotation':[0,90,-25],'translation':[1.13,3.2,1.13],'scale':[.68,.68,.68]},
                 'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.5,.5,.5]},
                 'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.5,.5,.5]},
                 'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.5,.5,.5]},
                 'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]}}
        model = {'parent':'item/generated','ambientocclusion':False,'textures':{'layer0':'vandorlabs:items/'+identifier,'edge':'vandorlabs:items/data_'+kind+'_edge','particle':'vandorlabs:items/'+identifier},'display':display}
        Image.new('RGBA',(128,128),(64,68,74,255) if kind=='card' else (40,43,48,255)).save(textures/('data_'+kind+'_edge.png'),optimize=True)
        (models/(identifier+'.json')).write_text(json.dumps(model, indent=2)+'\n')


if __name__ == '__main__':
    main()
