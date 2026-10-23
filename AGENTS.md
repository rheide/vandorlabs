# Vandor Labs project guidance

- Shared gameplay code must not call `@SideOnly(Side.CLIENT)` APIs. In Minecraft 1.12.2, `AxisAlignedBB(Vec3d, Vec3d)` and `AxisAlignedBB.getCenter()` are client-only; use six coordinates and explicit midpoints. Run `testDedicatedServerLinkage`; integrated-server tests cannot establish dedicated-server API availability.
- Keep tile `onLoad()` local-only. Queue channel registration, neighbor reads, gameplay updates and reservation repair through `DeferredTileLoad`, which rechecks tile identity after chunk loading finishes.
- Failed vehicle or turntable deserialization must leave the entity inert and retain its original snapshot NBT for the next save. Do not keep a partially installed live snapshot after an installation failure.
- Network optimizations need before/after byte or message counts and round-trip checks. Keep world access on the server thread, preserve authorization/container/loaded-chunk checks, and bound decoded lengths before allocating.
- Keep release regressions separate from documentation capture. Prefer exhaustive non-rendering matrices plus targeted rendered regressions over unasserted gallery screenshots. Parallel capture must use isolated worlds, retain stateful scene groups, and verify merged scene coverage and identical artifact hashes before running image analyzers.

- Right-clicking a dialog option must select its previous value. Keep left-click and right-click cycling consistent across dialogs.
- Follow the existing screen, texture-list and door dialogs for layout and interaction conventions. Texture lists must support both wheel scrolling and dragging their scrollbar.
- Match the mod's established texture resolutions; do not ship oversized textures. Turntable surface artwork may use a larger resolution because it can span multiple blocks.
- When adding, removing or recategorizing selectable textures, regenerate the docs browser and category collages with `tools/generate_texture_browser.py`, then run it with `--check`. Keep `docs/texture-browser/` and `docs/texture-overview.md` synchronized with the in-game catalogs.
- Host the texture browser through this repository's GitHub Pages site, using `master` and `/docs` as recorded in `tools/texture_browser_hosting.json`. Commit regenerated browser assets with texture changes so Pages publishes the same catalog; no separate hosting checkout or upload is needed.

- Keep finished build artifacts in `build/libs/`. Copy a build elsewhere only when the user explicitly requests a destination.
- Use Java 8 for Gradle 4.9 builds. Run the live client suite for client-visible or gameplay changes.
- Build only the standard mod JAR. Never produce original-textures JARs; the original texture directory is an archive and validation reference only.
- Keep documentation technical and user-facing. Do not commit task logs, chat transcripts, authoring instructions, personal paths or machine-specific details.
- Item icons must be slightly smaller than their inventory/hotbar slots, with visible padding on every side. Account for the GUI rotation and depth, and check every generated variant; fitting the unrotated model into 16×16 coordinates does not guarantee that its icon fits.

- Before tagging, publishing or handing off a release JAR, run `verifyRelease` with Java 8. It must pass the diagonal geometry checks, the live glass/contact frame regressions and the live gameplay/fluid suite against that exact JAR. A successful assemble alone is not release validation.
