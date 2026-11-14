# Native stationary Programmable Glass

Eligible stationary glass panes render in Minecraft's ordinary translucent chunk
buffers. Authored texture detail, including Gold glimmer, is enabled by default.
There is no world migration or required JVM argument.

Pane eligibility is independent of frame emissiveness. Opaque emissive frame
faces retain their native cutout layer and fullbright lighting. A frame mesh
containing blended emissive faces keeps its separate, correctly lit frame draw;
the pane still uses ordinary world lighting. Joined interior cells with no frame
geometry do not retain a frame renderer merely because the selected frame emits
light.

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
member. Other pane-only members leave the compiled tile list. Exceptional frame
draws remain independent. Main-view ordering uses the whole group's bounds;
holes remain holes and each occupied cell retains its authored texture mapping.
Grouping batches geometry; it does not replace a wall with a filled bounding box.

Native-palette groups use the same compensated single sheet as native terrain,
including very faint textures that cannot pass native terrain's alpha threshold.
Static custom atlas inserts retain their established artwork and two-sheet
geometry. Moving panes, corners, fluids and animated custom displays continue to
use their existing renderer.

## Edits and lifetime

Chunk workers consume immutable client-thread publications. Ownership is checked
against actual installed translucent vertex records, separately for each owner.
Physical presence and exact material freshness are distinct, so an unrecognized
sprite-local UV convention cannot cause duplicate fallback panes or endless
material rebuilds. Proven incomplete or stale results receive a bounded retry.

If a rendering extension does not retain inspectable native vertex records,
the manager withdraws its pane snapshots and requests a replacement chunk build.
Retained groups can take ownership only after that rebuild has completed its
uploads. A transparency resort does not authorize the handoff. This avoids an
indefinite unverified native/fallback state.

Existing layer uploads finish before the next snapshot is published. Compile
locks are polled without blocking the client thread. Edits retain Minecraft's
ordinary asynchronous chunk-rebuild latency; the manager does not turn separate
layer uploads into an atomic GPU transaction.

Unchanged sections avoid per-owner admission sweeps. Local mesh templates share
an 8 MiB cache, and at most 1,024 dirty owners are prepared per flush. Resource or
vertex-format changes rebuild these templates. Repeated stale worker requests
do not restart a format rebuild already progressing through that queue.
Retained groups have a separate 16 MiB budget covering their CPU and GPU record
copies, a 2 MiB limit per group, and an eight-section rebuild limit per flush.

## Supported cases

The native path supports flat, stationary native-palette panes, including Gold,
with stock frames, selected emissive or nonemissive frames, or no frame. Tint, placement
depth, orientation and frame joins remain intact.

Static custom atlas inserts and very faint palette materials use retained groups
when their vertex format and shader hooks are supported. Native terrain's alpha
threshold would otherwise remove some faint pixels. Opaque panes, corners,
contained fluids, moving VehicleWorld glass, animated custom displays and
unsupported retained layouts keep the existing renderer.
All authored frames are checked after compensation; positive alpha must be at
least 27/255. Faint artwork is not clamped or silently removed.

Native transparency has Minecraft's usual sorting limitations. Overlapping
windows are not discarded using an approximate layer cap.

## Client options

Options are Java system properties read at startup. Restart after changes.

| Property | Default | Meaning |
| --- | --- | --- |
| `vandorlabs.staticGlass` | `true` | Enable native stationary panes and retained fallback. |
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
pane owners and pruned tiles, with pending sections draining. Build and rebuild
counters should stop increasing when the camera and world are unchanged.
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
GPU timestamps where supported, native and retained coverage, and the camera
and graphics settings at each end of the capture. Retained main/shadow timings
cover those glass draws, not the entire shader shadow pass. Nested scopes
overlap and must not be summed. Query results are read only when available;
the profiler never calls `glFinish`. Profiling is disabled outside a capture.

For matched comparisons, use the same world, fixed camera, resolution, shader
pack and settings; wait for pending sections to drain first. Keep the previous
build as the baseline. A changed camera, active rebuilds or dropped samples must
be accounted for before interpreting a timing difference. Functional checks
and owner counts alone do not establish an FPS improvement.
The earlier JAR does not include this new command: use the same existing FPS or
profiler capture on both JARs, and add this report on the new JAR to verify its
coverage and GPU intervals. Selecting `legacy` is a useful diagnostic but does
not reproduce every behavior of the earlier JAR.

Focused Java checks cover authored pixels and metadata, native geometry and
format compatibility, actual Forge/vanilla buffer consumption, publication and
admission lifetime. `GlassPaneTerrainGpuChecks` exercises the actual block-model
and upload paths in an OpenGL compatibility context. The additional
`GlassPanePipelineChecks` and `GlassPaneRetainedPipelineChecks` use actual
`RenderChunk` rebuilding, uploads and dispatcher admissions. The reproducible
[EGL runner](../testclient/glass_egl/README.md) also compares the previous main
batch and per-tile shadow path with retained groups. A software framebuffer check
does not establish hardware FPS or acceptance with a particular shader pack.

## Applying this update

The download contains two alternatives. If the previous
`vandorlabs-native-glass-complete.patch` is already applied (equivalent source
revision `7735c3541c0cc76135cd3d980a380e8828efc470`), apply the upgrade:

```sh
git apply --check vandorlabs-glass-emissive-upgrade.patch
git apply vandorlabs-glass-emissive-upgrade.patch
```

The complete alternative is self-contained against public master revision
`97f1325420fd8e6f7d7281c913129b24aeea0637`, without earlier experimental glass
patches applied:

```sh
git apply --check vandorlabs-glass-emissive-complete.patch
git apply vandorlabs-glass-emissive-complete.patch
```

Apply one alternative, not both. Preserve unrelated local work; a separate
checkout can be used to build the complete version. The earlier combined,
grouped-coverage and stationary-glass downloads are not prerequisites for the
complete patch. Use Java 8 and Gradle 4.9 for the Forge 1.12.2 build.
