#!/usr/bin/env python3
"""Catch missing Space sprites and frozen door animation in live screenshots."""
import sys
from pathlib import Path
from PIL import Image, ImageChops, ImageStat

root = Path(sys.argv[1])
for motion in ('sliding', 'rotating'):
    for trim in ('bare', 'framed'):
        shots = []
        for pose in ('closed', 'open'):
            name = f'gallery_space_{motion}_{trim}_{pose}'
            image = Image.open(root / f'shot_{name}.png').convert('RGB')
            magenta = sum(r > 80 and b > 80 and g < min(r, b) * .3
                          for r, g, b in getattr(image, 'get_flattened_data', image.getdata)())
            assert magenta < 20, f'{name}: missing-texture magenta ({magenta} pixels)'
            shots.append(image)
        difference = sum(ImageStat.Stat(ImageChops.difference(*shots)).mean) / 3
        assert difference > .1, f'{motion}/{trim}: open and closed look identical'
Image.open(root / 'shot_gallery_space_glass.png').verify()
shapes={name:Image.open(root/f'shot_gallery_space_shape_{name}.png').convert('RGB')
        for name in ('hexagon','octagon','square','round')}
for name,image in shapes.items():
    missing=sum(r>80 and b>80 and g<min(r,b)*.3
                for r,g,b in getattr(image,'get_flattened_data',image.getdata)())
    assert missing<20,f'{name}: missing door shape texture'
    if name!='square':
        w,h=image.size
        door_box=(int(w*.37),int(h*.37),int(w*.58),int(h*.63))
        difference=sum(ImageStat.Stat(ImageChops.difference(
            image.crop(door_box),shapes['square'].crop(door_box))).mean)/3
        assert difference>.1,f'{name}: door shape did not change rendered door pixels'
hinges=[Image.open(root/f'shot_gallery_space_config_hinges_{mode}.png').convert('RGB') for mode in ('on','off')]
assert sum(ImageStat.Stat(ImageChops.difference(*hinges)).mean)>.01,'hinge toggle has no rendered effect'
for image in hinges+[Image.open(root/'shot_gallery_space_glass.png').convert('RGB')]:
    missing=sum(r>80 and b>80 and g<min(r,b)*.3
                for r,g,b in getattr(image,'get_flattened_data',image.getdata)())
    assert missing<20,'missing texture in hinge variants or glass detail tiers'
for side in ('east','west'):
    for edge in ('front','back'):
        name=f'gallery_space_jamb_{side}_{edge}'
        image=Image.open(root/f'shot_{name}.png').convert('RGB')
        w,h=image.size
        # Sweep across the full visible jamb on every interior scanline, not
        # just its center. Ignore two silhouette pixels for edge antialiasing.
        def is_backing(rgb):
            r,g,b=rgb
            return r>60 and b>60 and g<min(r,b)*.65
        pixels=image.load()
        for y in range(int(h*.15),int(h*.85)):
            # The open leaf occupies the far left of hinge-side views. This
            # window contains the full jamb silhouette but excludes that leaf
            # and its legitimate clearance gap beside the frame.
            row=[is_backing(pixels[x,y]) for x in range(int(w*.38),int(w*.65))]
            solid=[x for x,pink in enumerate(row) if not pink]
            assert solid and solid[-1]-solid[0]>w*.15,f'{name}: missing jamb at row {y}'
            assert not any(row[solid[0]+2:solid[-1]-1]),f'{name}: see-through gap at row {y}'
        # Ensure the fixture really contains its contrasting backing wall.
        backing=sum(is_backing(rgb) for rgb in getattr(image,'get_flattened_data',image.getdata)())
        assert backing>500,f'{name}: missing magenta test backing'
print('PASS: inside-frame oblique views of both jambs have no see-through pixels')
log = (root / 'client.log').read_text()
assert 'FML.TEXTURE_ERRORS' not in log, 'Client reported texture loading errors'
assert 'Exception baking model' not in log, 'Client reported model baking errors'
print('PASS: all Space door galleries have textures and distinct open/closed poses')
