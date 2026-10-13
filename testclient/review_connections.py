#!/usr/bin/env python3
"""Build an offline review index for an opt-in connection-acceptance capture.

Usage: python testclient/review_connections.py build/<capture-directory>
The source screenshots and manifest.tsv are preserved. All outputs stay in the
capture directory. Pillow is optional and only used for a representative sheet.
"""
import argparse
import csv
import html
import json
from pathlib import Path
import re

GEOMETRIES = ["Tall / half width", "Full width / height", "Wide / half height"]
TOPOLOGIES = ["L corner · C then N", "L corner · N then C", "T junction", "Four-way junction"]
EXPECTED = {"junction": 12, "horizontal": 24192}
MATERIALS = ["wall", "glass", "porthole"]
FACINGS = ["north", "east", "south", "west"]
OFFSETS = [(0, -1), (1, 0), (0, 1), (-1, 0)]
WORLD_ROLES = [("C", (0, 0), "center"), ("N", (0, -1), "north"),
               ("S", (0, 1), "south"), ("W", (-1, 0), "west"), ("E", (1, 0), "east")]
PILOT_COVERAGE = (
    "Pilot: 12 horizontal junction screenshots, one junction per image. "
    "Each of the three diagonal wall geometries appears in an L corner placed "
    "C then N, the same L corner placed N then C, a T junction and a four-way junction. "
    "All members use wall material and the same geometry within each scene. "
    "This pilot checks the scene layout and these uniform-geometry cases; it does "
    "not cover mixed materials, mixed geometry assignments, arbitrary orientations "
    "or inversion permutations. It contains no vertical or straight-only fixtures."
)
HORIZONTAL_COVERAGE = (
    "Horizontal matrix: 24,192 individual junction scenes. Flat and diagonal families "
    "are tested separately with every wall/glass/porthole assignment at each active "
    "block position. Four root directions cover front/back and left/right bends. "
    "L corners have four branch variants and both placement orders; T junctions "
    "have twelve branch/tail arrangements; four-way junctions have four independent "
    "front/back hand combinations. Diagonals include uniform lower and uniform upper "
    "placement; mixed upper/lower assignments within a junction are not included. "
    "Geometry and flat depth are fixed at the canonical setting, with Auto joins and "
    "no fill. There are no vertical or straight-only fixtures, size sweeps, manual "
    "join overrides or fill sweeps. Pilot scenes, if present, are a separate earlier set."
)
REGULAR_SCOPES = {
    "regular-l": {"kind": 0, "variants": [0, 1, 2, 3], "assignments": 9, "orders": 2,
                  "count": 72, "label": "L corners", "title": "Regular wall L connection review",
                  "coverage": "72 regular L corners: four front/back and left/right bend arrangements × nine ordered wall/glass/porthole assignments × two placement orders."},
    "regular-t": {"kind": 1, "variants": [0, 2, 4, 5], "assignments": 81, "orders": 1,
                  "count": 324, "label": "T junctions", "title": "Regular wall T connection review",
                  "coverage": "324 regular T junctions: four distinct branch/tail arrangements × all 81 wall/glass/porthole assignments across four block positions. Arrangements use a north or south branch with both side tails, or north and south branches with one west or east tail."},
    "regular-x": {"kind": 2, "variants": [0], "assignments": 243, "orders": 1,
                  "count": 243, "label": "four-way junctions", "title": "Regular wall four-way connection review",
                  "coverage": "243 regular four-way junctions: one canonical cross arrangement × every wall/glass/porthole assignment across center, north, south, west and east positions."},
}


def regular_scene_names(scope):
    config = REGULAR_SCOPES[scope]
    return [f"gallery_acceptance_horizontal_0_0_0_0_{config['kind']}_{variant}_{assignment}_{order}"
            for variant in config["variants"] for assignment in range(config["assignments"]) for order in range(config["orders"])]


def compact_scene_names(scope):
    """Deterministic material sampling; never a claim of geometric equivalence."""
    config = REGULAR_SCOPES[scope]
    if scope == "regular-l":
        return set(regular_scene_names(scope))
    assignments = set()
    def add(variant, digits):
        assignment = sum(value * 3 ** index for index, value in enumerate(digits))
        assignments.add(f"gallery_acceptance_horizontal_0_0_0_0_{config['kind']}_{variant}_{assignment}_0")
    if scope == "regular-t":
        for variant in (0, 2, 5):
            for a in range(3):
                for b in range(3):
                    add(variant, (a, b, (a + b) % 3, (a + 2*b) % 3))
        for c in range(3):
            for n in range(3):
                for south in range(3):
                    add(4, (c, n, south, (c+n+south) % 3))
        for assignment in (4, 5, 6, 7, 40, 80):
            assignments.add(f"gallery_acceptance_horizontal_0_0_0_0_1_4_{assignment}_0")
    else:
        for c in range(3):
            for n in range(3):
                for south in range(3):
                    add(0, (c, n, south, (c+n) % 3, (c+south) % 3))
        add(0, (1, 1, 1, 1, 1))
        add(0, (2, 2, 2, 2, 2))
    return assignments


