# Segmented Door native rendering contract

The section transform, panel/window mesh builder and leading-edge border use
portable scalar kernels. Atlas lookup and vertex formats remain runtime adapters.
Meshes own pixel-space float records containing position, atlas UV and normal;
frame emission streams through the active vertex consumer, including its light,
overlay and normal attributes. Mesh caches must never retain tiles or worlds.

The native renderer must distinguish terrain eligibility from the closed-terrain
handoff. The released eligible panel path omits internal horizontal caps only at
exact closed/open endpoints. The feature/window path keeps its caps. Closed
suppressed columns draw their fixed border without drawing duplicate panels.
Contained fluid remains a separate chunk-model responsibility.

Window layouts use a bottom-up row index. Body texture layout uses the panel's
row within the column; window glass uses `height - 1 - row` within the resolved
window group. Saved layout IDs are Stretch 0, Tile 1 and Fit 2. Fit preserves the
released aspect-match threshold. Custom door artwork uses two half sprites and
an aspect of one half, with repeat-boundary clipping before UV interpolation.

Focused references capture actual released Forge emitters before extraction:

- `exportSegmentedMeshReference`: 960 section poses and 23,040 vertices.
- `exportSegmentedPanelReference`: 900 panel/window profiles and 48,288 vertices.
- `exportSegmentedHeaderReference`: 432 border profiles and 27,360 vertices.
- `exportSegmentedFastReference`: 60 eligible-column profiles and 8,928 vertices.
- `exportSegmentedDoorArtworkReference`: 1,260 upper/lower artwork profiles and
  90,216 vertices, including rectangular groups that select aspect Fit.
- `exportSegmentedClockReference`: 40 sequenced shared/fallback clock cases.

The source adapters reproduce the complete frozen buffers byte for byte. Native
checks compare raw scalar positions, UVs and packed normals against those
references, and separately assert the native consumer's color/light/overlay
conventions and immutable mesh reuse. These checks do not establish placed-world
chunk handoff, shader-pack acceptance or hardware frame rates.

`NativeSegmentedVisual` contains shared settings, immutable per-row
settings and window layouts, and separate readiness, suppression, chunk-wait and
terrain-eligibility flags. Its `Motion` projection carries immutable identity and
sequence/group fields. The client clock preserves late-column grouping, ignores
repeated or older sequences, retains reversals and uses the released six visual
substeps per section tick. `resume` preserves the terrain-to-animation handoff.
The host supplies a reused row-light array; lighting invalidation and the terrain
handoff remain host responsibilities. The released renderer is global for root
columns and uses a 256-block view distance.

The bake retains byte-bounded mesh and snapshot caches (16 MiB each), a 2 MiB
aperture cache and bounded material metadata. Resource reload replaces the bake;
a generation guard prevents late publication from replacing newer state. Fully
suppressed eligible columns skip geometry, and empty panes do not request a
translucent buffer.

The focused GPU check draws eight three-column scenes, reloads resources and
repeats them. It asserts pane-removal and panel-suppression negative controls.
A warmed emitter probe samples a 361-frame quantized stroke at the default ten
ticks per section. The released progress cache is cleared to represent a new
descriptor; native static geometry is prepared before motion. Both the released
cached path and native path emit 25,944 vertices. The released path prepares 181
progress templates; the native path prepares no new meshes during the stroke.
The measured native scalar/consumer emitter allocates zero bytes in that probe.
The reference measurement subtracts a steady-call baseline to exclude reflective
fixture overhead. These are mesh-emission/cache measurements, not GPU or whole
client allocation measurements.

Segmented panes use a dedicated fixed render buffer so later opaque columns and
backing geometry cannot overwrite an already blended pane. The GPU fixture
asserts this with an opaque backing panel submitted after the window.
