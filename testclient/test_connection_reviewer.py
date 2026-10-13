"""Offline checks for connection reviewer sampling and approval boundaries."""
import contextlib
import io
import itertools
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import review_connections as reviewer


class ConnectionReviewerChecks(unittest.TestCase):
    def test_diagonal_preview_projection_and_clamping(self):
        row = reviewer.case(reviewer.diagonal_scene_names("diagonal-half-width-full-height")[0])
        style = reviewer.diagonal_preview(dict(row, roles=[row["roles"][0]]),
                                          {"camX": .5, "camY": 0, "camZ": -10, "posX": 0, "posY": 1.12, "posZ": 0, "yaw": 0, "pitch": 0})
        self.assertEqual(style["transform"], "translate(-50.000%, -50.000%)")
        for scope in reviewer.DIAGONAL_SCOPES:
            for name in reviewer.diagonal_scene_names(scope):
                row = reviewer.case(name)
                style = reviewer.diagonal_preview(row)
                zoom = float(style["width"].rstrip("%"))
                self.assertGreaterEqual(zoom, 100)
                self.assertLessEqual(zoom, 250 if row["topology"] == "l" else 175)
                self.assertTrue(all(0 <= float(v) <= 100 for v in re.findall(r"-([\d.]+)%", style["transform"])))
        style = reviewer.diagonal_preview(row, {"camX": -10000, "camZ": 10000})
        self.assertTrue(all(0 <= float(v) <= 100 for v in re.findall(r"-([\d.]+)%", style["transform"])))

    def test_diagonal_selected_poses_materials_and_page(self):
        node = os.environ.get("NODE") or shutil.which("node")
        scratch = Path(__file__).resolve().parents[1] / "build" / "reviewer-tests"
        scratch.mkdir(parents=True, exist_ok=True)
        for scope, config in reviewer.DIAGONAL_SCOPES.items():
            names = reviewer.diagonal_scene_names(scope)
            self.assertEqual(names, reviewer.diagonal_scene_names(scope))
            self.assertEqual(len(set(names)), 60)
            rows = [reviewer.case(name) for name in names]
            for topology, count in (("l", 18), ("t", 24), ("cross", 18)):
                group = [r for r in rows if r["topology"] == topology]
                self.assertEqual(len(group), count)
                self.assertEqual({r["facing"] for r in group}, set(reviewer.FACINGS))
                self.assertEqual({r["upper_lower"] for r in group}, {"upper", "lower"})
                # Material coverage is pooled across poses in relative role space.
                relative = []
                for row in group:
                    facing = reviewer.FACINGS.index(row["facing"])
                    positions = {reviewer.OFFSETS[facing]: "F", reviewer.OFFSETS[(facing+2)%4]: "B",
                                 reviewer.OFFSETS[(facing-1)%4]: "L", reviewer.OFFSETS[(facing+1)%4]: "R", (0, 0): "C"}
                    relative.append({positions[tuple(r["position"])]: r["material"] for r in row["roles"]})
                if topology == "l":
                    self.assertEqual(len({tuple(r["materials"]) for r in group}), 9)
                    self.assertEqual({name.split("_")[-1] for name in names[:18]}, {"0", "1"})
                    for i in range(0, 18, 2):
                        self.assertEqual(rows[i]["placement_order"], list(reversed(rows[i+1]["placement_order"])))
                else:
                    for a, b in itertools.combinations("CFBLR", 2):
                        self.assertEqual(len({(r[a], r[b]) for r in relative if a in r and b in r}), 9)
            self.assertTrue(all(r["family"] == "diagonal" and r["geometry"] == str(config["mode"]) and r["geometry_label"] == reviewer.GEOMETRIES[config["mode"]] for r in rows))
            crosses = [r for r in rows if r["topology"] == "cross"]
            for hand in range(4):
                self.assertEqual({r["facing"] for r in crosses[:16] if int(r["name"].split("_")[-3]) == hand}, set(reviewer.FACINGS))
            for hand in range(4):
                self.assertGreaterEqual(len({r["facing"] for r in rows[:18] if int(r["name"].split("_")[-3]) == hand}), 2)
            with tempfile.TemporaryDirectory(dir=scratch) as directory:
                folder = Path(directory)
                for row in rows:
                    (folder / row["file"]).write_bytes(b"fixture")
                argv = ["review_connections.py", str(folder), "--scope", scope, "--no-contact-sheet"]
                with patch.object(sys, "argv", argv), contextlib.redirect_stdout(io.StringIO()):
                    reviewer.main()
                catalog = json.loads((folder / "connection-review.json").read_text())
                self.assertEqual(catalog["expected_pages"], {"horizontal": 60})
                self.assertEqual(len(catalog["cases"]), 60)
                self.assertFalse((folder / "full.html").exists())
                self.assertIn("not exhaustive at every pose", (folder / "index.html").read_text())
                if node:
                    subprocess.run([node, str(Path(__file__).with_suffix(".cjs")), str(folder / "index.html"), "60"], check=True)
                (folder / rows[0]["file"]).unlink()
                with patch.object(sys, "argv", argv), contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
                    reviewer.main()
                self.assertEqual(error.exception.code, 2)
        with self.assertRaises(ValueError):
            reviewer.case("gallery_acceptance_horizontal_0_1_0_0_0_0_0_0")

    def test_sampling_material_coverage(self):
        for scope, count in (("regular-t", 58), ("regular-x", 29)):
            selected = reviewer.compact_scene_names(scope)
            self.assertEqual(selected, reviewer.compact_scene_names(scope))
            self.assertEqual(len(selected), count)
            self.assertLessEqual(selected, set(reviewer.regular_scene_names(scope)))
            rows = [reviewer.case(name) for name in selected]
            for variant in reviewer.REGULAR_SCOPES[scope]["variants"]:
                group = [r for r in rows if int(r["name"].split("_")[-3]) == variant]
                for a, b in itertools.combinations(range(len(group[0]["roles"])), 2):
                    self.assertEqual(len({(r["materials"][a], r["materials"][b]) for r in group}), 9)
            triples = [(0, 1, 2)] if scope == "regular-t" else [(0, 1, 2), (0, 3, 4)]
            group = [r for r in rows if scope == "regular-x" or "_1_4_" in r["name"]]
            for positions in triples:
                self.assertEqual(len({tuple(r["materials"][i] for i in positions) for r in group}), 27)
            for material in reviewer.MATERIALS:
                self.assertTrue(any(all(m == material for m in r["materials"]) for r in rows))

    def generate(self, folder, scope, feedback=None):
        argv = ["review_connections.py", str(folder), "--scope", scope, "--compact", "--no-contact-sheet"]
        if feedback:
            argv.extend(["--feedback-order", str(feedback)])
        with patch.object(sys, "argv", argv), contextlib.redirect_stdout(io.StringIO()):
            reviewer.main()

    def test_full_catalog_missing_png_and_scoped_feedback(self):
        node = os.environ.get("NODE") or shutil.which("node")
        scratch = Path(__file__).resolve().parents[1] / "build" / "reviewer-tests"
        scratch.mkdir(parents=True, exist_ok=True)
        for scope, count in (("regular-t", 58), ("regular-x", 29)):
            with tempfile.TemporaryDirectory(dir=scratch) as directory:
                folder = Path(directory)
                names = reviewer.regular_scene_names(scope)
                for name in names:
                    (folder / ("shot_" + name + ".png")).write_bytes(b"fixture")
                absent = folder / ("shot_" + names[-1] + ".png")
                absent.unlink()
                with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
                    self.generate(folder, scope)
                self.assertEqual(error.exception.code, 2)
                self.assertFalse((folder / "index.html").exists())
                absent.write_bytes(b"fixture")
                self.generate(folder, scope)
                compact = json.loads((folder / "connection-review.json").read_text())
                full = json.loads((folder / "connection-review.full.json").read_text())
                self.assertEqual(len(compact["cases"]), count)
                self.assertEqual(len(full["cases"]), len(names))
                self.assertEqual({r["name"] for r in full["cases"]}, set(names))
                if scope == "regular-t":
                    self.assertEqual([r["name"].split("_")[-2] for r in compact["cases"][:4]], ["4", "5", "6", "7"])
                self.assertIn("Hidden cases are not reviewed.", (folder / "index.html").read_text())
                if node:
                    subprocess.run([node, str(Path(__file__).with_suffix(".cjs")), str(folder / "index.html"), str(count)], check=True)
                wrong = compact["cases"][-1]["file"]
                hidden = next(r["file"] for r in full["cases"] if r["file"] not in {r["file"] for r in compact["cases"]})
                feedback = folder / "feedback.json"
                feedback.write_text(json.dumps({"schema": "vandorlabs-connection-review-v1", "displayedFiles": [r["file"] for r in compact["cases"]], "reviews": {wrong: {"status": "wrong"}, hidden: {"status": "wrong"}}}))
                self.generate(folder, scope, feedback)
                ordered = json.loads((folder / "connection-review.json").read_text())["cases"]
                self.assertEqual(ordered[0]["file"], wrong)
                self.assertEqual({r["file"] for r in ordered}, {r["file"] for r in compact["cases"]})
                self.assertEqual(len(ordered), count)
                if node:
                    subprocess.run([node, str(Path(__file__).with_suffix(".cjs")), str(folder / "index.html"), str(count)], check=True)


if __name__ == "__main__":
    unittest.main()
