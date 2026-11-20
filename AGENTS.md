# Vandor Labs project guidance

Minecraft 1.12.2 Forge mod (Java 8, Gradle 4.9) with a NeoForge 1.21.1 port in
`platforms/neoforge-1.21.1` (Java 21). Shared, loader-free logic lives in `src/portable`
with plain-Java checks in `src/portableTest`.

The mod must build and run on both Windows and Linux. Keep code, Gradle tasks and file
handling cross-platform. Windows live clients run through `testclient/run_windows.py`;
`tools/focus.sh` is a Linux convenience wrapper and does not replace Windows coverage.

Also read before changing these areas:
- `agent-notes/feature-regressions.md`: vehicles and VehicleWorld, ramps, cockpits, arms and
  Extenders, Copy Controllers and Turntables, rendering, shaders and animation, networking,
  JEI and the creative catalog, and optional mod adapters.
- `agent-notes/release.md`: version releases, broad cross-system updates and running-server
  updates.

## Tools

- `tools/focus.sh <focus> [--shots] [--no-build]` runs one live client focus. It runs
  preflight checks, rebuilds the JAR only when sources are newer and prints a short PASS/FAIL
  summary with the first failure cause. The full output goes to `build/focus-logs/`. Focus
  names are the `--focus` targets in `testclient/test_viewscreen.sh`. Put machine-specific
  settings such as `VANDOR_LABS_COMPAT_MODS` in the untracked `tools/focus.local.env`.
- `--shots` copies the run's screenshots to `build/focus-shots/<focus>/` and tiles them into
  `contact-sheet.png` with `tools/contact_sheet.py`. Review the sheet first, then crop one
  original at full resolution where needed; don't open every screenshot.
- In 1.12 live fixtures, use `LiveGallery` (`src/main/java/com/vandorlabs/client`) for named
  camera shots and for placing blocks through the real item path. Never block the client
  thread on an integrated-server task with `.get()`: a paused server runs no tasks and the
  client times out.
- Run long checks in the background and wait for completion; don't poll logs repeatedly.
  Read logs with bounded searches for PASS markers and the first stack trace.

## Platform rules

- Use Java 8 on both Windows and Linux. Keep journal file sync and atomic
  publication on both platforms, but route directory sync through `JournalFiles`, because
  Windows NIO cannot open directory channels. Do not swallow genuine Linux permission/I/O
  failures. Exercise real asynchronous journal preparation, completion and recovery in live
  tests; matrix fixtures that bypass journaling cannot establish this behavior.
- Shared gameplay code must not call `@SideOnly(Side.CLIENT)` APIs. In 1.12.2,
  `AxisAlignedBB(Vec3d, Vec3d)` and `AxisAlignedBB.getCenter()` are client-only; use six
  coordinates and explicit midpoints. Run `testDedicatedServerLinkage` when shared code changes;
  integrated-server tests cannot establish dedicated-server API availability.
- Keep tile `onLoad()` local-only. Queue channel registration, neighbor reads, gameplay updates
  and reservation repair through `DeferredTileLoad`, which rechecks tile identity after chunk
  loading finishes.

## Building and validation

- Use Java 8 for Gradle 4.9 builds. Build only the standard mod JAR and keep it in
  `build/libs/`. Never produce original-textures JARs; the original texture directory is an
  archive and validation reference only. Copy a build elsewhere only when the user asks.
- Choose validation from the changed behavior before starting a run, and record the selected
  checks and the reason in the handoff. For example, a vehicle mode or saved-vehicle change
  needs previous-version NBT fixtures, Taxi/park/recovery live checks and dedicated-server
  linkage; it does not need Dynmap or glass/contact-frame checks. A texture catalog, atlas or
  renderer change needs its picker/render focus, plus Dynmap when the mapping affects Dynmap.
  Glass/contact frames belong to glass, geometry, material-pass or depth changes. Run adjacent
  historical regressions when the changed code reaches them.
- During feature work, run the smallest relevant live focus with the actual optional mods
  before stacking further changes. Client-visible or gameplay changes need a live client run.
- Run at most two live Minecraft checks concurrently, each with isolated game, world and output
  directories. Run timing benchmarks on their own.
- In live client fixtures, synchronize the integrated server and the client before sending a
  real interaction packet: game mode, selected hotbar slot, held stack and position. Include
  the fixture, stage, mode and inventory values in timeout failures.
- Generated test runtimes use the shared artifact-retention helper (`docs/test-artifact-retention.md`).
  Set `VANDOR_LABS_TEST_KEEP_ARTIFACTS=1` only when a full runtime must remain for debugging.
- Commit coherent steps as you go. Remove inactive task worktrees after their changes are
  integrated, keeping their branch or commit and compact verification evidence.

## UI, textures and items

- Configurizer interaction and ordinary right-click must open the same dedicated dialog, with
  the same controls, authorization and behavior, through one shared path. Never substitute the
  generic redstone dialog.
- Left-click cycles a dialog option forward and right-click selects its previous value,
  consistently across dialogs. Before adding or changing a dialog, reuse the layout, widgets and
  interaction style of existing dialogs with the same kind of controls (for example the
  Turntable Controller height slider, and the screen, texture-list and door dialogs). Texture
  lists support both wheel scrolling and dragging the scrollbar.
- Match the mod's established texture resolutions. Turntable surface artwork may be larger,
  because it can span multiple blocks.
- When adding, removing or recategorizing selectable textures, regenerate the texture browser
  and collages with `tools/generate_texture_browser.py` and check them with `--check`. Commit
  `docs/texture-browser/` and `docs/texture-overview.md` with the change; GitHub Pages serves
  them from `master` `/docs` (`tools/texture_browser_hosting.json`). A texture category can also
  drive runtime artwork, including turntable low-resolution sprites and Dynmap lookup, so
  search every consumer of a changed category string and run `testDynmapRenderers` and the
  focused live picker.
- Item icons must be slightly smaller than their slots, with visible padding on every side,
  accounting for the GUI rotation and depth. Check every generated variant.

## Documentation

- Every new multiblock needs step-by-step building instructions under `docs/`, with
  construction-stage images like the existing guides. Update `CHANGELOG.md` and the current
  version promotion page alongside the feature.
- Keep documentation technical and user-facing. Do not commit task logs, chat transcripts,
  authoring instructions, personal paths or machine-specific details.
