# Stationary Programmable Glass and Portholes

Stationary glass panes render as Minecraft's ordinary chunk geometry. Authored texture detail, including Gold glimmer, is enabled by default.
There is no world migration or required JVM argument.

Frame and pane ownership are independent. Opaque emissive frame faces retain
their native cutout layer and fullbright lighting. Selected straight frames with
blended emissive artwork use retained frame groups, with their own per-vertex
lightmap, instead of an individual frame submission for every block. The pane
keeps its ordinary lighting. Opaque custom inserts retain their existing frame
backlighting. Joined interior cells with no frame geometry do not retain a frame
renderer merely because the selected frame emits light.

This closes a performance gap in the previous implementation: moving panes into
terrain buffers still left real partial-alpha emissive frames in
`renderSelectedFrame`. All eight shipped emissive frame textures contain partial
alpha. Caching their meshes on the CPU did not remove their per-block uploads,
draws, state changes or tile-render admissions.

## Rendering behavior

Each pane contributes immutable block-local baked quads to the native
`TRANSLUCENT` layer. Existing frames keep their native layer assignments and
joining behavior. Joined interior cells retain their glimmer without extra frame
borders; openings remain empty. Per-cell UVs preserve the original artwork while
the section shares a normal terrain submission.

The default is one physical midpoint sheet with two opposite windings. Native
backface culling makes it visible from both sides while only one face contributes
from either side. Per-pixel alpha is compensated using
`effectiveAlpha = 1 - (1 - alpha)^2`; RGB, authored frame dimensions, animation
metadata and shader companion resources are preserved. This approximation does
not promise identical shader refraction or reflection to two physical sheets.

Minecraft supplies lighting, optional shader attributes and normal translucent
sorting. There is no separate pane draw, group anchor or private shader pointer
setup. Every settled pane-only tile is removed from its compiled section's
tile-render list; the tile entities continue to store gameplay and configuration
data.

Selected artwork, static-mode Screens and opaque panes use the mapped two-sheet
material plane. Native backface culling leaves one visible sheet, so translucent
artwork is written with the equivalent stacked opacity
`1 - (1 - opacity)^2` (35%, 65% and 85% become about 58%, 88% and 98%). Opaque
stock and artwork panes go to the `SOLID` layer: no sorting, blending or alpha
test. A lit frame still backlights opaque replacement artwork. Translucent
artwork pixels fainter than the native alpha test are dropped.

Stock palette textures are prepared with a visibility floor: after compensation,
every visible pixel has at least 27/255 alpha, so even the faintest tints (Soft
Cyan Mild and Normal, Soft Dark Grey Mild) are chunk geometry. Their appearance
is slightly more tinted than the authored texture.

Moving panes, corners, fluids and animated or off custom displays continue to use
their existing pane renderer; they do not disqualify an otherwise eligible
stationary frame from native or retained frame rendering.

### Programmable Portholes

Stationary portholes retain their housing and glass in separate GPU buffers.
Flat, diagonal, full-depth and joined corner/T-bar variants use the existing
geometry emitters and baked model transforms. Grouping never fills the aperture
with a rectangular pane or removes its frame. Materials, per-owner lighting,
custom UV layouts and distinct shader material identities are preserved.

Housing can remain retained when the glass is an animated custom display.
Static glass panes are grouped only when their placement and render state are
compatible. Bent or noncoplanar panes keep the ordering granularity required by
their shape. Group bounds include all emitted vertices, so a group remains
visible when its anchor block is outside the camera frustum.

Ordinary flat translucent palette portholes use the same compensated glimmer
texture as Programmable Glass. Their actual aperture polygons move to one
midpoint sheet with opposite windings and backface culling. This removes the
second sheet's fragment contribution without losing the aperture or glimmer.
Opaque panes, custom artwork and projected diagonal panes keep their original
sheet geometry. The existing single-surface option controls this choice; no new
startup flag is needed.

## Edits and lifetime

