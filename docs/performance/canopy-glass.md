# Canopy Glass rendering costs

Plain fixed Canopy Glass now joins Minecraft's solid and translucent chunk
meshes. Opening canopies and carried canopies retain their tile renderers.
The existing frame geometry, logical joins, retained roof rail next to an
opening canopy, collision and cockpit sharing are preserved. Fixed glass
remains non-opaque to neighbouring blocks, including with Opaque tint.

## Fixed glass chunk rendering

The same 256-block Normal sheet measures:

| Path | Draw calls | Vertices | VBO bytes | CPU submission | Bytes allocated/frame |
| --- | ---: | ---: | ---: | ---: | ---: |
| Vanilla glass | 1 | 2,304 | 64,512 | 0.027 ms | 0 |
| Carried Canopy Glass, unchanged | 316 | 23,264 | 651,392 | 1.099 ms | 127,344 |
| Fixed Canopy Glass, chunk mesh | 2 | 23,264 | 651,392 | 0.164 ms | 80 |

The chunk benchmark's 80 bytes come from its two-layer submission loop;
Minecraft submits the normal chunk layers. This reduces measured submission
CPU by about 85%, without reducing geometry to vanilla glass complexity.
Draw counts describe one occupied chunk section; multiple sections and
render passes naturally require more submissions. These software OpenGL
measurements do not establish hardware FPS or shader-pack acceptance.

Immutable atlas quads are shared through an 8 MiB weighted cache and published
on the client thread. Chunk workers consume snapshots without scanning
neighbours. Topology, material edits, chunk loading, atlas reload and vertex
format changes invalidate the appropriate snapshots. Lighting and translucent
quad sorting use Minecraft's chunk renderer. The tile remains for gameplay,
settings, reservations, collision and saved-data compatibility. Only the
fixed native block changes renderer; there is no block-state palette change.

The complete comparison is in [chunk.csv](canopy-glass/chunk.csv).

## Earlier tile-renderer optimization

## Dense joined example

These measurements use a 16 by 16 horizontal sheet, default Canopy dimensions,
clear glass and a stationary camera. CPU times are median render submission
time per frame; they are not hardware frame times or FPS.

| 256 blocks | Draw calls | Vertices | VBO bytes | CPU before | CPU after | Allocated bytes/frame before → after |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Vanilla glass | 1 | 2,304 | 64,512 | 0.026 ms | 0.024–0.039 ms | 0 → 0 |
| Vanilla stained glass | 1 | 2,304 | 64,512 | 0.034 ms | 0.024–0.028 ms | 0 → 0 |
| Canopy, Normal | 316 | 23,264 | 651,392 | 1.941 ms | 1.053–1.069 ms | 1,754,064 → 127,344 |
| Canopy, Opaque | 256 | 23,264 | 651,392 | 1.810 ms | 0.737–0.742 ms | 1,754,064 → 66,024 |

The optimization reduces Normal submission time by about 45% and allocations
by 92.7% in this case; Opaque improves by about 59% and 96.2% respectively.
All 33 before/after scenarios retain identical vertex, draw-call and VBO byte
counts. An isolated Canopy has 2,472 vertices versus 24 for vanilla glass;
joining neighbours suppresses interior frame surfaces. This is a comparison
of the same occupied cells, rather than equivalent-looking geometry.

Mild, Normal and Strong are close: approximately 1.05–1.07 ms and 127,344
allocated bytes for the clear 256-block sheet. Cyan Normal and a general
material have similar costs. Repeated measurements of a general material
range from 1.05 to 1.14 ms. Opaque general material is about 0.74–0.77 ms.
Choosing a new shade or opacity incurs a one-off geometry/cache rebuild and
VBO upload; the measured 256-block edits are several milliseconds, rather
than a persistent increase in steady rendering cost.

## Changes

The renderer reuses exact settings keys, compares cached face settings before
constructing keys, reads the neighbour connection mask once per face query,
and reuses the finite model names. It skips an empty translucent pass for
Opaque and avoids allocating camera vectors during glass sorting.

The settings cache has one entry per settings instance and retains the same
saved representation. It checks directly mutable fields and channel values
before reuse; channel, layout, geometry, shade and opacity changes invalidate
it. This adds a small fixed settings snapshot per instance; the allocation
figures above measure transient allocation, not retained heap size.

Opening and carried canopies still use more submissions than vanilla. Their
moving geometry and per-tile transparency handling remain on the existing
path; the chunk optimization applies only to native fixed Canopy Glass.

## Method and limits

The isolated Forge 1.12.2 client uses Java 8, OptiFine G5, VBOs and no shader
pack. A read-only scene contains 1, 64 or 256 Canopies, or the equivalent
vanilla glass/stained-glass footprint. Vanilla faces are baked with the actual
block model renderer into one layer VBO; Canopies use their actual tile
renderer. Offscreen rendering is 64 by 64 pixels with a software OpenGL
renderer. Warmup precedes 11 samples of three frames; allocation uses the
Java thread allocation counter. GPU completion is excluded from submission
time. Shade/opacity edit timings include renderer rebuild/upload only.

These figures establish CPU submission, transient allocation and geometry
costs. They do not establish GPU throughput, shader-pack performance, server
tick cost, full-world chunk rebuilding, retained heap or network edit latency.
Software renderer timings should not be translated into hardware FPS.

Raw data: [before](canopy-glass/before.csv), [after](canopy-glass/after.csv),
[independent repeat](canopy-glass/repeat.csv). Key invalidation and model-name
checks run through `testCanopyAnimationCache`; the live `canopy-cost` focus
emits the measurements and validates decoded scene settings.
