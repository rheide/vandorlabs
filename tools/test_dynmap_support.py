#!/usr/bin/env python3
"""Validate the bundled, dependency-free Dynmap render definitions."""

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/vandorlabs"
TEXTURES = ROOT / "texture-packs/default/assets/vandorlabs/textures"


def records(path, prefix):
    found = set()
    empty = []
    pattern = re.compile(r"^%s:id=%%([^,]+),state=([^,]+)(.*)$" % prefix)
    for number, line in enumerate(path.read_text().splitlines(), 1):
        match = pattern.match(line)
        if match:
            found.add((match.group(1), match.group(2)))
            if not match.group(3):
                empty.append(number)
    if empty:
        raise AssertionError("%s contains empty records at %s" % (path, empty[:10]))
    return found


def main():
    model_path = ASSETS / "dynmap-models.txt"
    texture_path = ASSETS / "dynmap-texture.txt"
    models = records(model_path, "modellist")
    mappings = records(texture_path, "block")
    custom = records(model_path, "customblock")
    models.update(custom)
    if not models.issubset(mappings):
        raise AssertionError("Dynmap models lack matching texture states")
    for name, renderer in (
            ("programmable_slab", "ProgrammableSlabRenderer"),
            ("programmable_trapdoor", "ProgrammableTrapdoorRenderer"),
            ("programmable_diagonal_trapdoor", "ProgrammableDiagonalTrapdoorRenderer"),
            ("programmable_door", "ProgrammableDoorRenderer"),
            ("large_programmable_door", "LargeProgrammableDoorRenderer"),
            ("controlled_ramp", "ControlledRampRenderer")):
        if (name, "*") not in custom or not re.search(
                r"^customblock:id=%%%s,state=\*,class=com\.vandorlabs\.dynmap\.%s$" %
                (name, renderer), model_path.read_text(), re.MULTILINE):
            raise AssertionError("missing tile-aware Dynmap renderer for " + name)
        if not (ROOT / "src/main/java/com/vandorlabs/dynmap" /
                (renderer + ".java")).is_file():
            raise AssertionError("missing Dynmap renderer class " + renderer)
    # These Minecraft models have no JSON elements: Dynmap can only see them
    # when every listed blockstate has an explicit model and texture mapping.
    dynamic = (
        "programmable_block", "programmable_light", "programmable_trigger_block",
        "programmable_slab", "programmable_wall", "programmable_porthole_wall",
        "programmable_porthole_block", "programmable_diagonal_wall",
        "programmable_stairs", "controlled_ramp",
    )
    for name in dynamic:
        variants = json.loads((ASSETS / "blockstates" / (name + ".json")).read_text())["variants"]
        wanted = {(name, state.replace("=", ":").replace(",", "/"))
                  for state in variants}
        missing = wanted - models if (name, "*") not in custom else set()
        if missing:
            raise AssertionError("missing Dynmap model states for %s: %s" %
                                 (name, sorted(missing)[:5]))
    texture_only = {"dark_wall_panel", "light_wall_panel", "ribbed_wall"}
    invalid = sorted(name for name, _ in models if name in texture_only)
    if invalid:
        raise AssertionError("texture-only names emitted as Dynmap blocks: " + repr(invalid))
    if re.search(r"[eunsdw]/\d+(?:/-?\d+(?:\.\d+)?){4}",
                 model_path.read_text()):
        raise AssertionError("scanner UV bounds were not normalized")
    if re.search(r"^modellist:id=%(?:rocket_thruster|ion_drive|plasma_vent|impulse_engine),.*?/5\.750000:",
                 model_path.read_text(), re.MULTILINE):
        raise AssertionError("propulsion emitter faces are too thin for Dynmap")
    finish_count = len(re.findall(r'new Finish\("[^"]+", "[^"]+"\)',
                                  (ROOT / "src/main/java/com/vandorlabs/tiles/ScreenHousingTextures.java").read_text()))
    if "block:id=%controlled_ramp,state=*,patch0=" not in texture_path.read_text() \
            or "patch%d=0:v12_source_stone" % finish_count not in texture_path.read_text():
        raise AssertionError("ramp source texture mapping is incomplete")

    texture_text = texture_path.read_text()
    declarations = dict(re.findall(
        r"^texture:id=([^,]+),filename=([^,]+)",
        texture_text, re.MULTILINE))
    missing_files = sorted(name for name, relative in declarations.items()
                           if relative.startswith("assets/vandorlabs/textures/")
                           and not any((folder / relative.split("assets/vandorlabs/textures/",1)[1]).is_file()
                                       for folder in (TEXTURES, ROOT / "texture-packs/additional/assets/vandorlabs/textures")))
    if missing_files:
        raise AssertionError("missing declared textures: " + repr(missing_files))
    referenced = set(re.findall(r"patch\d+=\d+:([^,\n]+)", texture_text))
    unknown = sorted(referenced - set(declarations))
    if unknown:
        raise AssertionError("undeclared Dynmap texture ids: " + repr(unknown))

    catalog = json.loads((ROOT / "generated-resources/assets/vandorlabs/data/blocks.json").read_text())
    expected = {entry["id"] for entry in catalog
                if not entry.get("retired")
                and not entry.get("programmable_only")
                and not entry.get("internal_model")}
    expected.update({
        "industrial_power_lever", "compact_power_lever", "programmable_viewscreen",
        "programmable_console", "programmable_diagonal_screen",
        "programmable_half_input", "programmable_half_console",
        "programmable_input", "programmable_ramp", "industrial_table",
    })
    present = set(re.findall(r"^block:id=%([^,]+)", texture_text,
                             re.MULTILINE))
    missing_blocks = sorted(expected - present)
    if missing_blocks:
        raise AssertionError("blocks missing Dynmap definitions: " + repr(missing_blocks))

    print("Dynmap support PASS (%d states, %d blocks, %d textures)"
          % (len(mappings), len(present), len(declarations)))


if __name__ == "__main__":
    main()
