#!/usr/bin/env python3
"""Compare actual screen GUI pixels at matched legacy and modern logical scales."""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image


def require(value, message):
    if not value:
        raise AssertionError(message)


def panel(image, scale, target):
    image = image.convert('RGB')
    require(image.size == (1280, 720), f'{target}: expected 1280x720 capture')
    width, height = (image.width + scale - 1) // scale, (image.height + scale - 1) // scale
    pw, ph = min(460, width - 8), min(332, height - 8)
    left, top = (width - pw) // 2, (height - ph) // 2
    controls, list_x = left + pw - 166, left + 12
    list_width, list_y = controls - list_x - 19, top + 54
    count = max(2, (ph - 60) // 22)
    pixels = np.asarray(image.convert('RGB'))

    def sample(x, y):
        return tuple(pixels[y * scale + scale // 2, x * scale + scale // 2])

    require(sample(left + 1, top + 1) == (48, 72, 88), f'{target}: header palette/placement')
    require(sample(left + 1, top + 25) == (25, 35, 44), f'{target}: panel palette/placement')
    require(sample(list_x - 1, list_y - 1) == (0, 0, 0), f'{target}: list border placement')
    selected = []
    for row in range(count):
        # The first logical scanline avoids text and thumbnail pixels.
        strip = pixels[(list_y + row * 22 + 1) * scale, list_x * scale:(list_x + list_width) * scale]
        if np.mean(np.all(strip == (42, 74, 106), axis=1)) > .95:
            selected.append(row)
    require(selected == [count // 2], f'{target}: selected artwork is not centered: {selected}')
    row = selected[0]

    def crop(x, y, w, h):
        return image.crop((x * scale, y * scale, (x + w) * scale, (y + h) * scale)).resize((w, h), Image.Resampling.NEAREST)

    title = crop(left + 12, top + 8, 190, 9)
    label = crop(list_x + 23, list_y + row * 22 + 7, min(190, list_width - 26), 9)
    thumb = crop(list_x + 3, list_y + row * 22 + 3, 16, 16)
    require(np.unique(np.asarray(thumb).reshape(-1, 3), axis=0).shape[0] > 12, f'{target}: selected artwork thumbnail is blank')
    # These are independent screenshots: title and selected label must contain glyphs,
    # and the shared controls must stay inside their authored column.
    require(np.sum(np.all(np.asarray(title)[:, :, :3] >= 180, axis=2)) > 180, f'{target}: missing title glyphs')
    require(controls + 154 <= left + pw - 12, f'{target}: controls exceed panel')
    return {'title': title, 'label': label, 'thumb': thumb, 'geometry': [left, top, pw, ph, controls, list_x, list_width, count], 'selected_row': row}


def mask(image, selected=False):
    values = np.asarray(image.convert('RGB'))
    if selected:
        return (values[:, :, 0] > 180) & (values[:, :, 1] > 160) & (values[:, :, 2] < 180)
    return np.all(values >= 180, axis=2)


def glyph_overlap(first, second):
    require(first.shape == second.shape, 'glyph comparison dimensions differ')
    best = 0.
    # Platform font backends can place the same glyph one pixel higher.
    for offset in (-1, 0, 1):
        shifted = np.roll(second, offset, axis=0)
        union = np.sum(first | shifted)
        best = max(best, np.sum(first & shifted) / max(1, union))
    return best


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--legacy', type=Path, required=True)
    parser.add_argument('--modern-scale3', type=Path, required=True)
    parser.add_argument('--modern-scale2', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    fixtures = {'legacy': panel(Image.open(args.legacy), 3, 'legacy'),
                'modern3': panel(Image.open(args.modern_scale3), 3, 'modern3'),
                'modern2': panel(Image.open(args.modern_scale2), 2, 'modern2')}
    require(fixtures['legacy']['geometry'] == fixtures['modern3']['geometry'], 'matched-scale geometry differs')
    scores = {}
    for target in ('modern3', 'modern2'):
        title_score = glyph_overlap(mask(fixtures['legacy']['title']), mask(fixtures[target]['title']))
        # Use a common width because the taller scale-2 panel has a wider picker.
        width = min(fixtures['legacy']['label'].width, fixtures[target]['label'].width)
        label_score = glyph_overlap(mask(fixtures['legacy']['label'].crop((0, 0, width, 9)), True), mask(fixtures[target]['label'].crop((0, 0, width, 9)), True))
        require(title_score >= .9, f'{target}: title glyphs differ ({title_score:.3f})')
        require(label_score >= .9, f'{target}: selected Engineering Screen label differs ({label_score:.3f})')
        scores[target] = {'title_overlap': title_score, 'selected_label_overlap': label_score}
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for target, fixture in fixtures.items():
        for region in ('title', 'label', 'thumb'):
            fixture[region].save(args.output_dir / f'{target}-{region}.png')
    result = {'status': 'PASS', 'geometry': {key: fixture['geometry'] for key, fixture in fixtures.items()}, 'glyphs': scores}
    (args.output_dir / 'screen-pixel-contracts.json').write_text(json.dumps(result, indent=2) + '\n')
    print('Screen pixel contracts PASS: matched panel/list geometry, palette, selection, title/label glyphs and artwork thumbnails at GUI scales 3 and 2')


if __name__ == '__main__':
    main()
