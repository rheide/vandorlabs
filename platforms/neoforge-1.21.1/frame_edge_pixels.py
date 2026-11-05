"""Build-time projection of FrameEdgePixels, checked against the actual Java pixel baseline."""
from PIL import Image


def crop(image, edge):
    image = image.convert('RGBA')
    w, h = image.size
    vertical = edge in (2, 3)
    along, depth = (h, w) if vertical else (w, h)

    def pixel(at, d):
        return image.getpixel((at, d) if edge == 0 else (at, h - 1 - d) if edge == 1 else (d, at) if edge == 2 else (w - 1 - d, at))

    band = 1
    for at in range(along // 4, along - along // 4):
        for d in range(depth // 3):
            if pixel(at, d)[3] >= 32:
                band = max(band, d + 1)
    start = band
    for at in range(along // 4, along - along // 4):
        for d in range(band):
            if pixel(at, d)[3] >= 128:
                start = min(start, d)
    start = min(start, band - 1)
    out = Image.new('RGBA', (64, 64))
    for x in range(64):
        at = min(along - 1, x * along // 64)
        fallback = next((pixel(at, d) for d in range(depth // 3) if pixel(at, d)[3] >= 240), None)
        if fallback is None:
            fallback = next((pixel(a, d) for a in range(along) for d in range(depth // 3) if pixel(a, d)[3] >= 240), None)
        if fallback is None:
            raise ValueError('Frame artwork contains no opaque material')
        for y in range(64):
            color = pixel(at, min(band - 1, start + y * (band - start) // 64))
            if color[3] >= 240:
                fallback = color
            out.putpixel((x, y), fallback[:3] + (255,))
    return out
