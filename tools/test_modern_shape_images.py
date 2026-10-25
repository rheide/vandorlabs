#!/usr/bin/env python3
"""Check actual slab/stair dialogs, configured icons and discrete stair slider artwork."""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image
from test_modern_screen_images import glyph_overlap


def need(value, message):
    if not value:
        raise AssertionError(message)


def glyph(image):
    a = np.asarray(image.convert('RGB'))
    return (a[:, :, 0] > 180) & (a[:, :, 1] > 180) & (a[:, :, 2] > 140)


def panel(image, scale, faces=False):
    image = image.convert('RGB')
    need(image.size == (1280, 720), 'actual 1280x720 capture required')
    width, height = (1280 + scale - 1) // scale, (720 + scale - 1) // scale
    pw, ph = min(460, width - 8), min(332, height - 8)
    left, top = (width - pw) // 2, (height - ph) // 2
    pixels = np.asarray(image)

    def sample(x, y):
        return tuple(pixels[y * scale + scale // 2, x * scale + scale // 2])

    def crop(x, y, w, h):
        return image.crop((x * scale, y * scale, (x + w) * scale, (y + h) * scale)).resize((w, h), Image.Resampling.NEAREST)

    need(sample(left + 1, top + 1) == (32, 32, 40), 'header palette/scale differs')
    need(sample(left + 1, top + 19) == (16, 16, 18), 'opaque panel palette/bounds differ')
    controls, listx = left + pw - 166, left + 12
    listw, listy = controls - listx - 19, top + 54
    need(sample(listx - 1, listy - 1) == (0, 0, 0), 'material-list border differs')
    title = crop(left + 8, top + 5, 220, 9)
    need(glyph(title).sum() > 130, 'shape title missing')
    count, span = (8, pw - 24) if faces else (2, listw)
    tabwidth = span // count
    for index in range(count):
        label = crop(listx + index * tabwidth + 2, top + 34, tabwidth - 6, 10)
        need((np.asarray(label).min(axis=2) > 140).sum() > 12, f'visible tab {index}/{count} glyphs missing')
    rows = max(2, (ph - 60) // 22)
    selected = []
    for row in range(rows):
        strip = pixels[(listy + row * 22 + 1) * scale, listx * scale:(listx + listw) * scale]
        if np.mean(np.all(strip == (42, 74, 106), axis=1)) > .95:
            selected.append(row)
    need(len(selected) == 1, f'exactly one selected finish required: {selected}')
    thumbnail = crop(listx + 3, listy + selected[0] * 22 + 3, 16, 16)
    need(len(np.unique(np.asarray(thumbnail).reshape(-1, 3), axis=0)) > 5, 'selected authored thumbnail missing')
    done = crop(controls + 3, top + ph - 21, 148, 10)
    need(glyph(done).sum() > 30, 'Done glyph missing')
    return {'geometry': [left, top, pw, ph], 'tabs': count}, title, done


def icons(image):
    a = np.asarray(image.convert('RGB'))
    need(image.size == (1280, 720) and tuple(a[0, 0]) == (16, 16, 18), 'configured gallery background differs or is blurred')
    scale, left, top = 2, (640 - 8 * 22) // 2, (360 - 6 * 22) // 2
    boxes, signatures = [], []
    for index in range(48):
        x, y = left + index % 8 * 22 + 2, top + index // 8 * 22 + 2
        # Inspect physical pixels: downsampling to 16px can erase thin stair treads.
        pixels = a[y * scale:(y + 16) * scale, x * scale:(x + 16) * scale].astype(int)
        mask = np.any(np.abs(pixels - (40, 44, 50)) > 3, axis=2)
        ys, xs = np.where(mask)
        need(len(xs) >= 20, f'configured item {index} empty')
        box = [int(xs.min()), int(ys.min()), int(xs.max() + 1), int(ys.max() + 1)]
        need(box[0] >= 1 and box[1] >= 1 and box[2] <= 31 and box[3] <= 31, f'configured item {index} lacks padding: {box}')
        boxes.append(box)
        signatures.append(hashlib.sha256(pixels.tobytes()).hexdigest())
    need(len(set(signatures[3:])) >= 15, 'stair tread/roll/layout variants ignored')
    return {'count': 48, 'physical_pixel_bounds': boxes, 'distinct_stair_images': len(set(signatures[3:]))}


def reject(call, label):
    try:
        call()
    except AssertionError:
        return
    raise AssertionError('negative accepted: ' + label)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--screenshots', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    root = args.screenshots
    image = lambda name: Image.open(root / ('modern-shape-' + name + '.png')).convert('RGB')
    result = {'status': 'PASS', 'dialogs': {}}
    for family in ('slab', 'stairs'):
        a, at, ad = panel(image(family + '-gui3'), 3)
        b, bt, bd = panel(image(family + '-gui2'), 2)
        scores = [glyph_overlap(glyph(at), glyph(bt)), glyph_overlap(glyph(ad), glyph(bd))]
        need(min(scores) >= .9, f'{family}: title/Done glyphs differ across scales {scores}')
        f, _, _ = panel(image(family + '-faces'), 2, True)
        panel(image(family + '-restored'), 2, True)
        panel(image(family + '-dormant'), 2)
        result['dialogs'][family] = {'gui3': a, 'gui2': b, 'faces': f, 'glyph_overlap': scores}
    result['icons'] = icons(image('items'))
    # All four captures must differ in the actual slider track/thumb/label region.
    left, top = (640 - 460) // 2, (360 - 332) // 2
    crops = []
    for tread in (1, 2, 4, 8):
        a = np.asarray(image('stairs-slider' + str(tread)))
        crops.append(a[(top + 126) * 2:(top + 146) * 2, (left + 294) * 2:(left + 448) * 2].astype(int))
    changes = [int(np.any(np.abs(crops[i] - crops[j]) > 10, axis=2).sum()) for i in range(4) for j in range(i)]
    need(min(changes) > 30, f'discrete stair slider artwork did not change: {changes}')
    result['slider_changed_pixels'] = changes
    reject(lambda: panel(image('slab-gui2'), 3), 'wrong GUI scale')
    reject(lambda: panel(image('slab-world'), 3), 'world as GUI')
    reject(lambda: panel(image('stairs-dormant'), 2, True), 'dormant tabs as eight visible tabs')
    reject(lambda: icons(Image.new('RGB', (1280, 720), (16, 16, 18))), 'blank icons')
    result['negative_controls'] = 4
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / 'shape-pixel-contracts.json').write_text(json.dumps(result, indent=2) + '\n')
    print('Shape pixels PASS: real two/eight-tab dialogs, GUI 3/2 title/Done glyphs, authored thumbnails, four stair slider poses and 48 padded item icons; four negatives rejected')


if __name__ == '__main__':
    main()
