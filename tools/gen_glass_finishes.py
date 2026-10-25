#!/usr/bin/env python3
"""Generate seamless glass finishes with stable legacy tint and vanilla dye colors."""
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/programmable_glass'
COLORS = [('white', 0xF9FFFE), ('orange', 0xF9801D), ('magenta', 0xC74EBD),
          ('light_blue', 0x3AB3DA), ('yellow', 0xFED83D), ('lime', 0x80C71F),
          ('pink', 0xF38BAA), ('grey', 0x474F52), ('light_grey', 0x9D9D97),
          ('cyan', 0x169C9C), ('purple', 0x8932B8), ('blue', 0x3C44AA),
          ('brown', 0x835432), ('green', 0x5E7C16), ('red', 0xB02E26), ('black', 0x1D1D21)]

for detail in ('low', 'medium'):
    base = Image.open(ROOT / f'texture-packs/default/assets/vandorlabs/textures/blocks/space_doors/{detail}/glass_tile.png').convert('RGBA')
    for name, rgb, opacity in [('clear', 0xFFFFFF, 0), ('soft_cyan', 0x33D9F2, .0513),
                               ('soft_dark_grey', 0x1A1F29, .1754)] + [(n, c, .30) for n, c in COLORS]:
        tint = ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255)
        for suffix, factor in (('_mild', .4), ('', 1), ('_strong', 1.35)):
            if "--strong-only" in sys.argv and suffix != "_strong": continue
            # Preserve the existing Normal finish exactly; adjust only the color overlay.
            strength = min(.8, opacity * factor)
            image = Image.new('RGBA', base.size)
            pixels = []
            for r, g, b, a in base.getdata():
                alpha = a / 255
                combined = strength + alpha * (1 - strength)
                color = tuple(round((c * strength + original * alpha * (1 - strength)) / combined)
                              if combined else original for c, original in zip(tint, (r, g, b)))
                pixels.append((*color, 255 if suffix=="_strong" else round(combined * 255)))
            image.putdata(pixels)
            (OUT / detail).mkdir(parents=True, exist_ok=True)
            image.save(OUT / detail / (name + suffix + '.png'))
print('Generated 19 glass finishes at 3 tint strengths and both detail sizes')