The client thread prepares each tile's immutable pane mesh and stores it on the
tile. The block's extended state carries it and the block model emits it, so a
pane is a pure function of the tile's settings and placement: no installed-record
inspection, withdrawal or upload fence is involved. When a mesh changes, the
section is marked for Minecraft's ordinary asynchronous rebuild, with the usual
chunk-rebuild latency. Meshes written for an older vertex format are never
emitted; a format or atlas change rebuilds every mesh and marks its section.

Joined artwork depends on its whole joined surface. Owners of mapped artwork are
rechecked when the connected-surface layout cache changes; an unchanged mapping
reuses its cached mesh and requests no rebuild.

Local mesh templates share two 8 MiB caches, and at most 1,024 dirty owners are
prepared per flush. Retained frame groups have a 32 MiB budget and porthole
groups a 16 MiB budget, covering CPU and GPU record copies and owner bookkeeping.
Groups are limited to 2 MiB, with at most eight sections rebuilt per manager
flush. Exceeding a total budget restores the existing renderer rather than
omitting geometry. Native frame publication keeps its own upload fence.

Changes are queued from tile callbacks and applied at safe render-thread flush
boundaries. Compiled tile-list restoration happens outside an active render
iterator. Resource/format changes, world switches, member removal, relighting
and joined porthole topology changes retire affected frame and porthole buffers.

## Supported cases

| Stationary combination | Surround | Glass or insert |
| --- | --- | --- |
| Stock, bare or native-compatible selected frame | Existing native geometry, or empty | Native terrain (`TRANSLUCENT`, or `SOLID` when opaque) |
| Selected blended emissive frame + any static glass or insert | Retained frame group | Native terrain |
| Selected blended emissive frame + animated insert or contained fluid | Retained frame group | Existing dynamic/fluid renderer |
| Porthole + static palette/custom/opaque glass | Retained housing group | Retained aperture group |
| Porthole + animated insert or contained fluid | Retained housing group | Retained static aperture where eligible; dynamic insert/fluid rendering remains independent |

Programmable Glass corners keep their existing native frame and shaped-pane
paths. Moving VehicleWorld geometry keeps the existing renderer. These are
explicit compatibility cases, not eligibility tests based on whether a frame
emits light.

Native transparency has Minecraft's usual sorting limitations. Overlapping
windows are not discarded using an approximate layer cap.

## Client options

Options are Java system properties read at startup. Restart after changes.

| Property | Default | Meaning |
| --- | --- | --- |
| `vandorlabs.staticGlassTexture` | `authored` | Native panes preserve glimmer; `smooth` explicitly selects a constant diffuse tint. |
| `vandorlabs.staticGlassSingleSurface` | `true` | Use one compensated sheet; `false` keeps two sheet positions. |
| `vandorlabs.glassPaneShadows` | `native` | Preserve pane shadows. `frames` omits shadow draws of panes that are not part of chunk geometry; chunk-baked panes and frames always cast shadows. |
| `vandorlabs.staticGlassDiagnostics` | `false` | Log native ownership, pruning, pending work and fallback counts. |

Stationary panes are always published into chunk geometry; retained groups are
only the automatic fallback for panes the chunk path cannot represent. The former
`staticGlass` and `staticGlassRenderer` options were removed. An earlier
`staticGlassTexture=authored` option can stay.

The benchmark's no-glass setting keeps its comparison behavior.
`benchmarkLegacyGlazing=true` affects only the fallback renderers and no longer
disables chunk-baked panes. Smooth and single-sheet settings apply to native-palette
panes; they do not change custom insert artwork or the per-tile compatibility
renderer.

## Diagnostics and verification

