#!/usr/bin/env python3
"""Compare actual flat-wall GUI captures against an independently built reference JAR."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops

SCENES = (
    'plain-main-scale3', 'plain-main-scale2',
    'full-main-scale3', 'full-main-scale2', 'full-side-scale3',
    'full-stock-glass-scale3', 'full-custom-glass-scale3', 'full-custom-glass-scale2',
)

def compare(before, after):
    assert before.resolve() != after.resolve(), 'Reference and candidate captures must be separate runs'
    result = {}
    for scene in SCENES:
        name = 'flat-wall-' + scene + '.png'
        with Image.open(before / name) as reference, Image.open(after / name) as candidate:
            assert reference.size == candidate.size == (1280, 720), name + ': capture dimensions'
            # At scale 2 the 460x332 logical maximum is centered; scale 3 uses the smaller viewport.
            box = (180, 28, 1100, 692) if scene.endswith('scale2') else (12, 12, 1269, 708)
            for image in (reference, candidate):
                assert image.convert('RGB').getpixel((box[0], box[1])) == (32, 32, 40), name + ': title panel bounds'
                assert image.convert('RGB').getpixel((box[0] + 1, box[3] - 1)) == (16, 16, 18), name + ': opaque body bounds'
            difference = ImageChops.difference(reference.convert('RGB').crop(box), candidate.convert('RGB').crop(box))
            changed = sum(pixel != (0, 0, 0) for pixel in difference.getdata())
            result[name] = {'crop': box, 'changed': changed, 'pixels': difference.width * difference.height}
            assert changed == 0, name + ': ' + str(changed) + ' changed opaque panel pixels'
    return result

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('before', type=Path)
    parser.add_argument('after', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    report = compare(args.before, args.after)
    if args.report:
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print('Flat wall dialog pixel parity PASS: ' + str(len(report)) + ' captures, 0/' + str(sum(item['pixels'] for item in report.values())) + ' changed opaque pixels')
