#!/usr/bin/env python3
"""Compare independently executed Forge/NeoForge shape adapters (not generated policy meshes)."""
import argparse
import copy
import json
import math
from pathlib import Path

POSITION_EPSILON = 1e-6
UV_EPSILON = 4e-4  # Atlas float precision; content-normalized coordinates, not atlas locations.


def occupied(boxes):
    """Exact union on the authored 1/16 lattice, independent of AABB decomposition."""
    cells = 0
    for box in boxes:
        cuts = [round(value * 16) for value in box]
        if any(not math.isfinite(value) or abs(value * 16 - cut) > 1e-6
               for value, cut in zip(box, cuts)):
            raise AssertionError(f"Collision leaves authored 1/16 lattice: {box}")
        x0, y0, z0, x1, y1, z1 = cuts
        if not (0 <= x0 < x1 <= 16 and 0 <= y0 < y1 <= 16 and 0 <= z0 < z1 <= 16):
            raise AssertionError(f"Invalid unit-cell collision: {box}")
        line = (1 << (z1 - z0)) - 1
        for x in range(x0, x1):
            for y in range(y0, y1):
                cells |= line << ((x * 16 + y) * 16 + z0)
    return cells


def canonical_quad(quad):
    if not quad.get("material"):
        raise AssertionError(f"Actual sprite has no catalog identity: {quad['sprite']}")
    vertices = quad["vertices"]
    if len(vertices) != 4 or any(len(v) != 5 or not all(map(math.isfinite, v)) for v in vertices):
        raise AssertionError("Nonfinite or malformed emitted quad")
    # Cyclic changes are harmless, reversing winding is observable and remains distinct.
    candidates = [vertices[i:] + vertices[:i] for i in range(4)]
    vertices = min(candidates, key=lambda row: tuple(round(v, 6) for p in row for v in p[:3]))
    positions = tuple(round(value, 6) for vertex in vertices for value in vertex[:3])
    key = (quad["bucket"], quad["face"], quad["shade"], quad["material"], positions)
    return key, vertices


def compare_case(old, new):
    errors = []
    if (old["layout"], old["profile"], old["ao"]) != (new["layout"], new["profile"], new["ao"]):
        errors.append("settings/model ambient-occlusion mismatch")
    if not old["ao"] and new.get("rendererAo", "FALSE") != "FALSE":
        errors.append("actual renderer AO overload enables legacy-flat model")
    if occupied(old["boxes"]) != occupied(new["boxes"]):
        errors.append("actual collision union mismatch")
    left = sorted(map(canonical_quad, old["quads"]))
    right = sorted(map(canonical_quad, new["quads"]))
    if [q[0] for q in left] != [q[0] for q in right]:
        missing = set(q[0] for q in left) - set(q[0] for q in right)
        extra = set(q[0] for q in right) - set(q[0] for q in left)
        errors.append(f"quad geometry/cull/shade/material mismatch old={len(left)} new={len(right)} missing={list(missing)[:1]} extra={list(extra)[:1]}")
        return errors, 0
    maximum = 0
    for (_, old_vertices), (_, new_vertices) in zip(left, right):
        for old_vertex, new_vertex in zip(old_vertices, new_vertices):
            if max(abs(a - b) for a, b in zip(old_vertex[:3], new_vertex[:3])) > POSITION_EPSILON:
                errors.append("vertex position mismatch")
            maximum = max(maximum, *(abs(a - b) for a, b in zip(old_vertex[3:], new_vertex[3:])))
    if maximum > UV_EPSILON:
        errors.append(f"content UV mismatch max={maximum:.9f}")
    if any(quad.get("quadAo", old["ao"]) != old["ao"] for quad in new["quads"]):
        errors.append("per-quad ambient-occlusion differs from legacy renderer policy")
    return errors, maximum


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy", type=Path, required=True)
    parser.add_argument("--modern", type=Path, required=True)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    old_data = json.loads(args.legacy.read_text())
    new_data = json.loads(args.modern.read_text())
    assert old_data["format"] == new_data["format"] == 1
    assert old_data["rectangularChoice"] == new_data["rectangularChoice"]
    old = {row["key"]: row for row in old_data["cases"]}
    new = {row["key"]: row for row in new_data["cases"]}
    assert len(old) == len(new) == 708, "Expected actual600stairs +108slabs"
    assert old.keys() == new.keys(), "Export case coverage differs"
    failures, maximum, quads = [], 0, 0
    for key in old:
        errors, delta = compare_case(old[key], new[key])
        maximum = max(maximum, delta)
        quads += len(old[key]["quads"])
        if errors:
            failures.append({"key": key, "errors": errors})
    # Mutate actual emitted data: these must fail even after adapter outputs otherwise match.
    baseline = next(iter(new.values()))
    uv_negative = copy.deepcopy(baseline)
    uv_negative["quads"][0]["vertices"][0][3] += .125
    assert compare_case(baseline, uv_negative)[0], "UV negative control did not fail"
    collision_negative = copy.deepcopy(baseline)
    collision_negative["boxes"] = []
    assert compare_case(baseline, collision_negative)[0], "Collision negative control did not fail"
    cull_negative = copy.deepcopy(baseline)
    cull_negative["quads"][0]["bucket"] = 5 if baseline["quads"][0]["bucket"] != 5 else 0
    assert compare_case(baseline, cull_negative)[0], "Cull negative control did not fail"
    report = {"cases": len(old), "quads": quads, "max_content_uv_error": maximum,
              "position_tolerance": POSITION_EPSILON, "content_uv_tolerance": UV_EPSILON,
              "negative_controls": ["uv", "collision", "cull"], "failures": failures}
    if args.report:
        args.report.write_text(json.dumps(report, indent=2) + "\n")
    if failures:
        for failure in failures[:12]:
            print(f"FAIL {failure['key']}: {'; '.join(failure['errors'])}")
        raise SystemExit(f"FAIL {len(failures)}/{len(old)} shape adapter cases")
    print(f"PASS actual600stairs+108slabs, {quads} emitted quads, exact collision unions, cull/shade/AO/material/winding; max content UV {maximum:.9f}; UV/collision/cull negatives rejected")


if __name__ == "__main__":
    main()
