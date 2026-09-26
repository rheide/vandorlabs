#!/usr/bin/env python3
"""Copy documentation-worthy ReproLab shots to stable GitHub paths."""

import shutil
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "docs/images/gallery"
SHOTS = {
    "console_gui": "programmable/console-config.png",
    "input_gui": "programmable/input-config.png",
    "half_console_gui": "programmable/half-console-config.png",
    "full_input_gui": "programmable/full-input-config.png",
    "gallery_connected_thruster": "propulsion/connected-particle-mode.png",
    "ramp_controller_gui": "systems/ramp-controller-config.png",
    "gallery_ramp_up_smooth": "ramp-controller/up-smooth.png",
    "gallery_ramp_up_stairs": "ramp-controller/up-stairs.png",
    "gallery_ramp_down_smooth": "ramp-controller/down-smooth.png",
    "gallery_ramp_down_stairs": "ramp-controller/down-stairs.png",
}

for name in ("viewscreen", "console", "diagonal_up", "diagonal_down",
             "input_wall", "input_keyboard", "half_console", "full_input_wall",
             "full_input_floor"):
    SHOTS[f"gallery_close_display_{name}"] = f"programmable/{name.replace('_', '-')}.png"

for family in ("rocket_thruster", "ion_drive", "plasma_vent", "impulse_engine"):
    for shape in ("block", "hexagon", "wedge"):
        SHOTS[f"gallery_close_thruster_{family}_{shape}"] = (
            f"propulsion/{family.replace('_', '-')}-{shape}.png")

for index, name in enumerate(("porthole", "light-column", "slatted-lamp",
                              "window-lamp", "lightbar", "logo")):
    SHOTS[f"gallery_close_light_{index}"] = f"lights/{name}.png"

for name in ("push_button", "rocker_switch", "compact_power_lever",
             "industrial_power_lever"):
    SHOTS[f"gallery_close_control_{name}"] = f"controls/{name.replace('_', '-')}.png"

for index, name in enumerate(("command", "companion", "operator",
                              "conference", "mess-hall")):
    SHOTS[f"gallery_close_chair_{index}"] = f"chairs/{name}.png"

for name in ("hull", "padding", "pipes", "glass"):
    SHOTS[f"gallery_close_material_{name}"] = f"building/{name}.png"

for index, name in enumerate(("observation", "airlock", "standard", "security",
                              "reactor-service", "viewport", "laboratory", "cargo",
                              "ventilation", "cargo-lift", "blast-shield",
                              "glazed-hangar", "quarantine-seal", "reactor-barrier",
                              "modular-shutter")):
    SHOTS[f"gallery_door_design_{index}"] = f"doors/design-{name}.png"

for motion in ("rotating", "sideways", "up", "down"):
    for pose in ("closed", "open"):
        SHOTS[f"gallery_door_motion_{motion}_{pose}"] = (
            f"doors/{motion}-{pose}.png")

for depth in ("near", "middle", "far"):
    SHOTS[f"gallery_door_position_{depth}"] = f"doors/position-{depth}.png"

for panel in ("on", "off"):
    SHOTS[f"gallery_door_panel_{panel}"] = f"doors/panel-{panel}.png"

for mode in ("ramp", "filled", "lift", "extend"):
    for pose in ("off", "on"):
        SHOTS[f"gallery_ramp_mode_{mode}_{pose}"] = (
            f"ramp-controller/{mode}-{pose}.png")

SHOTS.update({
    "space_door_gui": "doors/door-config.png",
    "programmable_block_gui": "programmable/block-config.png",
    "programmable_light_gui": "lights/light-config.png",
    "thruster_gui": "propulsion/thruster-config.png",
    "programmable_glass_gui": "building/glass-config.png",
})


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
