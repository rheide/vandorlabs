#!/usr/bin/env python3
"""Copy documentation-worthy ReproLab shots to stable GitHub paths."""

import argparse
from material_catalog import choices as material_choices
import re
import shutil
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "docs/images/gallery"
SHOTS = {
    "gallery_storage_face_overrides": "storage/face-overrides.png",
    "gallery_storage_hotbar": "storage/hotbar.png",
    "gallery_storage_sets": "storage/sets.png",
    "gallery_storage_inventory": "storage/inventory.png",
    "gallery_storage_picker": "storage/picker.png",
    "gallery_trapdoor_followup_vanilla_alignment_open":"tasks/trapdoor-vanilla-alignment-open.png",
    **{f"gallery_trapdoor_{group}_{motion}_{pose}":f"tasks/trapdoor-{group.replace('_','-')}-{motion.replace('_','-')}-{pose}.png" for group in ("patch_halfwidth_horizontal","patch_halfwidth_stagger","patch_shallow_horizontal","patch_shallow_stagger") for motion in ("rotating","sliding","inset_sliding") for pose in ("closed","open")},
    **{f"custom_{family}_texture_gui":f"tasks/custom-{family}-texture-config.png" for family in ("block","slab","door","trapdoor")},
    "diagonal_trapdoor_into_wall_gui": "tasks/diagonal-trapdoor-into-wall-config.png",
    "gallery_trapdoor_followup_opposing_next_closed": "tasks/trapdoor-opposing-next-closed.png",
    "gallery_trapdoor_followup_opposing_next_open": "tasks/trapdoor-opposing-next-open.png",
    **{f"gallery_trapdoor_{group}_{motion}_{pose}":f"tasks/trapdoor-{group.replace('_','-')}-{motion}-{pose}.png" for group in ("stagger_halfwidth","stagger_shallow") for motion in ("rotating","sliding") for pose in ("closed","open")},
    "gallery_trapdoor_followup_diagonal_opposite_slopes_sliding_open": "tasks/trapdoor-diagonal-opposite-slopes-sliding-open.png",
    "gallery_trapdoor_followup_diagonal_reversed_plane_sliding_open": "tasks/trapdoor-diagonal-reversed-plane-sliding-open.png",
    "gallery_trapdoor_followup_diagonal_opposite_slopes_open": "tasks/trapdoor-diagonal-opposite-slopes-open.png",
    "gallery_trapdoor_followup_diagonal_reversed_plane_open": "tasks/trapdoor-diagonal-reversed-plane-open.png",
    "diagonal_trapdoor_hotbar": "tasks/diagonal-trapdoor-hotbar.png",
    "duplifier_door_settings_gui": "tools/duplifier-door-config.png",
    "gallery_trapdoor_followup_flush_floor": "tasks/trapdoor-flush-floor.png",
    "gallery_trapdoor_followup_flush_ceiling": "tasks/trapdoor-flush-ceiling.png",
    "gallery_v12_door_fit": "tasks/door-face-fit.png",
    "gallery_v12_door_tile": "tasks/door-face-tile.png",
    "gallery_v12_door_block_half": "tasks/door-block-half.png",
    "gallery_v12_gear_extra_large": "v1.2/gear-extra-large.png",
    "gallery_v12_input_ceiling": "v1.2/input-ceiling.png",
    "gallery_v12_light_shapes": "v1.2/light-shapes.png",
    "gallery_propulsion_wall_bottom": "v1.2/propulsion-wall-bottom.png",
    "gallery_propulsion_wall_top": "v1.2/propulsion-wall-top.png",
    "gallery_v12_materials": "tasks/material-choices.png",
    "gallery_v12_gear_corner": "tasks/gear-2x2.png",
    "gallery_v12_gear_cover_closed": "tasks/gear-cover-closed.png",
    "gallery_v12_gear_cover_open": "tasks/gear-cover-open.png",
    "duplifier_apply_settings_gui": "tools/duplifier-config.png",
    "console_gui": "programmable/console-config.png",
    "input_gui": "programmable/input-config.png",
    "half_console_gui": "programmable/half-console-config.png",
    "full_input_gui": "programmable/full-input-config.png",
    "gallery_connected_thruster": "propulsion/connected-particle-mode.png",
    "ramp_matching_gui": "v1.1/ramp-matching-config.png",
    "ramp_controller_gui": "systems/ramp-controller-config.png",
    "gallery_ramp_up_smooth": "ramp-controller/up-smooth.png",
    "gallery_ramp_up_stairs": "ramp-controller/up-stairs.png",
    "gallery_ramp_down_smooth": "ramp-controller/down-smooth.png",
    "gallery_ramp_down_stairs": "ramp-controller/down-stairs.png",
}

SHOTS["trapdoor_gui"]="tasks/trapdoor-config.png"
SHOTS["trapdoor_joined_gui"]="tasks/trapdoor-joined-config.png"
SHOTS["offset_trapdoor_closed_selection"]="tasks/trapdoor-offset-closed-selection.png"
SHOTS["offset_trapdoor_open_selection"]="tasks/trapdoor-offset-open-selection.png"
SHOTS["diagonal_trapdoor_gui"]="tasks/diagonal-trapdoor-config.png"
for scene in ("flat_door_tile","flat_door_fit","flat_custom_door_tile","diagonal_door_tile","diagonal_door_fit","diagonal_custom_door_tile","diagonal_slide_wall","flat_rotate_neighbors","next_rotating_closed","next_rotating_open","next_sliding_closed","next_sliding_open"):
    SHOTS[f"gallery_trapdoor_followup_{scene}"]=f"tasks/trapdoor-{scene.replace('_','-')}.png"

