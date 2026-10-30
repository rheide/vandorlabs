# Programmable solid tile memory and state-build costs

Measured 2026-10-30 with Java 8, using `tools/measure_programmable_cost.sh`.
This benchmark changes no gameplay implementation and never opens a saved world.

## Method and limits

Each fixture contains a contiguous 16×16×16 section of 4,096 cubes, slabs or
straight stairs, with air outside. Compare a single main finish with 32 main
finishes distributed across the section; orientation and other settings are
identical. Tiles are serialized and reloaded before state-build measurements.
Sampling uses an isolated map-backed world with position-dependent light.

Each timing includes actual-state and extended-state construction, including
stair corner queries. It excludes model quad generation, vertex emission, Forge
lighting, GPU upload and rendering. These are **not complete chunk rebuild times
or FPS measurements**. Real Minecraft chunk-cache access costs can differ from
the fixture's map accesses. Results do not cover joined Fit/Stretch layouts,
custom face overrides, waterlogged blocks or shader formats.

The experimental comparison memoizes neighbour states and combined light in
per-section hash maps. Caches are cleared inside every timed batch; this is a
measurement prototype, not a shipped optimization. There are 100 warmup batches,
31 alternating paired samples, and two independent JVM runs. Timing ranges below
span the medians of those runs. Allocation uses the current thread allocation
counter. JVM instrumentation measures actual aligned object sizes.

## Retained tile memory

All three block families use the same tile class and yielded identical sizes.
Shared strings, enums, classes, block objects and worlds are excluded. Counts
include the tile, owned support objects and position, but exclude chunk/world
maps, lighting arrays, render meshes and static resource caches.

| Fixture | Owned bytes per tile, approximately | Bytes for 4,096 tiles |
| --- | ---: | ---: |
| Freshly created | 320 | 1,310,848 |
| Save/reloaded | 384 | 1,572,928 |

The small fleet totals include once-counted shared defaults; per-tile values are
rounded. Same and mixed main textures have identical memory use: texture indices
are primitive integers and do not duplicate artwork.

Component sizes: tile object 184 bytes; frame appearance 40 bytes; contained-fluid
helper plus its empty two-slot array 72 bytes; face overrides plus six-entry array
64 bytes. The remaining per-tile position occupies 24 bytes.

Fresh tiles share the default face-override object. Reloading creates 4,096
separate equivalent objects, even with overrides disabled. Canonicalizing this
specific default could save 262,080 bytes per section, or approximately 625 KiB
per 10,000 reloaded tiles per world instance. This is about 17% of the measured
owned tile graph, **not** 17% of total chunk memory.

Lazy frame/fluid support could potentially remove another 112 bytes per plain,
dry tile (about 1.07 MiB per 10,000). That is an object-size opportunity, not an
implemented saving: fluid behavior, appearance persistence, copying and vehicle
compatibility must be preserved. A separate tile type is not required to pursue
lazy allocation and is not justified by this measurement alone.

## State-build timing

Milliseconds per 4,096 blocks:

| Family | Finishes | Current median range | Hash-map memoization median range |
| --- | --- | ---: | ---: |
| Cube | Same | 0.255–0.258 | 0.361–0.448 |
| Cube | Mixed 32 | 0.257–0.258 | 0.386–0.431 |
| Slab | Same | 0.392–0.433 | 0.473–0.500 |
| Slab | Mixed 32 | 0.357–0.362 | 0.467–0.477 |
| Stairs | Same | 1.726–1.799 | 1.746–1.800 |
| Stairs | Mixed 32 | 1.776–1.805 | 1.798–1.816 |

For cubes/slabs, memoization reduces underlying state reads and light reads from
24,576 each to 5,632 each (77%). However, it increases allocation from 688,128 to
1,404,416 bytes per batch, and is slower. Fewer lookups alone do not establish a
performance improvement.

Stairs' actual-state shape queries perform 8,192 state reads, reduced to 4,608 by
memoization. Their extended-state path does not perform the cube/slab six-light
sampling. Allocation is 5,390,336 bytes per batch without memoization and
5,537,792 with it. Stairs therefore allocate 1,316 bytes per block in this combined
state-build path, versus 168 for cubes/slabs. This is transient allocation,
not retained memory. Extra immutable property-map/state construction is a more
promising target than neighbour memoization; its achievable benefit needs a
separate measured prototype.

## Decision

1. Canonicalizing default disabled face overrides after loading is a small,
   concrete memory optimization worth considering first.
2. Lazy optional support objects offer measurable but modest absolute savings;
   weigh them against compatibility and maintenance cost.
3. Do not ship the tested hash-map sampling cache: it loses on both time and
   allocation in these fixtures. A compact array cache would need fresh evidence,
   especially against Minecraft's actual chunk cache.
4. Investigate stair state construction before attempting more sampling caches.
5. Repeated main textures alone do not materially improve this state-build path.
   Existing shared model caches are outside this benchmark; no additional geometry
   cache or full-rendering speedup is established here.

## Vanilla comparison and larger textures

A subsequent run adds vanilla state-building baselines in the same fixture.
Per 4,096 blocks, medians in milliseconds:

| Shape | Vanilla | Programmable, one finish | Additional state-build time |
| --- | ---: | ---: | ---: |
| Cube (stone) | 0.0246 | 0.2792 | 0.2546 |
| Slab (stone slab) | 0.1268 | 0.3542 | 0.2274 |
| Straight stairs (oak) | 1.5609 | 1.7644 | 0.2036 |

Vanilla cubes/slabs allocate zero bytes in this state-building loop; vanilla
stairs allocate 1,753,088 bytes per section. Programmable cubes/slabs allocate
688,128 bytes, and stairs 5,783,552 in this run. Allocation can vary with JVM
escape-analysis decisions and benchmark ordering; the earlier stair result was
5,390,336. None of the three vanilla references requires a tile entity.
Ratios from this small substep must not be presented as total rebuild or FPS
ratios.

Inspection of the standard JAR finds `light_alloy_hull` at 16×16,
`dark_wall_panel` at 64×64 and `intricate_composite_wall_1` at 128×128.
For a single square RGBA8 sprite the theoretical uncompressed base-level storage
is 1 KiB, 16 KiB and 64 KiB respectively; including all mip levels it is roughly
1.33 KiB, 21.33 KiB and 85.33 KiB. These are per unique sprite, not per placed
block. Actual atlas allocation includes packing, padding and power-of-two sizing;
CPU copies and resource-load buffers are additional, and animation changes the
accounting. The calculation does not measure actual driver memory.

Higher image resolution changes texture coordinates/storage, not the number of
faces on an ordinary cube or slab. Repeating the same high-resolution sprite
across many blocks does not allocate another image per block. In this mod sprites
are stitched as resources load, so the atlas footprint is not newly incurred
for every placement. GPU texture-cache and bandwidth effects require a controlled
hardware-rendered benchmark; these state-building numbers cannot quantify them.
The larger-texture comparison therefore establishes memory scaling and unchanged
basic geometry, not hardware FPS parity.
