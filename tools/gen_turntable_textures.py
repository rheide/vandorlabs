"""Generate the Turntable block textures.

Direction cues required by the design:
  * turntable                - plain platform plating
  * turntable_face           - plating plus a bold arrow marking the payload side
  * turntable_indicator      - asymmetric rotating hand, drawn by TETurntable
  * turntable_controller     - controller body
  * turntable_controller_top - body plus an arrow pointing at the platform
  * turntable_controller_face- front sensor lens

Run with the bundled runtime:
  build/tooling/python-runtime/python.exe tools/gen_turntable_textures.py
"""
import os
import random

from PIL import Image, ImageDraw

SIZE = 64
OUT = os.path.join("texture-packs", "additional", "assets", "vandorlabs", "textures", "block")

PLATE = ["#12171b", "#1c2128", "#21262e", "#232830", "#242931", "#252a32"]
EDGE = "#2b323a"
BOLT = "#3b424a"
BOLT_LIGHT = "#565d67"
GREEN = "#37c46a"
GREEN_LIGHT = "#68e895"
GREEN_DARK = "#1e6b3a"
GREEN_SHADOW = "#12512c"


def plating(seed):
    """Dark bolted plate matching the programmable ramp plating."""
    image = Image.new("RGBA", (SIZE, SIZE), PLATE[0])
    pixels = image.load()
    rng = random.Random(seed)
    cell = 2
    for cy in range(0, SIZE, cell):
        for cx in range(0, SIZE, cell):
            shade = _hex(rng.choice(PLATE))
            for y in range(cy, min(cy + cell, SIZE)):
                for x in range(cx, min(cx + cell, SIZE)):
                    pixels[x, y] = shade
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 1, SIZE - 2, SIZE - 2], outline=_hex(EDGE))
    draw.rectangle([3, 3, SIZE - 4, SIZE - 4], outline=_hex(PLATE[1]))
    for x, y in ((2, 2), (SIZE - 3, 2), (2, SIZE - 3), (SIZE - 3, SIZE - 3)):
        draw.rectangle([x - 1, y - 1, x + 1, y + 1], fill=_hex(BOLT))
        pixels[x, y] = _hex(BOLT_LIGHT)
    return image


def arrow(draw, cx, top, bottom, half, colour, outline):
    """Bold in-plane arrow pointing at the top of the image."""
    head = bottom - max(6, (bottom - top) // 2)
    draw.polygon([(cx, top), (cx + half, head), (cx - half, head)], fill=_hex(colour),
                 outline=_hex(outline))
    draw.rectangle([cx - max(2, half // 3), head - 1, cx + max(2, half // 3), bottom],
                   fill=_hex(colour), outline=_hex(outline))


def ring(draw, cx, cy, radius, width, colour, outline):
    draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], outline=_hex(outline),
                 width=width + 2)
    draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], outline=_hex(colour),
                 width=width)


def platform():
    image = plating(11)
    draw = ImageDraw.Draw(image)
    draw.rectangle([8, 8, SIZE - 9, SIZE - 9], outline=_hex(PLATE[5]))
    return image


def face():
    image = plating(23)
    draw = ImageDraw.Draw(image)
    ring(draw, 32, 32, 22, 3, GREEN_DARK, GREEN_SHADOW)
    arrow(draw, 32, 11, 40, 9, GREEN, GREEN_SHADOW)
    draw.rectangle([29, 29, 35, 35], fill=_hex(GREEN_LIGHT), outline=_hex(GREEN_SHADOW))
    return image


def indicator():
    """Rotating hand: one long arrow blade plus a short tail, so any turn reads."""
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.polygon([(32, 4), (42, 26), (32, 22), (22, 26)], fill=_hex(GREEN_LIGHT),
                 outline=_hex(GREEN_SHADOW))
    draw.rectangle([29, 22, 35, 44], fill=_hex(GREEN), outline=_hex(GREEN_SHADOW))
    draw.polygon([(29, 44), (35, 44), (32, 54)], fill=_hex(GREEN_DARK),
                 outline=_hex(GREEN_SHADOW))
    draw.ellipse([25, 25, 39, 39], fill=_hex(GREEN), outline=_hex(GREEN_SHADOW))
    draw.ellipse([29, 29, 35, 35], fill=_hex(GREEN_LIGHT))
    return image


def controller_body():
    image = plating(37)
    draw = ImageDraw.Draw(image)
    draw.rectangle([12, 12, SIZE - 13, SIZE - 13], outline=_hex(PLATE[5]))
    draw.rectangle([20, 20, SIZE - 21, SIZE - 21], outline=_hex(BOLT))
    return image


def controller_top():
    image = controller_body()
    draw = ImageDraw.Draw(image)
    ring(draw, 32, 32, 17, 3, GREEN_DARK, GREEN_SHADOW)
    arrow(draw, 32, 14, 44, 8, GREEN, GREEN_SHADOW)
    draw.rectangle([29, 29, 35, 35], fill=_hex(GREEN_LIGHT), outline=_hex(GREEN_SHADOW))
    return image


def controller_face():
    image = controller_body()
    draw = ImageDraw.Draw(image)
    ring(draw, 32, 32, 16, 4, GREEN, GREEN_SHADOW)
    draw.ellipse([26, 26, 38, 38], fill=_hex(GREEN_LIGHT), outline=_hex(GREEN_SHADOW))
    draw.rectangle([30, 30, 34, 34], fill=_hex(GREEN_DARK))
    return image


def _hex(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def write(name, image):
    os.makedirs(OUT, exist_ok=True)
    image.save(os.path.join(OUT, name))
    print("wrote", os.path.join(OUT, name))


if __name__ == "__main__":
    write("turntable.png", platform())
    write("turntable_face.png", face())
    write("turntable_indicator.png", indicator())
    write("turntable_controller.png", controller_body())
    write("turntable_controller_top.png", controller_top())
    write("turntable_controller_face.png", controller_face())