def feedback_order(rows, feedback):
    """Prioritize previously wrong files without adding cases or importing marks."""
    review = json.loads(feedback.read_text(encoding="utf-8"))
    if review.get("schema") != "vandorlabs-connection-review-v1":
        raise ValueError("Unknown feedback schema for ordering")
    displayed = review.get("displayedFiles")
    if displayed is not None and (not isinstance(displayed, list) or not all(isinstance(file, str) for file in displayed)):
        raise ValueError("displayedFiles must list screenshot filenames")
    wrong = {file for file, entry in review.get("reviews", {}).items()
             if isinstance(entry, dict) and entry.get("status") == "wrong" and (displayed is None or file in displayed)}
    return sorted(rows, key=lambda row: row["file"] not in wrong)


def horizontal_case(name):
    match = re.fullmatch(r"gallery_acceptance_horizontal_([0-9]+(?:_[0-9]+){7})", name)
    if not match:
        return None
    family, mode, facing, inverted, kind, variant, assignment, order = map(int, match.group(1).split("_"))
    if family not in (0, 1) or mode != 0 or facing not in range(4) or inverted not in range(2) or family == 0 and inverted != 0:
        raise ValueError("Invalid horizontal family, orientation or canonical geometry: " + name)
    if kind not in range(3) or variant not in range((4, 12, 4)[kind]) or order not in range(2 if kind == 0 else 1):
        raise ValueError("Invalid horizontal topology, variant or order: " + name)
    left, right, back = (facing - 1) % 4, (facing + 1) % 4, (facing + 2) % 4
    active = {(0, 0): facing}
    if kind == 0 or kind == 1 and variant < 4:
        branch = facing if variant < 2 else back
        hand = right if variant % 2 else left
        active[OFFSETS[branch]] = hand
        variant_label = ("front" if variant < 2 else "back") + ("-right" if variant % 2 else "-left")
        if kind == 1:
            active[OFFSETS[left]] = facing
            active[OFFSETS[right]] = facing
            variant_label += " branch + both tails"
    else:
        hands = (variant - 4) // 2 if kind == 1 else variant
        active[OFFSETS[facing]] = right if hands & 1 else left
        active[OFFSETS[back]] = right if hands & 2 else left
        variant_label = "front " + ("right" if hands & 1 else "left") + " / back " + ("right" if hands & 2 else "left")
        if kind == 1:
            tail = right if (variant - 4) % 2 else left
            active[OFFSETS[tail]] = facing
            variant_label += " + " + ("right" if tail == right else "left") + " tail"
        else:
            active[OFFSETS[left]] = facing
            active[OFFSETS[right]] = facing
            variant_label += " + both tails"
    if assignment not in range(3 ** len(active)):
        raise ValueError("Invalid material assignment: " + name)
    roles, remaining = [], assignment
    for role_id, position, location in WORLD_ROLES:
        if position not in active:
            continue
        material = MATERIALS[remaining % 3]
        remaining //= 3
        roles.append({"id": role_id, "position": list(position), "location": location,
                      "facing": FACINGS[active[position]], "material": material})
    placement = [role["id"] for role in roles]
    if order:
        placement.reverse()
    family_label = "diagonal" if family else "flat"
    upper_lower = ("upper" if inverted else "lower") if family else "na"
    cells = [f"{role['id']}: {role['location']}, facing {role['facing']} · {role['material']}" for role in roles]
    title = ["L corner", "T junction", "Four-way junction"][kind] + " · " + family_label + " · facing " + FACINGS[facing]
    if family:
        title += " · " + upper_lower + " placement"
    title += " · " + variant_label
    return {"name": name, "kind": "horizontal", "family": family_label, "topology": ["l", "t", "cross"][kind],
            "geometry": "0", "geometry_label": "Canonical", "facing": FACINGS[facing], "upper_lower": upper_lower,
            "materials": [role["material"] for role in roles], "placement_order": placement,
            "roles": roles, "cells": cells, "title": title, "file": "shot_" + name + ".png"}


