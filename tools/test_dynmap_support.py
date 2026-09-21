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
    if not models.issubset(mappings):
        raise AssertionError("Dynmap models lack matching texture states")
    if re.search(r"[eunsdw]/\d+(?:/-?\d+(?:\.\d+)?){4}",
                 model_path.read_text()):
        raise AssertionError("scanner UV bounds were not normalized")

    texture_text = texture_path.read_text()
    declarations = dict(re.findall(
        r"^texture:id=([^,]+),filename=assets/vandorlabs/textures/([^,]+)",
        texture_text, re.MULTILINE))
    missing_files = sorted(name for name, relative in declarations.items()
                           if not (TEXTURES / relative).is_file())
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
        "industrial_lever", "compact_lever", "animated_screen_selector",
        "programmable_console", "programmable_diagonal_screen",
        "programmable_input", "programmable_half_console",
        "programmable_full_input", "ramp_controller",
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
