# Stationary Programmable Glass and Portholes

Eligible stationary glass panes render in Minecraft's ordinary translucent chunk
buffers. Authored texture detail, including Gold glimmer, is enabled by default.
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
sorting. There is no separate native-pane draw, group anchor or private shader
pointer setup. Once installed pane geometry is confirmed, every settled
pane-only tile is removed from its compiled section's tile-render list. The tile
entities continue to store gameplay and configuration data.

Stationary flat panes that cannot use native terrain have a retained grouped
fallback. A group contains compatible coplanar panes within one 16-block render
section. It retains its vertex buffer across frames, including shader shadow
passes. One real member acts as the group's renderer, with bounds covering every
member. Other pane-only members leave the compiled tile list. Retained frame
groups have independent anchors. Main-view ordering uses the whole group's bounds;
holes remain holes and each occupied cell retains its authored texture mapping.
Grouping batches geometry; it does not replace a wall with a filled bounding box.

Native-palette groups use the same compensated single sheet as native terrain,
including very faint textures that cannot pass native terrain's alpha threshold.
Static custom atlas inserts and opaque palette panes retain their established
artwork, opacity and two-sheet geometry. Opaque groups draw in pass zero with
depth writes. Moving panes, corners, fluids and animated custom displays continue
to use their existing pane renderer; they do not disqualify an otherwise eligible
stationary frame from native or retained rendering.

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

Chunk workers consume immutable client-thread publications. Ownership is checked
against actual installed translucent vertex records, separately for each owner.
Physical presence and exact material freshness are distinct, so an unrecognized
sprite-local UV convention cannot cause duplicate fallback panes or endless
material rebuilds. Proven incomplete or stale results receive a bounded retry.

If a rendering extension does not retain inspectable native vertex records,
the manager withdraws its pane snapshots and requests a replacement chunk build.
Retained groups can take ownership only after that rebuild has completed its
uploads. A resort of the old geometry does not authorize the handoff. A successor
resort can acknowledge the distinct replacement after its upload; the old
installed result and the task present before withdrawal remain excluded. The
same fence protects native-to-retained frame transitions, including material
edits while a chunk task is uploading.

Existing layer uploads finish before the next snapshot is published. Compile
locks are polled without blocking the client thread. Edits retain Minecraft's
ordinary asynchronous chunk-rebuild latency; the manager does not turn separate
layer uploads into an atomic GPU transaction.

Unchanged sections avoid per-owner admission sweeps. Local mesh templates share
an 8 MiB cache, and at most 1,024 dirty owners are prepared per flush. Resource or
vertex-format changes rebuild these templates. Repeated stale worker requests
do not restart a format rebuild already progressing through that queue.
Retained pane groups and porthole groups each have a separate 16 MiB budget;
retained frame groups have a 32 MiB budget. Budgets cover CPU and GPU record
copies and owner bookkeeping. Groups are limited to 2 MiB, with at most eight
sections rebuilt per manager flush. Larger frame or porthole groups split into
bounded groups. Exceeding a total budget restores the existing renderer rather
than omitting geometry.

Changes are queued from tile callbacks and applied at safe render-thread flush
boundaries. Group retirement and compiled tile-list restoration happen together,
outside an active render iterator. Resource/format changes, world switches,
member removal, relighting and joined porthole topology changes retire affected
buffers. A long joined aperture records its full dependency region, including
across section boundaries.

## Supported cases

The native path supports flat, stationary native-palette panes, including Gold,
with stock frames, selected emissive or nonemissive frames, or no frame. Tint, placement
depth, orientation and frame joins remain intact.

Static custom atlas inserts, opaque inserts and very faint palette materials use
retained groups when their vertex format and shader hooks are supported. Native
terrain's alpha threshold would otherwise remove some faint pixels. Frame
selection does not force these inserts out of their fast path.

