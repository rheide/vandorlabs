#!/usr/bin/env python3
"""Inspect actual flat-wall menus and compare matching legacy title/list/control regions."""
import argparse
import json
from pathlib import Path
import numpy as np
from PIL import Image
from test_modern_screen_images import glyph_overlap, mask, require

KINDS = ('plain', 'porthole', 'full')
EXTRA = ('side-default-scale3', 'side-scale3', 'side-scale2', 'stock-glass-scale3', 'stock-glass-scale2', 'custom-glass-scale3', 'custom-glass-scale2')


def crop(image, scale, x, y, w, h):
    return image.crop((x * scale, y * scale, (x + w) * scale, (y + h) * scale)).resize((w, h), Image.Resampling.NEAREST)


def inspect(image, scale, kind, suffix):
    image = image.convert('RGB')
    require(image.size == (1280, 720), 'expected 1280x720 wall capture')
    width, height = (1280 + scale - 1) // scale, (720 + scale - 1) // scale
    pw, ph = min(460, width - 8), min(332, height - 8)
    left, top = (width - pw) // 2, (height - ph) // 2
    controls, list_x = left + pw - 166, left + 12
    list_width = controls - list_x - 19
    list_y, rows = top + (30 if kind == 'plain' else 54), max(2, (ph - (64 if kind == 'plain' else 60)) // 22)
    pixels = np.asarray(image)
    def sample(x, y):
        return tuple(pixels[y * scale + scale // 2, x * scale + scale // 2])
    require(sample(left + 1, top + 1) == (32, 32, 40), 'wall title panel bounds/palette')
    require(sample(left + 1, top + 19) == (16, 16, 18), 'wall body bounds/palette')
    require(sample(list_x - 1, list_y - 1) == (0, 0, 0), 'wall list border/vertical offset')
    title = crop(image, scale, left + 8, top + 5, 220, 9)
    require(int(mask(title).sum()) > 150, 'canonical wall title missing')
    selected = []
    for row in range(rows):
        scan = pixels[(list_y + row * 22 + 1) * scale, list_x * scale:(list_x + list_width) * scale]
        if np.mean(np.all(scan == (42, 74, 106), axis=1)) > .95:
            selected.append(row)
    if suffix.startswith('side-default'):
        require(len(selected) <= 1, 'default side has duplicate selection rows')
    else:
        require(len(selected) == 1, 'missing or duplicate chosen material row: ' + str(selected))
    result = {'geometry': [left, top, pw, ph, list_x, list_y, list_width, rows], 'title': title, 'selected': selected}
    if selected:
        row = selected[0]
        result['label'] = crop(image, scale, list_x + 23, list_y + row * 22 + 7, min(190, list_width - 26), 9)
        result['thumbnail'] = crop(image, scale, list_x + 3, list_y + row * 22 + 3, 16, 16)
        require(int(mask(result['label'], True).sum()) > 15, 'chosen material label missing')
        colors = np.asarray(result['thumbnail'])
        if suffix.startswith('stock-glass'):
            require(int(np.any(colors != (42, 74, 106), axis=2).sum()) > 100, 'stock glass thumbnail was not painted')
        else:
            require(np.unique(colors.reshape(-1, 3), axis=0).shape[0] > 12, 'authored material thumbnail blank')
    if kind != 'plain':
        for tab in range(3):
            tab_width = list_width // 3
            label = crop(image, scale, list_x + tab * tab_width + 2, top + 34, tab_width - 6, 10)
            gray = np.asarray(label)
            glyphs = (np.min(gray, axis=2) >= 150) & (np.max(gray, axis=2) - np.min(gray, axis=2) < 12)
            require(int(glyphs.sum()) > 25, 'wall Main/Side/Glass tab glyphs missing')
    ys = [30] if kind == 'plain' else [30, 54, 78, 102] if suffix.startswith(('side', 'custom-glass')) else [30, 54, 78]
    result['controls'] = [crop(image, scale, controls + 2, top + y + 5, 150, 10) for y in ys]
    for index, label in enumerate(result['controls']):
        if suffix.startswith('side-default') and index == 0:
            rgb = np.asarray(label)
            glyph = (np.min(rgb, axis=2) >= 145) & (np.max(rgb, axis=2) - np.min(rgb, axis=2) < 12)
        else:
            glyph = mask(label)
        require(int(glyph.sum()) > 20, 'wall option label missing')
    return result


def stock_alpha(thumbnail, texture):
    require(texture.size == (128, 128), 'stock glass must retain the legacy packed128px resolution')
    rgba = np.asarray(texture.convert('RGBA'), dtype=float)
    alpha = rgba[:, :, 3:4] / 255
    expected = (rgba[:, :, :3] * alpha + np.asarray((42, 74, 106)) * (1 - alpha)).mean((0, 1))
    actual = np.asarray(thumbnail.convert('RGB'), dtype=float).mean((0, 1))
    error = float(np.abs(actual - expected).mean())
    require(error <= 3, 'stock glass preview does not alpha-blend over its selected row: mean RGB error ' + str(error))
    return error


def compare(first, second, name):
    require(first['geometry'] == second['geometry'], name + ': matched-scale geometry differs')
    title = glyph_overlap(mask(first['title']), mask(second['title']))
    require(title >= .9, name + ': title glyph mismatch')
    report = {'title_overlap': title}
    if 'label' in first and 'label' in second:
        label = glyph_overlap(mask(first['label'], True), mask(second['label'], True))
        require(label >= .9, name + ': selected material glyph mismatch')
        error = float(np.abs(np.asarray(first['thumbnail'], dtype=float) - np.asarray(second['thumbnail'], dtype=float)).mean())
        require(error <= 12, name + ': authored thumbnail pixels differ')
        report.update(selected_label_overlap=label, thumbnail_mean_absolute_error=error)
    for index, (a, b) in enumerate(zip(first['controls'], second['controls'])):
        # Native button surfaces differ; compare the centered text rather than button artwork.
        overlap = glyph_overlap(mask(a), mask(b))
        require(overlap >= .85, name + ': option glyph mismatch ' + str(index))
        report['control_' + str(index) + '_overlap'] = overlap
    return report


def negative(operation, name):
    try:
        operation()
    except AssertionError:
        return
    raise AssertionError('substituted capture accepted: ' + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--screenshots', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--legacy-profiles', type=Path)
    parser.add_argument('--stock-black-texture', type=Path, default=Path(__file__).resolve().parents[1] / 'platforms/neoforge-1.21.1/build/resources/main/assets/vandorlabs/textures/block/programmable_glass/low/black.png', help='Packed128px stock art used for the independent alpha composite check')
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    report = []
    for kind in KINDS:
        world = Image.open(args.screenshots / ('modern-wall-' + kind + '-world.png')).convert('RGB')
        configured = Image.open(args.screenshots / ('modern-wall-' + kind + '-configured.png')).convert('RGB')
        changed = int(np.any(np.abs(np.asarray(world.crop((420, 150, 860, 570)), dtype=int) - np.asarray(configured.crop((420, 150, 860, 570)), dtype=int)) > 16, axis=2).sum())
        require(changed > 500, kind + ': configured world did not display changed authored material')
        suffixes = ('main-scale3', 'main-scale2') + (() if kind == 'plain' else EXTRA)
        for suffix in suffixes:
            image = Image.open(args.screenshots / ('modern-wall-' + kind + '-' + suffix + '.png'))
            scale = 2 if suffix.endswith('scale2') else 3
            fixture = inspect(image, scale, kind, suffix)
            negative(lambda: inspect(image, 5 - scale, kind, suffix), kind + suffix + 'wrong-scale')
            negative(lambda: inspect(world, scale, kind, suffix), kind + suffix + 'world-as-menu')
            result = {'kind': kind, 'scene': suffix, 'geometry': fixture['geometry'], 'selected': fixture['selected']}
            if suffix.startswith('stock-glass'):
                with Image.open(args.stock_black_texture) as texture:
                    result['stock_alpha_mean_error'] = stock_alpha(fixture['thumbnail'], texture)
            if args.legacy_profiles and kind != 'porthole' and not suffix.startswith('side-default'):
                legacy_path = args.legacy_profiles / ('flat-wall-' + kind + '-' + suffix + '.png')
                if legacy_path.is_file():
                    result['legacy'] = compare(inspect(Image.open(legacy_path), scale, kind, suffix), fixture, kind + suffix)
            fixture['title'].save(args.output_dir / (kind + '-' + suffix + '-title.png'))
            report.append(result)
    (args.output_dir / 'wall-pixel-contracts.json').write_text(json.dumps({'status': 'PASS', 'menus': report, 'negative_controls': 2 * len(report)}, indent=2) + '\n')
    print('Wall pixels PASS: 3 profiles, both GUI scales, exact palette/list/tab/control geometry, authored thumbnails and substituted world/wrong-scale rejection')

if __name__ == '__main__':
    main()
