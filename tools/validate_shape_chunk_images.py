#!/usr/bin/env python3
"""Validate owner mesh publication against fresh renderer data with an independent vanilla control."""
import argparse
import json
from pathlib import Path
import numpy as np
from PIL import Image


def image(path, bounds=None):
    pixels = np.asarray(Image.open(path).convert("RGB"), dtype=np.int32)
    height, width = pixels.shape[:2]
    # The vanilla HUD inverts the crosshair color: cyan world pixels become red UI pixels.
    pixels[height//2-12:height//2+13, width//2-12:width//2+13] = 0
    if bounds is None:
        radius = min(width, height)//4
        bounds = (width//2-radius, height//2-radius, width//2+radius, height//2+radius)
    x0, y0, x1, y1 = bounds
    assert 0 <= x0 < x1 <= width and 0 <= y0 < y1 <= height, (path, bounds)
    return pixels[y0:y1, x0:x1]


def cyan(pixels):
    r, g, b = pixels[:, :, 0], pixels[:, :, 1], pixels[:, :, 2]
    return ((g > r*1.25) & (b > r*1.25) & (g > b*.88) & (g < b*1.15)
            & (np.minimum(g, b) > 45) & (np.maximum(g, b)-r > 40))


def red(pixels):
    r, g, b = pixels[:, :, 0], pixels[:, :, 1], pixels[:, :, 2]
    return (r > g*1.35) & (r > b*1.35) & (r > 60) & (r-np.minimum(g, b) > 45)


def lime(pixels):
    r, g, b = pixels[:, :, 0], pixels[:, :, 1], pixels[:, :, 2]
    return (g > r*1.3) & (g > b*1.8) & (g > 40) & (g-b > 30)


def overlap(a, b):
    return float(np.count_nonzero(a & b)/max(1, np.count_nonzero(a | b)))


def owner_errors(ordinary, reference):
    left, right = cyan(ordinary), cyan(reference)
    errors = []
    if min(int(left.sum()), int(right.sum())) < 40:
        errors.append("owner artwork missing")
    iou = overlap(left, right)
    if iou < .90:
        errors.append(f"owner differs from fresh reference (IoU{iou:.3f})")
    selected = left | right
    rgb = float(np.abs(ordinary-reference)[selected].mean()) if selected.any() else 255.
    if rgb > 8:  # Retain tolerance for the wet owner's animated vanilla water overlay.
        errors.append(f"owner RGB differs from fresh reference ({rgb:.2f})")
    return errors, {"ordinary_fresh_iou": iou, "ordinary_fresh_rgb_delta": rgb}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--before-directory", type=Path,
                        help="Retained actual pre-fix ordinary frames, checked as owner-mesh negatives")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    rows = json.loads((args.directory/"modern-shape-chunks.json").read_text())
    assert len(rows) == 8 and {row["index"] for row in rows} == set(range(8))
    report, failures, before_rejected = [], [], []
    for row in rows:
        index = row["index"]
        phases = ("loaded", "unloaded", "forced-reference", "reloaded")
        paths = {phase: args.directory/f"modern-shape-chunk-{index}-{phase}.png" for phase in phases}
        owner = {phase: image(path, row["ownerBounds"]) for phase, path in paths.items()}
        masks = {phase: cyan(value) for phase, value in owner.items()}
        counts = {phase: int(value.sum()) for phase, value in masks.items()}
        assert min(counts.values()) >= 40, f"Owner artwork missing case{index}: {counts}"
        errors, metrics = owner_errors(owner["unloaded"], owner["forced-reference"])
        restore = overlap(masks["loaded"], masks["reloaded"])
        if restore < .85:
            errors.append(f"reload restoration IoU{restore:.3f}")
        red_counts = {phase: int(red(image(path, row["neighborBounds"])).sum()) for phase, path in paths.items()}
        lime_counts = {phase: int(lime(image(path, row["controlBounds"])).sum()) for phase, path in paths.items()}
        assert red_counts["loaded"] >= 40 and lime_counts["loaded"] >= 40, f"Neighbor/control not visibly established case{index}"
        # A fixed-camera synthetic forget can retain vanilla GPU buffers. The actual vanilla cube
        # independently identifies that condition; it never relaxes owner-vs-fresh publication.
        vanilla_ghost = lime_counts["unloaded"] > max(10, lime_counts["loaded"]*.05)
        assert lime_counts["forced-reference"] <= max(10, lime_counts["loaded"]*.05), f"Fresh cache retained vanilla control case{index}"
        assert red_counts["forced-reference"] <= max(10, red_counts["loaded"]*.05), f"Fresh cache retained exact target red neighbor case{index}"
        if not vanilla_ghost and red_counts["unloaded"] > max(10, red_counts["loaded"]*.05):
            errors.append("mod neighbor persists while independent vanilla control disappeared")
        assert lime_counts["reloaded"] >= 40, f"Vanilla control did not reload case{index}"
        if errors:
            failures.append({"index": index, "errors": errors})
        assert owner_errors(owner["loaded"], owner["forced-reference"])[0], f"Same-run old joined owner mesh negative accepted case{index}"
        if args.before_directory:
            old = image(args.before_directory/f"modern-shape-chunk-{index}-unloaded.png", row["ownerBounds"])
            old_errors, old_metrics = owner_errors(old, owner["forced-reference"])
            assert old_errors, f"Actual retained no-op owner publication negative accepted case{index}"
            before_rejected.append({"index": index, "errors": old_errors, **old_metrics})
        report.append({"index": index, "owner_cyan_pixels": counts, "target_red_pixels": red_counts,
                       "vanilla_lime_pixels": lime_counts, "vanilla_in_view_forget_retention": vanilla_ghost,
                       "loaded_reloaded_iou": restore, **metrics})
    sky = cyan(image(args.directory/"modern-shape-chunk-negative-sky.png"))
    assert int(sky.sum()) < 10, "Actual sky negative contains owner artwork"
    result = {"cases": report, "failures": failures, "sky_negative_pixels": int(sky.sum()),
              "retained_noop_owner_negatives": before_rejected,
              "same_run_negative": "actual joined owner mesh rejected as unloaded publication"}
    if args.report:
        args.report.write_text(json.dumps(result, indent=2)+"\n")
    if failures:
        for failure in failures:
            print(f"FAIL case{failure['index']}: {'; '.join(failure['errors'])}")
        raise SystemExit("FAIL actual owner chunk renderer publication")
    print("PASS8 exact owner ROIs match fresh rebuilds; independent vanilla control classifies in-view forget caches; target neighbors vanish in fresh references; reload/sky/old-owner negatives pass")


if __name__ == "__main__":
    main()