| Stationary combination | Surround | Glass or insert |
| --- | --- | --- |
| Stock, bare or native-compatible selected frame | Existing native geometry, or empty | Native terrain when eligible; otherwise retained |
| Selected blended emissive frame + palette glass | Retained frame group | Native terrain or retained pane group |
| Selected blended emissive frame + static custom/opaque insert | Retained frame group | Retained pane group in the appropriate pass |
| Selected blended emissive frame + animated insert or contained fluid | Retained frame group | Existing dynamic/fluid renderer |
| Porthole + static palette/custom/opaque glass | Retained housing group | Retained aperture group |
| Porthole + animated insert or contained fluid | Retained housing group | Retained static aperture where eligible; dynamic insert/fluid rendering remains independent |

Programmable Glass corners keep their existing native frame and shaped-pane
paths. Moving VehicleWorld geometry and unsupported retained formats keep the
existing renderer. These are explicit compatibility cases, not eligibility
tests based on whether a frame emits light.
Native eligibility checks every animation frame of the palette texture after
compensation; positive alpha must be at least 27/255. Fainter artwork uses
retained panes and is not clamped or silently removed. This threshold does not
reclassify blended frame artwork as opaque.

Native transparency has Minecraft's usual sorting limitations. Overlapping
windows are not discarded using an approximate layer cap.

## Client options

Options are Java system properties read at startup. Restart after changes.

| Property | Default | Meaning |
| --- | --- | --- |
| `vandorlabs.staticGlass` | `true` | Enable native stationary panes and retained panes, emissive frames and portholes. |
| `vandorlabs.staticGlassRenderer` | `terrain` | Prefer native chunks with retained fallback; `groups` selects retained groups; `legacy` selects the existing per-tile pane renderer for comparison. |
| `vandorlabs.staticGlassTexture` | `authored` | Native panes preserve glimmer; `smooth` explicitly selects a constant diffuse tint. |
| `vandorlabs.staticGlassSingleSurface` | `true` | Use one compensated sheet; `false` keeps two sheet positions. |
| `vandorlabs.glassPaneShadows` | `native` | Preserve pane shadows. `frames` uses retained pane groups in the main view and omits their pane shadow draws, while frame shadows remain. |
| `vandorlabs.staticGlassDiagnostics` | `false` | Log native ownership, pruning, pending work and fallback counts. |

An earlier `staticGlassTexture=authored` option can stay. Explicit
`glassPaneShadows=frames` now keeps an optimized retained main-view path. It is a
visual tradeoff: panes no longer contribute tinted shadows. The default retains
pane shadows. `staticGlassRenderer=groups` selects an actual retained renderer.

The benchmark's `benchmarkLegacyGlazing=true` and no-glass settings keep their
comparison behavior. Smooth and single-sheet settings apply to native and
retained native-palette panes; they do not change custom insert artwork or the
per-tile compatibility renderer.

## Diagnostics and verification

For a settled eligible area, expect `renderer=terrain`, `texture=authored`, native
or retained pane owners and pruned tiles, with pending sections draining. A wall
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
GPU timestamps where supported, native/retained pane, frame and porthole coverage,
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
builds. The new report adds frame and porthole coverage. Selecting `legacy` is a
useful diagnostic but does not reproduce every behavior of the earlier JAR.

Focused Java checks cover authored pixels and metadata, native geometry and
format compatibility, actual Forge/vanilla buffer consumption, publication and
admission lifetime. `GlassPaneTerrainGpuChecks` exercises the actual block-model
and upload paths in an OpenGL compatibility context. The additional
`GlassPanePipelineChecks` and `GlassPaneRetainedPipelineChecks` use actual
`RenderChunk` rebuilding, uploads and dispatcher admissions. The reproducible
[EGL runner](../testclient/glass_egl/README.md) also compares the previous main
batch and per-tile shadow path with retained groups. A software framebuffer check
does not establish hardware FPS or acceptance with a particular shader pack.

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