For a settled eligible area, expect `renderer=terrain`, `texture=authored`, native
pane owners (`opaque-owners` for solid panes) and pruned tiles, with pending owners
draining. A wall
using partial-alpha emissive frames should also show `retained-frame-owners` and
`retained-frame-groups`; `legacy-frame-main-draws` and
`legacy-frame-shadow-draws` should stop increasing for those stationary frames.
Portholes report separate owner, group, pruned, housing/pane draw, legacy draw
and pending counts. Build and upload counters should stop increasing when the
camera and world are unchanged.
Minecraft can still resort translucent chunk data as the camera moves.

These ownership counters are not GPU timings. Native panes share terrain draws;
measure whole-frame or translucent-layer timing with the same camera, shader
pack, resolution and world when comparing performance.

Run `/vandorglassprofile start 600` to capture 600 frames without changing the
camera, graphics settings or world. `/vandorglassprofile stop` ends a capture
early; `/vandorglassprofile status` shows progress and the saved path. Reports
are written under `vandorlabs-profiles` in the game directory. No startup flag
is required for this command.

The report records frame intervals, render-event CPU elapsed time, asynchronous
GPU timestamps where supported, native pane, retained frame and porthole coverage,
and the camera and graphics settings at each end of the capture. Separate scopes
name retained frames and porthole housing/panes in main and shadow rendering.
These timings cover the named submissions, not the entire shader shadow pass. Nested scopes
overlap and must not be summed. Query results are read only when available;
the profiler never calls `glFinish`. Profiling is disabled outside a capture.

For matched comparisons, use the same world, fixed camera, resolution, shader
pack and settings; wait for pending sections to drain first. Keep the previous
build as the baseline. A changed camera, active rebuilds or dropped samples must
be accounted for before interpreting a timing difference. Functional checks
and owner counts alone do not establish an FPS improvement.
The `glasstmp2` baseline already includes this command, so it can be used on both
builds. The new report adds frame and porthole coverage.

Focused Java checks cover authored pixels and metadata, native geometry and
format compatibility, actual Forge/vanilla buffer consumption and admission
lifetime. `GlassPaneTerrainGpuChecks` exercises the actual block-model and upload
paths in an OpenGL compatibility context. The additional `GlassPanePipelineChecks`
use actual `RenderChunk` rebuilding, uploads and dispatcher admissions through the
reproducible [EGL runner](../testclient/glass_egl/README.md). The live
`glassArtworkTerrainOnly` fixture on the `utility26` focus requires joined
artwork, opaque stock and artwork panes and the faintest stock tints to be chunk
geometry, with an animated Screen keeping its renderer. A software framebuffer
check does not establish hardware FPS or acceptance with a particular shader pack.

The additional `frames-before`/`frames-after` modes compare the exact branch
baseline with retained frame groups using 2,040 cells across eight joined walls,
all eight authored partial-alpha emissive frame textures, and their mipmaps.
They count actual legacy frame submissions and compiled tile-list admissions,
not just pane draws. The `portholes-before`/`portholes-after` modes compare the
real housing and aperture paths in 2,048 cells. Separate runtime checks exercise
material/pass combinations, real animated inserts, native frame handoffs,
anchor promotion and resource/world/format lifetime. Timed comparisons must run
with builds and other probes idle.

The [OptiFine G5 probe](../testclient/glass_egl/optifine/README.md) uses a privately
supplied official patched client to check the actual 56-byte vertex writers,
retained shader payload and VBO attribute hooks. It is separate from the
benchmark's minimal pass-selection facade. Neither harness is a full Forge
world running the player's shader pack; the in-game matched capture remains the
way to establish the effect on that world and GPU.

## Applying this update

This update targets `glasstmp2` at
`f494ecbc3db4a3efeeca6a26ece7fff73a6d0231`, which already contains the earlier
glass patches. Keep that branch's changes and apply this incremental patch once:

```sh
git apply --check vandorlabs-glasstmp2-frames-portholes.patch
git apply vandorlabs-glasstmp2-frames-portholes.patch
```

Preserve unrelated local work. Do not reapply earlier complete or experimental
patches over `glasstmp2`. Use Java 8 and Gradle 4.9 for the Forge 1.12.2 build.
