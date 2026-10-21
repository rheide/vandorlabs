# Vandor Labs project guidance

- Right-clicking a dialog option must select its previous value. Keep left-click and right-click cycling consistent across dialogs.
- Follow the existing screen, texture-list and door dialogs for layout and interaction conventions. Texture lists must support both wheel scrolling and dragging their scrollbar.
- Match the mod's established texture resolutions; do not ship oversized textures. Turntable surface artwork may use a larger resolution because it can span multiple blocks.
- When adding, removing or recategorizing selectable textures, regenerate the docs browser and category collages with `tools/generate_texture_browser.py`, then run it with `--check`. Keep `docs/texture-browser/` and `docs/texture-overview.md` synchronized with the in-game catalogs.

- Keep finished build artifacts in `build/libs/`. Copy a build elsewhere only when the user explicitly requests a destination.
- Use Java 8 for Gradle 4.9 builds. Run the live client suite for client-visible or gameplay changes.
- Build only the standard mod JAR. Never produce original-textures JARs; the original texture directory is an archive and validation reference only.
- Keep documentation technical and user-facing. Do not commit task logs, chat transcripts, authoring instructions, personal paths or machine-specific details.
- Item icons must be slightly smaller than their inventory/hotbar slots, with visible padding on every side. Account for the GUI rotation and depth, and check every generated variant; fitting the unrotated model into 16×16 coordinates does not guarantee that its icon fits.
