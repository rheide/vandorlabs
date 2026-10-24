"""Pixel-identical whole sprites and atlas tiles have distinct Dynmap meanings."""
import tempfile
from pathlib import Path
from PIL import Image
from deduplicate_runtime_textures import stage

with tempfile.TemporaryDirectory() as temporary:
    root=Path(temporary);folder=root/'textures/blocks';folder.mkdir(parents=True)
    for name in ('whole','duplicate','atlas'):
        Image.new('RGBA',(64,64),(100,160,220,255)).save(folder/(name+'.png'))
    text=root/'dynmap-texture.txt'
    text.write_text('texture:id=whole,filename=assets/vandorlabs/textures/blocks/whole.png,xcount=1,ycount=1\ntexture:id=atlas,filename=assets/vandorlabs/textures/blocks/atlas.png,xcount=4,ycount=4\nblock:id=%fixture,state=*,patch0=3:atlas\n')
    stage(root)
    assert (folder/'atlas.png').exists(),'Atlas deduplicated into a whole sprite'
    assert len(list(folder.glob('*.png')))==2,'Ordinary duplicate was not shared'
    assert 'filename=assets/vandorlabs/textures/blocks/atlas.png,xcount=4,ycount=4' in text.read_text(),'Atlas grid points at another physical file'
print('PASS: Dynmap atlas grids survive PNG sharing; ordinary duplicate sprites still deduplicate')
