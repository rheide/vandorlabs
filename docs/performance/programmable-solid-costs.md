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
