#!/usr/bin/env python3
"""Copy documentation-worthy ReproLab shots to stable GitHub paths."""

import shutil
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "docs/images/gallery"
SHOTS = {
    "gallery_programmable_displays": "programmable/display-catalog.png",
    "gallery_programmable_inputs": "programmable/input-catalog.png",
    "gallery_programmable_full_inputs": "programmable/full-input-catalog.png",
    "console_gui": "programmable/console-config.png",
    "input_gui": "programmable/input-config.png",
    "half_console_gui": "programmable/half-console-config.png",
    "full_input_gui": "programmable/full-input-config.png",
    "gallery_space_sliding_framed_closed": "doors/space-sliding-closed.png",
    "gallery_space_sliding_framed_open": "doors/space-sliding-open.png",
    "gallery_space_rotating_framed_closed": "doors/space-rotating-closed.png",
    "gallery_space_rotating_framed_open": "doors/space-rotating-open.png",
    "gallery_propulsion": "propulsion/shape-catalog.png",
    "gallery_connected_thruster": "propulsion/connected-particle-mode.png",
    "gallery_lighting_controls": "systems/lighting-controls.png",
    "gallery_structure": "systems/structure-catalog.png",
    "gallery_chairs": "systems/bridge-chairs.png",
    "ramp_controller_gui": "systems/ramp-controller-config.png",
    "gallery_ramp_up_smooth": "ramp-controller/up-smooth.png",
    "gallery_ramp_up_stairs": "ramp-controller/up-stairs.png",
    "gallery_ramp_down_smooth": "ramp-controller/down-smooth.png",
    "gallery_ramp_down_stairs": "ramp-controller/down-stairs.png",
}


def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: export_gallery.py RENDER_RUN_DIRECTORY")
    source = Path(sys.argv[1]).resolve()
    if DEST.exists():
        shutil.rmtree(DEST)
    missing = []
    for shot, relative in SHOTS.items():
        image = source / f"shot_{shot}.png"
        if not image.is_file():
            missing.append(image.name)
            continue
        target = DEST / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(image, target)
    if missing:
        raise SystemExit("missing gallery screenshots: " + ", ".join(missing))
    print(f"Exported {len(SHOTS)} gallery screenshots to {DEST}")


if __name__ == "__main__":
    main()