for group in ("flat", "v", "rectangle", "stagger"):
    for motion in ("rotating", "sliding"):
        for pose in ("closed", "open"):
            SHOTS[f"gallery_trapdoor_{group}_{motion}_{pose}"] = f"tasks/trapdoor-{group}-{motion}-{pose}.png"

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

finish_catalog = (ROOT / "src/main/java/com/vandorlabs/tiles/ScreenHousingTextures.java").read_text()
finish_count = len(re.findall(r'new Finish\("', finish_catalog))
for page in range((finish_count + 9) // 10):
    SHOTS[f"gallery_finish_overview_{page}"] = f"building/finishes-{page + 1:02d}.png"

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

for name in ("faces", "seating", "seating_heights", "seating_unjoined", "gear", "gear_extended", "gear_four", "gear_half", "gear_retracted", "portholes",
             "half_height", "fill", "half_console", "controller", "stairs",
             "portholes_stacked", "portholes_half_height", "portholes_half_height_unjoined", "shallow_fill", "shallow_fill_under", "light_depth_near", "light_depth_far", "light_depth_oblique",
             "round_glass_front", "round_glass_left", "round_glass_right", "round_glass_back", "filled_corners_inside", "filled_corners_outside"):
    SHOTS[f"gallery_v12_{name}"] = f"v1.1/{name.replace('_', '-')}.png"
SHOTS["diagonal_direction_gui"] = "v1.1/diagonal-direction-config.png"
SHOTS["connected_seat_hotbar"] = "v1.1/seat-and-gear-icons.png"
SHOTS["connected_seat_gui"] = "v1.1/connected-seat-config.png"
SHOTS["landing_gear_gui"] = "v1.1/landing-gear-config.png"
SHOTS["programmable_face_overrides_gui"] = "v1.1/face-overrides-config.png"
SHOTS["programmable_diagonal_width_gui"] = "v1.1/diagonal-config.png"


# Armor has its own live suite; the main full-gallery run does not capture it.
FOCUSED_SHOTS = {
    "armor_stand_set_" + role: "armor/" + role.replace("_", "-") + ".png"
    for role in ("bioengineer", "scientist", "hazmat", "repairman", "pilot",
                 "civilian_staff", "spaceship_staff", "security_rescue")
}

for index,name in ((0,"thruster-lever"),(2,"airliner-throttle"),(3,"fighter-throttle")):
    FOCUSED_SHOTS[f"channels_{index+9}"]=f"controls/{name}-config.png"
    for step,setting in ((0,"standard"),(1,"raised-2px"),(2,"raised-4px"),(3,"tilted")):
        FOCUSED_SHOTS[f"controls_{index}_{step}_floor"]=f"controls/{name}-{setting}.png"

for role in ("bioengineer","scientist","hazmat","repairman","pilot","civilian_staff","spaceship_staff","security_rescue"):
    FOCUSED_SHOTS[f"armor_stand_set_{role}_open"]=f"armor/{role.replace('_','-')}-open.png"

for page in range((len(material_choices())+9)//10):
    SHOTS["gallery_catalog_material_"+str(page)] = f"building/current-materials-{page+1:02d}.png"
door_names = re.search(r'DESIGNS=\{(.*?)\};', (ROOT / "src/main/java/com/vandorlabs/tiles/TileEntitySpaceDoor.java").read_text(), re.S).group(1)
for index,name in enumerate(re.findall(r'"([^"]+)"',door_names)):
    SHOTS["gallery_catalog_door_"+str(index)] = "doors/design-"+("reactor-service" if name=="reactor" else name.replace("_","-"))+".png"

FOCUSED_SHOTS["armor_worn_and_icons"] = "armor/block-materials.png"
FOCUSED_SHOTS["armor_stand_picker"] = "armor/stand-picker.png"
FOCUSED_SHOTS["armor_custom_picker"] = "armor/custom-picker.png"
FOCUSED_SHOTS["armor_duplifier_options"] = "tools/duplifier-config.png"


def main():
    parser = argparse.ArgumentParser(description="Incrementally export available gallery captures; preserve unrelated images.")
    parser.add_argument("source", type=Path)
    parser.add_argument("--full", action="store_true", help="Require every mapped screenshot before exporting anything")
    parser.add_argument("--prefix", action="append", default=[], help="Export only mapped shots with this prefix; may repeat")
    parser.add_argument("--only", nargs="+", default=[], help="Export only these exact shot names")
    args = parser.parse_args()
    if args.full and (args.prefix or args.only):
        parser.error("--full cannot be combined with a selection")
    mapped = SHOTS if args.full else {**SHOTS, **FOCUSED_SHOTS}
    unknown = set(args.only) - mapped.keys()
    if unknown:
        parser.error("Unknown shots: " + ", ".join(sorted(unknown)))
    selected = {name: dest for name, dest in mapped.items()
                if not (args.prefix or args.only) or name in args.only
                or any(name.startswith(prefix) for prefix in args.prefix)}
    if not selected:
        parser.error("No mapped shots match the selection")
    available = {name: dest for name, dest in selected.items()
                 if (args.source / ("shot_" + name + ".png")).is_file()}
    if args.full or args.prefix or args.only:
        missing = selected.keys() - available.keys()
        if missing:
            raise SystemExit("Missing gallery screenshots: " + ", ".join(sorted(missing)))
    if not available:
        raise SystemExit("No mapped screenshots in " + str(args.source))
    changed = 0
    for name, relative in available.items():
        source = args.source / ("shot_" + name + ".png")
        target = DEST / relative
        if target.exists() and source.read_bytes() == target.read_bytes():
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        changed += 1
    print(f"Exported {changed} changed gallery images; {len(available)-changed} unchanged; unrelated images preserved")


if __name__ == "__main__":
    main()
