"""Native 64px Core artwork shared by frame-based machine importers."""
from PIL import Image, ImageDraw


def core_texture(path, color, bars=None, symbol=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new('RGB', (64, 64), (39, 48, 60))
    draw = ImageDraw.Draw(image)
    draw.rectangle((2, 2, 61, 61), outline=(135, 151, 163), width=3)
    draw.rectangle((10, 10, 53, 53), outline=color, width=3)
    if bars is not None:
        for n in range(bars):
            draw.rectangle((20+n*9, 19, 24+n*9, 44), fill=color)
    elif symbol == 'meridian':
        for x in (21, 30, 39):
            draw.rectangle((x, 19, x+4, 44), fill=color)
        draw.line((19, 46, 45, 46), fill=(82, 207, 222), width=2)
    elif symbol == 'fold':
        draw.line((20, 20, 30, 31, 20, 43), fill=color, width=4)
        draw.line((44, 20, 34, 31, 44, 43), fill=color, width=4)
        draw.rectangle((29, 27, 35, 36), fill=(82, 207, 222))
    elif symbol == 'thrust':
        draw.polygon([(32, 18), (45, 33), (37, 33), (37, 45), (27, 45), (27, 33), (19, 33)], fill=color)
    image.save(path, optimize=True)