def case(name):
    if name.startswith("gallery_acceptance_horizontal_"):
        return horizontal_case(name)
    match = re.fullmatch(r"gallery_acceptance_junction_([0-2])_([0-3])", name)
    if not match:
        return None
    mode, kind = map(int, match.groups())
    topology = 0 if kind < 2 else kind - 1
    order = 1 if kind == 1 else 0
    row = horizontal_case(f"gallery_acceptance_horizontal_1_0_0_0_{topology}_0_0_{order}")
    row.update({"name": name, "kind": "junction", "family": "pilot", "geometry": str(mode),
                "geometry_label": GEOMETRIES[mode], "title": TOPOLOGIES[kind]+" · "+GEOMETRIES[mode],
                "file": "shot_"+name+".png"})
    row["cells"] = [f"{role['id']}: {role['location']}, facing {role['facing']} · wall · {GEOMETRIES[mode]}" for role in row["roles"]]
    return row


def representatives(rows):
    selected = []
    for kind in EXPECTED:
        group = [r for r in rows if r["kind"] == kind and r["exists"]]
        if group:
            count = min(12, len(group))
            selected.extend(group[round(i * (len(group) - 1) / max(1, count - 1))] for i in range(count))
    return selected


def contact_sheet(folder, rows):
    try:
        from PIL import Image, ImageDraw, ImageOps
    except ImportError:
        return None
    selected = representatives(rows)
    if not selected:
        return None
    width, height, columns = 480, 320, 4
    sheet = Image.new("RGB", (columns * width, ((len(selected) + columns - 1) // columns) * height), "#17212b")
    draw = ImageDraw.Draw(sheet)
    for index, row in enumerate(selected):
        x, y = (index % columns) * width, (index // columns) * height
        with Image.open(folder / row["file"]) as image:
            image = ImageOps.contain(image.convert("RGB"), (width - 16, height - 50))
            sheet.paste(image, (x + (width - image.width) // 2, y + 8))
        caption = row["name"].replace("gallery_acceptance_", "")
        draw.text((x + 8, y + height - 35), caption, fill="white")
        draw.text((x + 8, y + height - 19), "Representative only; use index for full coverage", fill="#a9bdce")
    name = "connection-contact-sheet.jpg"
    sheet.save(folder / name, quality=88)
    return name


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture_directory", type=Path)
    parser.add_argument("--no-contact-sheet", action="store_true")
    parser.add_argument("--compact", action="store_true", help="Show a representative regular T/X sample; preserve a separate full reviewer")
    parser.add_argument("--feedback-order", type=Path, help="Put previously wrong displayed cases first, without importing review marks or adding cases")
    parser.add_argument("--scope", choices=["full", *REGULAR_SCOPES], default="full")
    args = parser.parse_args()
    folder = args.capture_directory.resolve()
    if args.compact and args.scope not in ("regular-t", "regular-x"):
        parser.error("--compact requires --scope regular-t or regular-x")
    rows = []
    manifest = folder / "manifest.tsv"
    if manifest.exists():
        with manifest.open(encoding="utf-8-sig", newline="") as stream:
            names = [row["name"] for row in csv.DictReader(stream, delimiter="\t")]
    else:
        names = []
    # Resumed captures can retain earlier PNGs beside a manifest listing only the
    # resumed scenes. Include both sources so those screenshots remain reviewable.
    names.extend(path.stem.removeprefix("shot_") for path in folder.glob("shot_gallery_acceptance_*.png"))
    selected_names = set(regular_scene_names(args.scope)) if args.scope in REGULAR_SCOPES else None
    if selected_names is not None:
        names.extend(selected_names)
    for name in dict.fromkeys(names):
        row = case(name)
        if row and (selected_names is None or name in selected_names):
            row["exists"] = (folder / row["file"]).is_file()
            rows.append(row)
    rows.sort(key=lambda row: (list(EXPECTED).index(row["kind"]),
                              tuple(int(value) for value in row["name"].removeprefix("gallery_acceptance_").split("_")[1:])))
    if not rows:
        parser.error("No connection-acceptance cases found in manifest.tsv or screenshots")
    expected_pages = {kind: expected for kind, expected in EXPECTED.items() if any(row["kind"] == kind for row in rows)}
    config = REGULAR_SCOPES.get(args.scope)
    if config:
        expected_pages = {"horizontal": config["count"]}
    counts = {kind: sum(r["exists"] for r in rows if r["kind"] == kind) for kind in expected_pages}
    summary = "; ".join(f"{kind}: {counts[kind]}/{expected} pages" for kind, expected in expected_pages.items())
    coverage = HORIZONTAL_COVERAGE if "horizontal" in counts else PILOT_COVERAGE
    if config:
        coverage = config["coverage"] + " All scenes use regular panels, a north-facing root, centered depth, Auto joins and no fill. There are no diagonal or vertical fixtures. Click any wrong image; unmarked images are assumed correct."
    title = config["title"] if config else "Horizontal junction review"
    missing = [row["file"] for row in rows if not row["exists"]]
    if config and missing:
        parser.error(f"{args.scope} requires all {config['count']} screenshots; {len(missing)} are missing (first: {missing[0]})")
    full_rows = rows
    full_coverage = coverage
    if args.compact:
        chosen = compact_scene_names(args.scope)
        rows = [row for row in rows if row["name"] in chosen]
        if args.scope == "regular-t":
            flagged = {f"gallery_acceptance_horizontal_0_0_0_0_1_4_{a}_0": a for a in range(4, 8)}
            rows.sort(key=lambda row: (0, flagged[row["name"]]) if row["name"] in flagged else (1, full_rows.index(row)))
        sampling = ("Every ordered material pair at each pair of positions in each arrangement; all center/north/south triples on the two-branch arrangement; flagged assignments 4-7 and uniform-material baselines retained."
                    if args.scope == "regular-t" else
                    "Every ordered material pair at each pair of positions, all center/north/south triples and all center/west/east triples, plus uniform-material baselines.")
        coverage = f"Representative sample: {len(rows)} of {len(full_rows)} captured scenes. {sampling} This is material sampling, not a claim that omitted mixed assignments are equivalent. Only displayed cases are assumed correct when unmarked."
    if args.feedback_order:
        try:
            rows = feedback_order(rows, args.feedback_order)
        except (OSError, ValueError, TypeError) as error:
            parser.error(str(error))
    sheet = None if args.no_contact_sheet else contact_sheet(folder, rows)
    data = json.dumps(rows, ensure_ascii=True).replace("<", "\\u003c")
    sheet_link = f'<p><a href="{sheet}">Representative contact sheet</a> (pilot overview; use the reviewer for full-size screenshots).</p>' if sheet else ""
    template_name = "regular_l_review" if config else "connection_review"
    template = Path(__file__).with_name(template_name + ".html").read_text(encoding="utf-8")
    output = template.replace("__SUMMARY__", html.escape(summary)).replace("__COVERAGE__", html.escape(coverage))
    output = output.replace("__TITLE__", html.escape(title))
    output = output.replace("__TITLE_JSON__", json.dumps(title).replace("<", "\\u003c"))
    output = output.replace("__CASE_COUNT__", str(len(rows)))
    intro = (html.escape(coverage) + ' <a href="full.html">Inspect all captured cases</a>.' if args.compact else f"All {len(rows)} {html.escape(config['label'])} are on this page." if config else "")
    output = output.replace("__REVIEW_INTRO__", intro)
    output = output.replace("__REPORT_SCOPE__", json.dumps(f"Displayed sample only: {len(rows)} of {len(full_rows)} captured cases. Hidden cases are not reviewed." if args.compact else f"All {len(rows)} displayed cases."))
    output = output.replace("__TOPOLOGY_LABEL__", html.escape(config["label"] if config else "junctions"))
    output = output.replace("__MISSING__", str(len(missing))).replace("__SHEET__", sheet_link).replace("__DATA__", data)
    output = output.replace("__RUN_ID__", json.dumps(folder.name).replace("<", "\\u003c"))
    output = output.replace("__REVIEW_SCRIPT__", Path(__file__).with_name(template_name + ".js").read_text(encoding="utf-8"))
    if args.compact:
        full_data = json.dumps(full_rows, ensure_ascii=True).replace("<", "\\u003c")
        full_output = output.replace(data, full_data).replace(json.dumps("Displayed sample only: " + str(len(rows)) + " of " + str(len(full_rows)) + " captured cases. Hidden cases are not reviewed."), json.dumps(f"All {len(full_rows)} displayed cases."))
        full_output = full_output.replace(intro, f"All {len(full_rows)} captured {html.escape(config['label'])} are on this page. <a href='index.html'>Return to representative sample</a>.")
        (folder / "full.html").write_text(full_output, encoding="utf-8")
        (folder / "connection-review.full.json").write_text(json.dumps({"coverage": full_coverage, "expected_pages": expected_pages, "captured_pages": counts, "cases": full_rows}, indent=2), encoding="utf-8")
    (folder / "index.html").write_text(output, encoding="utf-8")
    (folder / "connection-review.json").write_text(json.dumps({"coverage": coverage, "expected_pages": expected_pages, "captured_pages": counts, "displayed_pages": len(rows), "cases": rows}, indent=2), encoding="utf-8")
    print(f"Wrote {folder / 'index.html'}; {summary}; missing screenshots: {len(missing)}")


if __name__ == "__main__":
    main()
