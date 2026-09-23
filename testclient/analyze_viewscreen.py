#!/usr/bin/env python3
"""Pixel assertions for the live programmable-block and door render scene."""

import argparse
import json
from pathlib import Path

from PIL import Image
from PIL import ImageChops, ImageStat


# The cameras in ReproLab put the middle of the tested face in this region.
SAMPLE_BOX = (610, 330, 670, 390)


def pixels(image):
    getter = getattr(image, "get_flattened_data", image.getdata)
    return getter()


def mean_chroma(path, box=SAMPLE_BOX):
    image = Image.open(path).convert("RGB").crop(box)
    pixels = image.load()
    chroma = [
        max(pixels[x, y]) - min(pixels[x, y])
        for y in range(image.height)
        for x in range(image.width)
    ]
    return sum(chroma) / len(chroma)


def mean_detail(path, box=SAMPLE_BOX):
    """Mean per-channel standard deviation, used for intentionally subdued art."""
    image = Image.open(path).convert("RGB").crop(box)
    return sum(ImageStat.Stat(image).stddev) / 3.0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("shots", type=Path)
    parser.add_argument("--texture-variant", action="store_true",
                        help="skip palette-specific assertions for canonical HD art")
    args = parser.parse_args()

    required = {
        name: args.shots / ("shot_%s.png" % name)
        for name in ("selector_top", "selector_back", "control_top", "control_back",
                     "console_front")
    }
    missing = [str(path) for path in required.values() if not path.is_file()]
    if missing:
        raise SystemExit("missing live-client screenshots: " + ", ".join(missing))

    values = {
        name: mean_chroma(path)
        for name, path in required.items()
        if name != "console_front"
    }
    print("viewscreen center-face mean chroma (lower is neutral wall-panel texture):")
    for name in sorted(values):
        print("  %-13s %.2f" % (name, values[name]))

    failures = []
    # The Dark Wall Panel control is deliberately used as the live reference;
    # an allowance covers the selector's brighter emitted block light.
    for face in ("top", "back"):
        selector = values["selector_" + face]
        control = values["control_" + face]
        if selector > control + 12.0:
            failures.append(
                "%s face is not housing (selector %.2f, control %.2f)"
                % (face, selector, control)
            )

    console = required["console_front"]
    console_screen = mean_chroma(console, (600, 320, 680, 400))
    console_keyboard = mean_chroma(console, (595, 442, 685, 458))
    console_housing = mean_chroma(console, (565, 463, 715, 472))
    console_housing_detail = mean_detail(console, (565, 463, 715, 472))
    print("console regions: screen %.2f, keyboard %.2f, housing %.2f"
          % (console_screen, console_keyboard, console_housing))
    if console_screen < console_housing + 12.0:
        failures.append("console programmable screen art is not visible")
    if console_keyboard < console_housing + 8.0:
        failures.append("console keyboard art is not visible")

    diag = args.shots / "shot_console_diag.png"
    if not diag.is_file():
        failures.append("missing diagonal console screenshot")
    else:
        triangle = Image.open(diag).convert("RGB").crop((695, 335, 710, 385))
        triangle_luma = sum(sum(p) / 3.0 for p in pixels(triangle)) \
            / (triangle.width * triangle.height)
        print("console side-wedge mean luminance %.2f" % triangle_luma)
        if triangle_luma > 95.0:
            failures.append("console side triangle is transparent or missing")

    for frame in range(6):
        path = args.shots / ("shot_selector_anim_%d.png" % frame)
        if not path.is_file():
            failures.append("missing animated frame screenshot %d" % frame)
            continue
        image = Image.open(path).convert("RGB").crop((565, 285, 715, 435))
        luma = sum(sum(p) / 3.0 for p in pixels(image)) / (image.width * image.height)
        print("selector animation slot %d mean luminance %.2f" % (frame, luma))
        if luma < 5.0:
            failures.append("selector animation slot %d rendered black" % frame)

    input_catalog = Path(
        "generated-resources/assets/vandorlabs/data/input_surfaces.json")
    inputs = sorted(entry["id"] for entry in json.loads(input_catalog.read_text()))
    for input_id in inputs:
        path = args.shots / ("shot_console_input_%s.png" % input_id)
        if not path.is_file():
            failures.append("missing console input screenshot %s" % input_id)
            continue
        chroma = mean_chroma(path, (595, 442, 685, 458))
        detail = mean_detail(path, (595, 442, 685, 458))
        print("console input %-20s mean chroma %.2f, detail %.2f"
              % (input_id, chroma, detail))
        # Some authored control faces (especially the framed starmap) are
        # intentionally dark and subdued. Accept either color separation or
        # visible fine detail relative to the neutral console housing.
        if (chroma < console_housing + 2.0
                and detail < console_housing_detail + 3.0):
            failures.append("console input %s did not render" % input_id)

    gui_path = args.shots / "shot_console_gui.png"
    if not gui_path.is_file():
        failures.append("missing live console selector GUI screenshot")
    else:
        gui = Image.open(gui_path).convert("RGB")
        # At the harness's 2x GUI scale the two 96px-tall lists occupy these
        # independent columns. Both must contain visible text/selection detail.
        screen_list = ImageStat.Stat(gui.crop((176, 162, 528, 354))).stddev
        input_list = ImageStat.Stat(gui.crop((552, 162, 856, 354))).stddev
        screen_detail = sum(screen_list) / 3.0
        input_detail = sum(input_list) / 3.0
        preview_screen = mean_chroma(gui_path, (910, 110, 1050, 235))
        preview_input = mean_chroma(gui_path, (910, 260, 1050, 320))
        print("console GUI list detail: screens %.2f, controls %.2f"
              % (screen_detail, input_detail))
        print("console GUI preview chroma: screen %.2f, controls %.2f"
              % (preview_screen, preview_input))
        if screen_detail < 8.0 or input_detail < 8.0:
            failures.append("console GUI does not show two populated peer lists")
        if preview_screen < 10.0 or preview_input < 10.0:
            failures.append("console GUI preview is missing screen or control artwork")

    diagonal_views = {
        "front": ((575, 305, 705, 430), 20.0),
        "up": ((575, 305, 705, 430), 15.0),
        "down": ((575, 305, 705, 430), 15.0),
    }
    for view, (box, minimum_chroma) in diagonal_views.items():
        path = args.shots / ("shot_diagonal_%s.png" % view)
        if not path.is_file():
            failures.append("missing diagonal-screen %s screenshot" % view)
            continue
        chroma = mean_chroma(path, box)
        print("diagonal screen %-7s mean chroma %.2f" % (view, chroma))
        if chroma < minimum_chroma:
            failures.append("diagonal-screen art is not visible in %s placement" % view)
    side_path = args.shots / "shot_diagonal_side.png"
    if not side_path.is_file():
        failures.append("missing diagonal-screen side screenshot")
    else:
        side = Image.open(side_path).convert("RGB").crop((680, 350, 720, 430))
        side_luma = sum(sum(p) / 3.0 for p in pixels(side)) / (side.width * side.height)
        print("diagonal screen solid side mean luminance %.2f" % side_luma)
        if side_luma > 90.0:
            failures.append("diagonal-screen triangular side is transparent or missing")

    wide_path = args.shots / "shot_wide_ship_pair.png"
    if not wide_path.is_file():
        failures.append("missing two-block programmable display screenshot")
    else:
        wide_left = mean_chroma(wide_path, (490, 280, 630, 450))
        wide_right = mean_chroma(wide_path, (650, 280, 790, 450))
        print("two-block programmable display chroma: left %.2f, right %.2f"
              % (wide_left, wide_right))
        if wide_left < 15.0 or wide_right < 15.0:
            failures.append("both two-block programmable display halves did not render")

    for name in ("input_wall", "input_keyboard", "half_console",
                 "full_input_wall", "full_input_floor"):
        path = args.shots / ("shot_%s.png" % name)
        if not path.is_file():
            failures.append("missing new programmable-block screenshot: %s" % name)
            continue
        detail = sum(ImageStat.Stat(Image.open(path).convert("RGB").crop(
            (560, 300, 720, 455))).stddev) / 3.0
        print("%-24s render detail %.2f" % (name, detail))
        if detail < 12.0:
            failures.append("%s geometry/input art did not render" % name)

    input_modes = {}
    for name in ("off", "static") + tuple("anim_%d" % i for i in range(6)):
        path = args.shots / ("shot_input_surface_%s.png" % name)
        if not path.is_file():
            failures.append("missing animated-input mode screenshot: %s" % name)
        else:
            input_modes[name] = Image.open(path).convert("RGB").crop((560, 325, 720, 365))
    if len(input_modes) == 8:
        mode_diff = sum(ImageStat.Stat(ImageChops.difference(
            input_modes["off"], input_modes["static"])).mean) / 3.0
        frame_diffs = [sum(ImageStat.Stat(ImageChops.difference(
            input_modes["anim_%d" % i], input_modes["anim_%d" % (i + 1)])).mean) / 3.0
                       for i in range(5)]
        print("animated input off/static difference %.2f; frame differences %s"
              % (mode_diff, ", ".join("%.2f" % value for value in frame_diffs)))
        if mode_diff < 2.0:
            failures.append("input Off and Static modes render identically")
        # Screenshot timing can land near either side of a frame boundary, so
        # judge motion across the sampled sequence instead of requiring one
        # particular adjacent pair to contain a large change.
        if sum(value > 0.15 for value in frame_diffs) < 3 or sum(frame_diffs) < 1.0:
            failures.append("input Animated mode does not advance through its frames")

    half_gui_path = args.shots / "shot_half_console_gui.png"
    if not half_gui_path.is_file():
        failures.append("missing half-console two-list GUI screenshot")
    else:
        gui = Image.open(half_gui_path).convert("RGB")
        left_detail = sum(ImageStat.Stat(gui.crop((330, 175, 625, 360))).stddev) / 3.0
        right_detail = sum(ImageStat.Stat(gui.crop((655, 175, 950, 360))).stddev) / 3.0
        print("half-console GUI list detail: front %.2f, rear %.2f"
              % (left_detail, right_detail))
        if left_detail < 8.0 or right_detail < 8.0:
            failures.append("half-console GUI does not show two populated input lists")
    input_gui_path = args.shots / "shot_input_gui.png"
    if not input_gui_path.is_file():
        failures.append("missing programmable-input selector GUI screenshot")
    else:
        input_detail = sum(ImageStat.Stat(Image.open(input_gui_path).convert("RGB").crop(
            (390, 170, 700, 370))).stddev) / 3.0
        print("programmable-input GUI detail %.2f" % input_detail)
        if input_detail < 8.0:
            failures.append("programmable-input GUI list/preview is empty")
    full_gui_path = args.shots / "shot_full_input_gui.png"
    if not full_gui_path.is_file():
        failures.append("missing full programmable-input GUI screenshot")
    else:
        full_detail = sum(ImageStat.Stat(Image.open(full_gui_path).convert("RGB").crop(
            (300, 120, 980, 600))).stddev) / 3.0
        print("full programmable-input GUI detail %.2f" % full_detail)
        if full_detail < 12.0:
            failures.append("full programmable-input regular animation list is empty")

    hotbar_path = args.shots / "shot_item_hotbar.png"
    if not hotbar_path.is_file():
        failures.append("missing representative item hotbar screenshot")
    else:
        hotbar = Image.open(hotbar_path).convert("RGB")
        # Vanilla's 182 px hotbar is centered at the bottom of a 1280x720
        # test frame. Crop inside each 20 px slot, excluding slot borders.
        icons = [hotbar.crop((552 + i * 20, 700, 568 + i * 20, 716))
                 for i in range(9)]
        detail = [sum(ImageStat.Stat(icon).stddev) / 3.0 for icon in icons]
        distinct = [sum(ImageStat.Stat(ImageChops.difference(
            icons[i], icons[i + 1])).mean) / 3.0 for i in range(8)]
        missing_pixels = [sum(1 for r, g, b in pixels(icon)
                              if r > 180 and b > 140 and g < 80)
                          for icon in icons]
        print("hotbar icon detail: %s" % ", ".join("%.2f" % x for x in detail))
        print("hotbar adjacent differences: %s" %
              ", ".join("%.2f" % x for x in distinct))
        if sum(value > 5.0 for value in detail) < 8:
            failures.append("one or more programmable/control hotbar icons are blank")
        if sum(value > 1.0 for value in distinct) < 7:
            failures.append("programmable/control hotbar icons are not distinguishable")
        if any(count > 2 for count in missing_pixels):
            failures.append("one or more hotbar icons use the magenta missing texture")

    cruiser_grid_path = args.shots / "shot_cruiser_grid.png"
    if not cruiser_grid_path.is_file():
        failures.append("missing Cruiser Three Views 3x2 grid screenshot")
    else:
        grid_detail = sum(ImageStat.Stat(Image.open(cruiser_grid_path).convert("RGB").crop(
            (400, 190, 880, 520))).stddev) / 3.0
        print("Cruiser Three Views 3x2 grid detail %.2f" % grid_detail)
        if grid_detail < 15.0:
            failures.append("Cruiser Three Views 3x2 grid did not render")

    for name, label, box in (
            ("bridge_chairs", "bridge chairs", (180, 120, 1100, 680)),
            ("material_grid", "material grid", (250, 90, 1030, 650))):
        path = args.shots / ("shot_%s.png" % name)
        if not path.is_file():
            failures.append("missing %s screenshot" % label)
            continue
        crop = Image.open(path).convert("RGB").crop(box)
        detail = sum(ImageStat.Stat(crop).stddev) / 3.0
        magenta = sum(1 for r, g, b in pixels(crop)
                      if r > 180 and b > 140 and g < 80)
        print("%s render detail %.2f" % (label, detail))
        if detail < 12.0:
            failures.append("%s did not render with visible detail" % label)
        if magenta > 4:
            failures.append("%s contains missing-texture pixels" % label)

    if failures:
        for failure in failures:
            print("FAIL: " + failure)
        raise SystemExit(1)
    print("PASS: Programmable Viewscreen non-screen faces render as housing")
    print("PASS: Programmable Console screen, keyboard, and housing all render")
    print("PASS: all Programmable Viewscreen animation slots render content")
    print("PASS: all Programmable Console input-panel options render")
    print("PASS: Programmable Console GUI shows both scrollable lists")
    print("PASS: Programmable Diagonal Screen renders both stair-style halves")
    print("PASS: paired programmable display halves render together")
    print("PASS: Programmable Half-Input renders Small wall and attached keyboard placements")
    print("PASS: programmable input Off/Static/Animated modes render distinctly")
    print("PASS: Programmable Half-Console renders two selectable input surfaces")
    print("PASS: Programmable Input renders full wall/floor surfaces and regular selector")
    print("PASS: Cruiser Three Views renders as its authored 3x2 grid")
    print("PASS: programmable/control blocks have representative hotbar icons")
    print("PASS: simple bridge chairs and active material, hull, and floor blocks render")


if __name__ == "__main__":
    main()
