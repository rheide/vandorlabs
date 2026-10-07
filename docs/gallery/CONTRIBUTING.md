# Maintaining the screenshot gallery

The gallery is generated from a real Forge client and should never be updated
by manually staging or cropping screenshots.

For a small trapdoor change, capture and validate just the affected scenes:

```bash
testclient/generate_gallery.sh --focus trapdoors
```

`--focus armor` validates all four programmable armor pieces, their default Civilian Staff
designs, texture selection and inventory icons, and captures all eight full role
sets on armor stands. Its captures export to `docs/images/gallery/armor`.

`--focus storage` captures the storage sets, inventory, material picker and hotbar icons and
runs the storage inventory and rendering contracts.

`--focus` also accepts a mapped scene prefix. It runs the focused runtime checks
and verifies the requested captures; it does not run the full-gallery pixel
analyzers. Inspect the changed images before publishing them.

A full regression and gallery refresh is explicit:

```bash
testclient/generate_gallery.sh --full
```

Both commands build the current standard jar and start a real Forge client in a
fresh deterministic world under software rendering. The exporter merges captures
into `docs/images/gallery`, preserving unrelated images and byte-identical files.
It never deletes the gallery directory.

To reuse a successful live check without starting a second client:

```bash
testclient/test_viewscreen.sh --focus trapdoors
python3 testclient/export_gallery.py testclient/render-run.<id> --prefix gallery_trapdoor_followup_
```

The exporter also accepts `--only shot_name another_shot`. Without a selection it
exports whichever mapped captures exist in the supplied run. `--full` requires
all mapped captures before writing any images.

The live-suite run includes `artifact.sha256` and `source-commit.txt` to identify
the tested binary and source. Check that the artifact hash still matches before
reusing a capture. This uses the same exporter as `generate_gallery.sh`; do not
stage or crop screenshots by hand.

When adding or changing a showcased block:

1. Add or update its grounded `gallery_*` shot and scene in
   `src/main/java/com/vandorlabs/client/ReproLab.java`.
2. Add its stable output path to `SHOTS` in `testclient/export_gallery.py`.
3. Reference that stable path from the appropriate page in `docs/gallery`.
4. Run `testclient/generate_gallery.sh --focus <scene-prefix>` and visually inspect every changed PNG.

Frame individual blocks tightly enough to show their geometry and texture.
Show both states when appearance changes with activation. Use matching camera
angles for before/after comparisons and include the relevant GUI when its
controls are described. The full gallery takes several minutes because each
image is a separate live-client scene.

Keep documentation scenes in the isolated gallery area, use a non-ship screen
such as `engineering_screen` for programmable blocks, and allow enough settle
ticks for chunks, animated models, and block-change particles to stabilize.
