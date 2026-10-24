#!/usr/bin/env python3
"""Inspect real seven-family selectors/editors at both GUI scales, with negative captures."""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image
from test_modern_screen_images import glyph_overlap
from test_modern_diagonal_art_images import inspect as diagonal_art


def require(value, message):
    if not value:
        raise AssertionError(message)


def geometry(image, scale, editor=False):
    require(image.size == (1280, 720), 'expected 1280x720 native capture')
    width, height = (1280 + scale - 1) // scale, (720 + scale - 1) // scale
    pw, ph = (min(420, width - 12), min(300, height - 12)) if editor else (min(460, width - 8), min(332, height - 8))
    return (width - pw) // 2, (height - ph) // 2, pw, ph


def crop(image, scale, x, y, w, h):
    return image.crop((x * scale, y * scale, (x + w) * scale, (y + h) * scale)).resize((w, h), Image.Resampling.NEAREST)


def white(image):
    data = np.asarray(image.convert('RGB'))
    return np.all(data >= 180, axis=2)


def inspect(image, scale, family, editor=False):
    image = image.convert('RGB')
    left, top, pw, ph = geometry(image, scale, editor)
    data = np.asarray(image)
    def sample(x, y):
        return tuple(data[y * scale + scale // 2, x * scale + scale // 2])
    require(sample(left + 1, top + 1) == ((25, 35, 44) if editor else (48, 72, 88)), 'panel/header palette or scale differs')
    require(sample(left + 1, top + 25) == (25, 35, 44), 'panel body palette or bounds differ')
    title = crop(image, scale, left + 12, top + (10 if editor else 8), min(290, pw - 24), 9)
    require(int(white(title).sum()) > 150, 'canonical title glyphs missing')
    result = {'geometry': [left, top, pw, ph], 'title': title}
    if editor:
        # Console checks deliberately edit their half-height secondary surface.
        header = family in (0, 1, 4)
        row_top = 82 if header else 54
        require(sample(left + 13, top + row_top + 1) == (54, 92, 112), 'first selected control row layout/palette differs')
        require(sample(left + 13, top + row_top + 21) == (34, 49, 62), 'second control row spacing/palette differs')
        region = np.asarray(crop(image, scale, left + 12, top + 55, 65, 9))
        # Native font blending lowers these glyph channels by up to three; solid fills stay exact.
        glyph_delta = np.asarray((218, 232, 240)) - region.astype(int)
        caption = int(((glyph_delta >= 0) & (glyph_delta <= 3)).all(axis=2).sum())
        require((caption > 20) == header, 'half/full Header caption visibility differs')
        if header:
            require(sample(left + 79, top + 51) == (0, 0, 0), 'full editor Header field missing')
        controls = crop(image, scale, left + pw - 164, top + 54, 148, 38)
        require(int(white(controls).sum()) > 60, 'Add/Remove/Up/Down control glyphs missing')
        result.update(header=header, rows_y=top + row_top)
    else:
        controls = left + pw - 166
        list_x, list_y, list_width = left + 12, top + 54, controls - (left + 12) - 19
        require(sample(list_x - 1, list_y - 1) == (0, 0, 0), 'picker border/list offset differs')
        selected = []
        for row in range(max(2, (ph - 60) // 22)):
            scan = data[(list_y + row * 22 + 1) * scale, list_x * scale:(list_x + list_width) * scale]
            if np.mean(np.all(scan == (42, 74, 106), axis=1)) > .95:
                selected.append(row)
        require(len(selected) == 1, f'expected one selected art row, found {selected}')
        thumb = crop(image, scale, list_x + 3, list_y + selected[0] * 22 + 3, 16, 16)
        require(np.unique(np.asarray(thumb).reshape(-1, 3), axis=0).shape[0] > 8, 'authored artwork thumbnail is blank')
        tabs = 3 if family in (2, 3) else 2
        for tab in range(tabs):
            tw = (list_width + 2) // tabs
            label = np.asarray(crop(image, scale, list_x + tab * tw + 2, top + 34, tw - 6, 10))
            glyphs = (np.min(label, axis=2) >= 150) & (np.max(label, axis=2) - np.min(label, axis=2) < 12)
            require(int(glyphs.sum()) > 25, f'family tab {tab} glyphs missing')
        result.update(selected_row=selected[0], tabs=tabs, thumb=thumb)
    return result


def world_contract(art, overlay):
    bounds = (420, 150, 860, 570)
    first, second = np.asarray(art.crop(bounds), dtype=int), np.asarray(overlay.crop(bounds), dtype=int)
    changed = int(np.any(np.abs(first - second) > 16, axis=2).sum())
    require(changed > 700, f'world surface did not change from authored art to controls ({changed} pixels)')
    red, green, blue = second[:, :, 0], second[:, :, 1], second[:, :, 2]
    active = (green > 75) & (red > green * .18) & (red < green * .36) & (blue > green * .60) & (blue < green * .84)
    active_pixels = int(active.sum())
    require(active_pixels >= 12, f'actual ON control color missing from rendered surface ({active_pixels} pixels)')
    return {'changed_surface_pixels': changed, 'active_control_pixels': active_pixels}

def negative(operation, label):
    try:
        operation()
    except AssertionError:
        return
    raise AssertionError(f'negative capture was accepted: {label}')


def title_from_legacy(path, editor=False):
    image = Image.open(path).convert('RGB')
    left, top, pw, ph = geometry(image, 3, editor)
    return crop(image, 3, left + 12, top + (10 if editor else 8), min(290, pw - 24), 9)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--screenshots', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--legacy-scenes', type=Path)
    parser.add_argument('--legacy-overlays', type=Path)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    report = []
    for family in range(7):
        prefix = f'modern-family-{family}-'
        images = {suffix: Image.open(args.screenshots / (prefix + suffix + '.png')).convert('RGB') for suffix in ('scale3', 'scale2', 'surface-dialog', 'art-world', 'editor-scale3', 'editor-scale2', 'overlay-world')}
        fixtures = {suffix: inspect(images[suffix], scale, family, editor) for suffix, scale, editor in [('scale3', 3, False), ('scale2', 2, False), ('editor-scale3', 3, True), ('editor-scale2', 2, True)]}
        inspect(images['surface-dialog'], 2, family)
        world = world_contract(images['art-world'], images['overlay-world'])
        if family == 1:
            world['diagonal_target'] = diagonal_art(images['art-world'])
            world['diagonal_off_target'] = diagonal_art(Image.open(args.screenshots / (prefix + 'art-off-target.png')))
        negative(lambda: world_contract(images['art-world'], images['art-world']), prefix + 'unchanged-art-as-overlay')
        images['art-world'].crop((420, 150, 860, 570)).save(args.output_dir / (prefix + 'art-surface.png'))
        images['overlay-world'].crop((420, 150, 860, 570)).save(args.output_dir / (prefix + 'control-surface.png'))
        scores = {}
        for kind in ('', 'editor-'):
            first, second = fixtures[kind + 'scale3']['title'], fixtures[kind + 'scale2']['title']
            score = glyph_overlap(white(first), white(second))
            require(score >= .9, f'family {family} {kind}title changes between scales ({score:.3f})')
            scores[kind + 'title_overlap'] = score
            negative(lambda: inspect(images[kind + 'scale2'], 3, family, bool(kind)), prefix + kind + 'wrong-scale')
            negative(lambda: inspect(images['art-world'], 3, family, bool(kind)), prefix + kind + 'world-as-dialog')
        if args.legacy_scenes:
            names = {0: 'shot_screen_gui.png', 2: 'shot_console_gui.png', 3: 'shot_half_console_gui.png', 4: 'shot_full_input_gui.png', 5: 'shot_input_gui.png', 6: 'shot_input_gui.png'}
            if family in names:
                score = glyph_overlap(white(title_from_legacy(args.legacy_scenes / names[family])), white(fixtures['scale3']['title']))
                require(score >= .9, f'family {family} selector title differs from legacy ({score:.3f})')
                scores['legacy_selector_title_overlap'] = score
        if args.legacy_overlays and family != 4:
            index = {0: 0, 1: 1, 2: 25, 3: 26, 5: 17, 6: 22}[family]
            score = glyph_overlap(white(title_from_legacy(args.legacy_overlays / f'shot_redstone_screen_{index}_dialog.png', True)), white(fixtures['editor-scale3']['title']))
            require(score >= .9, f'family {family} editor title differs from legacy ({score:.3f})')
            scores['legacy_editor_title_overlap'] = score
        for suffix, fixture in fixtures.items():
            fixture['title'].save(args.output_dir / (prefix + suffix + '-title.png'))
            if 'thumb' in fixture:
                fixture['thumb'].save(args.output_dir / (prefix + suffix + '-thumbnail.png'))
        report.append({'family': family, 'world': world, 'glyphs': scores, 'fixtures': {name: {key: value for key, value in fixture.items() if key not in ('title', 'thumb')} for name, fixture in fixtures.items()}})
    (args.output_dir / 'screen-family-pixel-contracts.json').write_text(json.dumps({'status': 'PASS', 'families': report, 'negative_controls': 35}, indent=2) + '\n')
    print('Screen-family pixels PASS: seven selector/editor pairs at scales3/2, exact palette/geometry/tabs/rows/headers/thumbnails, matching titles and35 substituted-capture rejections')


if __name__ == '__main__':
    main()
