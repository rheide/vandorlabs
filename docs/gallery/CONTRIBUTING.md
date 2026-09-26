# Maintaining the screenshot gallery

The gallery is generated from a real Forge client and should never be updated
by manually staging or cropping screenshots.

To regenerate everything:

```bash
testclient/generate_gallery.sh
```

The command builds the current jar, starts a fresh deterministic superflat
world under software rendering, disables tutorial prompts and mob spawning,
runs the live runtime contracts, captures the curated scenes, replaces
`docs/images/gallery`, and fails if any expected image is missing.

When adding or changing a showcased block:

1. Add or update its grounded `gallery_*` shot and scene in
   `src/main/java/com/vandorlabs/client/ReproLab.java`.
2. Add its stable output path to `SHOTS` in `testclient/export_gallery.py`.
3. Reference that stable path from the appropriate page in `docs/gallery`.
4. Run `testclient/generate_gallery.sh` and visually inspect every changed PNG.

Frame individual blocks tightly enough to show their geometry and texture.
Show both states when appearance changes with activation. Use matching camera
angles for before/after comparisons and include the relevant GUI when its
controls are described. The full gallery takes several minutes because each
image is a separate live-client scene.

Keep documentation scenes in the isolated gallery area, use a non-ship screen
such as `engineering_screen` for programmable blocks, and allow enough settle
ticks for chunks, animated models, and block-change particles to stabilize.
