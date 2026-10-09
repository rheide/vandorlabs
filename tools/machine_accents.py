"""Apply the Rivet Dynamics ON-highlight palette while retaining texture detail."""
import colorsys
from PIL import Image

COLORS = {
    'navy_blue': (22, 48, 108),
    'orange': (233, 99, 19),
    'red': (212, 27, 35),
    'light_green': (153, 213, 110),
    'dark_green': (27, 115, 56),
    'light_blue': (132, 191, 236),
    'yellow': (232, 198, 40),
}
ACTIVITY = {'cyan', 'amber', 'display_on'}


def accent(model):
    if 'shield_capacitor' in model['stem']:
        return 'orange'
    return {'power': 'red', 'core': 'navy_blue', 'dampener': 'light_green',
            'gravity': 'dark_green', 'tractor': 'light_blue', 'analysis': 'yellow'}.get(model['category'])


def apply_accents(models, directory, prefix=''):
    written = set()
    for model in models:
        if not model.get('active', False):
            continue
        color = accent(model)
        if color is None:
            continue
        hue, saturation, value = colorsys.rgb_to_hsv(*(n/255 for n in COLORS[color]))
        for face in model['faces']:
            if face.get('base_material') not in ACTIVITY:
                continue
            original = face['material'][len(prefix):]
            name = 'accents/' + color + '/' + original
            if name not in written:
                with Image.open(directory / (original+'.png')) as source:
                    image = source.convert('RGBA')
                    pixels = []
                    for r, g, b, a in image.getdata():
                        _, s, v = colorsys.rgb_to_hsv(r/255, g/255, b/255)
                        rgb = colorsys.hsv_to_rgb(hue, min(1, s*saturation/(191/239)), min(1, v*value/(239/255)))
                        pixels.append(tuple(round(n*255) for n in rgb)+(a,))
                    image.putdata(pixels)
                    target = directory / (name+'.png')
                    target.parent.mkdir(parents=True, exist_ok=True)
                    image.save(target)
                written.add(name)
            face['material'] = prefix + name
    print(f'Applied machine accent colors to {len(written)} activity textures')
