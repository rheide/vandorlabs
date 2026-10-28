"""Extract opaque native-resolution material strips without sampling a frame's transparent center."""
from pathlib import Path
from PIL import Image


def opaque_strip(image, edge=0):
    image = image.convert("RGBA")
    if max(image.size) > 64:
        image = image.resize((64, 64), Image.Resampling.NEAREST)
    if edge == 1:
        image = image.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    elif edge == 2:
        image = image.transpose(Image.Transpose.TRANSPOSE)
    elif edge == 3:
        image = image.transpose(Image.Transpose.ROTATE_90)
    width, depth = image.size
    heights = [0] * width
    best = (0, 0, 1, 1)
    best_area = 0
    for y in range(max(1, depth // 3)):
        for x in range(width):
            heights[x] = heights[x] + 1 if image.getpixel((x, y))[3] >= 240 else 0
        for x in range(width):
            height = heights[x]
            for right in range(x, width):
                height = min(height, heights[right])
                if not height:
                    break
                area = (right - x + 1) * height
                if area > best_area:
                    best_area = area
                    best = (x, y - height + 1, right + 1, y + 1)
    if not best_area:
        opaque = next(((x, y) for y in range(depth) for x in range(width)
                       if image.getpixel((x, y))[3] >= 240), None)
        if opaque is None:
            raise ValueError("Frame contains no opaque material")
        x, y = opaque
        best = (x, y, x + 1, y + 1)
    return image.crop(best).resize((64, 64), Image.Resampling.NEAREST)
