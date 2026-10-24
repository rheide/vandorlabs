#!/usr/bin/env python3
"""Compare actual slider glyphs with displaced-thumb references and a wrong-order control."""
import argparse
import json
from pathlib import Path

from PIL import Image


def white(pixel):
    return min(pixel[:3]) >= 220


def analyze(directory):
    metadata = json.loads((directory / "modern-wireless-glyphs.json").read_text())
    results = {}
    for level in (8, 9):
        for scale in (2, 3):
            key = f"level{level}-scale{scale}"
            data = metadata[key]
            images = {mode: Image.open(directory / f"modern-wireless-on{level}-scale{scale}-{mode}.png").convert("RGB")
                      for mode in ("actual", "reference", "negative", "vanilla")}
            reference = images["reference"]
            if any(image.size != reference.size for image in images.values()):
                raise AssertionError("Glyph capture dimensions differ")
            sx, sy = reference.width / data["screen_width"], reference.height / data["screen_height"]
            top = max(0, int((data["glyph_y"] - 1) * sy))
            bottom = min(reference.height, int((data["glyph_y"] + data["glyph_height"] + 1) * sy + 1))
            regions = {"label": (data["label_x"], data["label_width"]), "digit": (data["glyph_x"], data["glyph_width"])}
            checks = {}
            for region, (x, width) in regions.items():
                left = max(0, int((x - 1) * sx))
                right = min(reference.width, int((x + width + 1) * sx + 1))
                mask = [(px, py) for py in range(top, bottom) for px in range(left, right) if white(reference.getpixel((px, py)))]
                if len(mask) < 12 * scale * scale:
                    raise AssertionError(f"{key}: incomplete {region} reference mask: {len(mask)} pixels")
                missing = {mode: sum(not white(images[mode].getpixel(point)) for point in mask)
                           for mode in ("actual", "negative", "vanilla")}
                checks[region] = {"reference_pixels": len(mask), "missing": missing, "region": [left, top, right, bottom]}
                if missing["actual"]:
                    raise AssertionError(f"{key}: actual {region} hides {missing['actual']}/{len(mask)} reference foreground pixels; {checks}")
            label = checks["label"]
            if label["missing"]["negative"] < max(1, label["reference_pixels"] // 100):
                raise AssertionError(f"{key}: wrong-order negative control failed to hide label pixels: {checks}")
            results[key] = checks
    print("Wireless glyph pixels PASS: " + json.dumps(results, sort_keys=True))
    return results


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("screenshots", type=Path)
    args = parser.parse_args()
    analyze(args.screenshots)
