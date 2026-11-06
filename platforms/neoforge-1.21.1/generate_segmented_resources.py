#!/usr/bin/env python3
"""Copy the released Segmented Door panel; edge/pane artwork uses shared native assets."""
from pathlib import Path
from PIL import Image
ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / 'build/generated/segmented-resources'
source = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/segmented/segmented_panel.png'
target = OUT / 'assets/vandorlabs/textures/segmented-artwork/panel.png'
with Image.open(source) as image:
    if image.size != (32, 32):
        raise ValueError('Released Segmented Door panel dimensions changed')
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(source.read_bytes())
