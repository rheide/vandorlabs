# Programmable Glass rendering costs

## Current rendering paths

Ordinary transparent stock-glass colours share a sorted draw batch. Static
selected materials and Screens in static playback mode also enter that batch,
using the existing mapped pane geometry and per-pane opacity. Stretch, Tile and
Fit artwork retain their joined layouts, inset and depth transforms. Shader
batches split at block-state changes to preserve metadata-dependent aliases.
Animated or off Screens, opaque panes, fluids and junction panes retain their
existing renderer and sorted placement.

Stationary custom flat frames and regular Programmable Glass L, T and cross
junction frames are published as immutable chunk meshes. The client prepares
geometry and owner lighting; chunk workers consume snapshots from unlisted
extended state. Tile frame drawing remains active until the replacement section
has actually uploaded. Edits, joins, lighting changes, resource/format reloads and
world/frustum changes invalidate publication. Queued rebuild work and retained
mesh bytes are bounded. Moving VehicleWorld frames retain their tile renderer;
the separate Programmable Diagonal Glass renderer is unchanged.

Each frame quad retains its actual atlas sprite. Flat frame artwork with partial
alpha stays in the translucent terrain layer; opaque or binary-alpha artwork
uses the cutout layer. Junction frames preserve their previous cutout behavior.
Two-sided geometry and the previous owner/emissive light values are retained.
Stock flat frames continue to use their original baked model.

Joined interior cells can have no frame vertices. Screens also omit borders
inside joined surfaces. Both empty cases return before lighting, texture binding
and graphics-state changes.

Mixed-orientation glazing builds a finite projected-overlap graph each frame.
The renderer now chooses the horizontal or vertical sweep with fewer interval
pairs. Fully acyclic graphs use a stable minimum-index topological sort;
cyclic graphs retain the strongly connected component fallback. Transparency
constraints and camera-side plane ordering are unchanged.

Fixed canopy glass already uses cached chunk geometry, including a bounded mesh
cache, dirty notifications and vertex-format invalidation. It does not rebuild
its entire mesh on each rendered frame. The shared `programmable_viewscreen`
tile ID also belongs to Programmable Blocks and other housings: tile-ID counts
must not be interpreted as actual screen counts.

## Release 2.9 measurements before this patch

The measurements below describe the released 2.9 artifact, before the static
material batching, chunk-frame publication and housing lighting changes above.
They do not measure the speedup from this patch.

An isolated copied scene was measured at camera feet `(1228,102,-12146)`, yaw
`-146`, pitch `7`, using OptiFine G5, six-chunk render distance, 70-degree FOV,
120 measured frames after warm-up, and software rendering. The shader comparison
uses Complementary Unbound 5.6.1 with a 128-block shadow distance. These timings
are render-only measurements with `glFinish`, not hardware FPS predictions.
Existing entities and the integrated server continue ticking. A copied region
from a running server is not an atomic world snapshot.

The loaded inventory within 80 blocks in X/Z contains 11,339 Programmable Blocks,
1,139 Programmable Glass blocks, 1,675 fixed canopy-glass blocks, and 169
Programmable Walls. It contains no ordinary Programmable Screens. These counts
include invisible blocks and are not draw counts.

The first no-shader comparison reduced median completed rendering from 36.58 ms
to 35.89 ms (about 1.9%), and median render-thread allocation from 7.93 MB to
7.61 MB. This measures the initial acyclic-sort and empty-frame changes before
the adaptive sweep. Tail timings varied considerably; repeated runs are needed
before attributing tail improvements to the patch.

A synthetic 4,096-node acyclic graph with edges to the next 31 nodes reduced
sorting from approximately 2.34 ms / 5.27 MB allocated to 0.61 ms / 96 KB.
That microbenchmark improvement does not imply the same percentage FPS gain.

The final unprofiled shader comparison used the same validated JAR and scene,
with diagnostic legacy behavior enabled for the baseline:

| Variant | Completed render median | Render-thread CPU median | Allocation median |
| --- | ---: | ---: | ---: |
| Legacy glazing behavior | 198.05 ms | 130.94 ms | 15.41 MB |
| Optimized glazing behavior | 200.93 ms | 133.28 ms | 15.07 MB |
| Glazing omitted (image-changing control) | 171.99 ms | 107.17 ms | 7.45 MB |

