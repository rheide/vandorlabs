#!/usr/bin/env python3
"""Stage deterministic test-only artwork; never part of the production source set."""
from pathlib import Path
import os
import shutil
from PIL import Image
OUT=Path(__file__).resolve().parent/'build/generated/cube-smoke-resources'
if OUT.exists():shutil.rmtree(OUT)
reference=os.environ.get('VANDOR_CUBE_REFERENCE')
if reference:
    source=Path(reference).parent/'game/config/vandorlabs/textures/Reference/portrait.png'
    with Image.open(source) as image:
        image=image.convert('RGBA');w,h=image.size;canvas=1
        while canvas<max(w,h):canvas*=2
        padded=Image.new('RGBA',(canvas,canvas));padded.paste(image,(0,0))
        if w<canvas:padded.paste(image.crop((w-1,0,w,h)).resize((canvas-w,h)),(w,0))
        if h<canvas:padded.paste(padded.crop((0,h-1,canvas,h)).resize((canvas,canvas-h)),(0,h))
        target=OUT/'assets/vandorlabs/textures/block/cube_portrait_fixture.png';target.parent.mkdir(parents=True,exist_ok=True);padded.save(target)
