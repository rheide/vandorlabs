"""Offline checks for connection reviewer sampling and approval boundaries."""
import contextlib
import io
import itertools
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import review_connections as reviewer


class ConnectionReviewerChecks(unittest.TestCase):
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