**This pair demonstrates no end-to-end FPS improvement.** The approximately 1.5%
render-time difference is small relative to observed run-to-run variation. Earlier
profiled shader runs measured 238.42 ms baseline and 229.64 ms optimized, so using
those alone would overstate confidence in an improvement. The no-shader pair above
is also preliminary, not a hardware FPS claim.

The final camera collected 1,085 glazing tiles. The adaptive sweep reduced
candidate overlap pairs from 70,750 (X) to 24,937 (Y), about 65%; the final sampled
sort was 1,016 microseconds baseline versus 921 microseconds optimized. These are
single-frame diagnostics, not averaged sort timings. Both paths batched 1,070
panes into seven draws, confirming that ordinary panes were already batched.

A shader render-thread sample profile was dominated by chunk VBO draws, shader
composite texture binds, and Immersive Railroading track draws. Direct glazing
methods appeared sparsely. Software-driver profiles do not establish the same
bottleneck on a hardware GPU.

## Larger optimizations to investigate

1. Cache packed pane batches or immutable GPU meshes for static arrangements.
   Cache geometry independently of transparency ordering. Camera motion can change
   ordering without changing geometry. Keep shadow and main-view state separate;
   preserve shader block/entity payloads, and invalidate on format/resource reload.
2. Measure retained geometry and graph reuse before adding a persistent scene
   cache. Invalidation must cover neighbor edits, artwork/tint/frame changes,
   chunk load/unload, world replacement, lighting, carried tile replacement and
   camera projection changes. Bound caches by bytes, not just entry count.
3. Use a glazing-omission diagnostic to bound potential savings before undertaking
   a large rewrite. If terrain, shader composites or other mods dominate, glass
   caching alone cannot restore the reported frame rate. Omission changes the
   image and must never be presented as a production optimization.

For every implementation, compare the exact copied camera and artifact, retain
block counts and screenshots, test plain Forge, OptiFine without shaders and
Complementary Unbound, and run the focused glass ordering/frame regressions.
Do not substitute software timings for hardware shader acceptance.

The omission control is approximately 14% faster than the final optimized run.
It removes geometry and changes its occlusion/shading effects, so it is not a
prediction of what caching alone can recover and does not preserve the image.
There remains a large non-glazing frame cost in this software-rendered scene.

## Release 2.9 baseline validation

The Java 8 candidate passes `testGlazingOrder` (1,139,886 graph assertions),
`testGlazingSweep` (258 projected-interval cases, including touching, empty and
infinite intervals), and `testDedicatedServerLinkage`. The exact standard JAR
passes the `glass-display-order` live focus under plain Forge, OptiFine G5 and
Complementary Unbound 5.6.1. The `artwork27` focus also passes joined-border,
independent-frame-side and framebuffer checks. These are focused patch checks,
not a full release gate or hardware FPS/shader-pack acceptance.

Validated Forge 1.12.2 artifact SHA-256:
`5b692f889b7622c433e23310f3915f7db00faab59eecfd4b3332af331f485ea6`.
Benchmarks ran on isolated copies. Deployment, when authorized, uses the exact
validated artifact after graceful shutdown and a verified offline backup.

## Focused checks for the rendering changes

The housing lighting checks compare physical face corners and complete vertex
records against vanilla FaceBakery, including rotated top/bottom faces, slabs,
storage and expanded shader formats. Deliberately displaced floor corners form
a negative control. The shader startup fixture also checks vertex lighting
through the installed terrain renderer before and after a resource reload.

The dense-render fixture compares legacy and batched pane pixels in 24 scenes:
stock colours, selected static materials, static Screens and mixed panes, both
orientations and all depth/texture-layout modes. Its draw-count and CPU samples
are diagnostics, not hardware FPS claims.

The opt-in glass-frame fixture checks native upload publication, edits, joins,
resource reload, stock fallback and captured frame geometry. Plain Forge also
compares legacy and chunk-model frame pixels in bright/dark and emissive scenes.
The native-only variant keeps publication and geometry checks under an active
shader pack, where a manual fixed-function framebuffer comparison is unsuitable.
