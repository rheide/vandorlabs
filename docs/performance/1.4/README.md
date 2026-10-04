# 1.4 performance measurements

Reference: release 1.3 production code with the expanded trapdoor benchmark
fixtures in `8b666cd6`. Measurements use the standard texture pack.

## Trapdoor texture clipping

A rigid leaf's texture clipping depends on its material coordinates and layout,
not its animated pose. Clipping now produces a bounded reusable layout in local
corner coordinates. Rendering applies the current leaf transform, lightmap and
atlas sprites. The cache holds at most 512 layouts and 65,536 points; its keys
copy their UV inputs. It contains no tile entities, worlds, or atlas sprites.

The non-rendering suite compares 7,680 moving and stationary cases against the
released clipping algorithm, including the three diagonal shapes, both slopes,
all facings, Fit/Tile, alternating mirrored columns and separate custom-door
halves. Position/UV tolerance is one millionth of a block/atlas coordinate;
packed colors, lightmaps and normals must match exactly. Cache bounds and
clearing are checked separately.

`benchmarkTrapdoorMesh` measures 256 surface draws per batch with 15 warmups and
31 measured batches, using the same moving corner inputs for both algorithms.
This isolates CPU emission and allocation; it excludes world lookup, group
validation, draw calls and the GPU.

| Surface | 1.3 median (ms) | Cached median (ms) | 1.3 allocation (bytes) | Cached allocation (bytes) |
| --- | ---: | ---: | ---: | ---: |
| Flat leaf | 0.312 | 0.186 | 368,640 | 6,144 |
| Clipped diagonal leaf | 0.490 | 0.280 | 679,936 | 6,144 |
| Diagonal custom door | 0.360 | 0.285 | 663,552 | 6,144 |

These paired measurements show 21–43% lower CPU cost and 98–99% less allocation
in texture emission. They do not establish an FPS improvement.

The full live suite passed on release 1.3, the first optimized build, and the
housing/power optimization build, including
rendering, GUI/network, joining/redstone, placement, inventory and copy contracts.
The first live before/after benchmark pair passed all 56 static image comparisons: at least
99.99% of pixels are within 3/255 per channel. Across the 64-leaf trapdoor cases,
render-thread allocation fell by 64–67%. Representative submission medians were
0.279 → 0.235 ms for flat Fit, 0.329 → 0.248 ms for flat custom-door artwork, and
0.330 → 0.271 ms for shallow diagonal leaves. Early cases have greater timing
variation; retain the full CSVs and vanilla controls when interpreting results.

## Loaded redstone channels

Channel state is an OR over current local inputs. Reconciliation now first
rechecks the previous powered member, if it still belongs to the channel. A
live source proves the OR immediately. When it stops or disappears, the code
searches a fresh member snapshot; it does not maintain a potentially stale
incremental count. Notifications remain synchronous and loaded-chunk-only.

A 64-member test with 256 unchanged input notifications drops from 16,384 local
power queries to 256. A separate full-OR oracle covers 2,000 randomized sequences
with multiple input changes before an event, removal/rejoining and separate
worlds. Finding that a channel is unpowered still requires checking all members.

Ungated Static/Animated screens no longer query physical power during display
selection. A powered channel also satisfies trigger/display queries immediately;
the channel's own local-input probe remains physical-only. Off still wakes to
Animated when powered, including with the gate disabled. A complete mode/gate/
local/channel matrix reduces irrelevant neighbor-state reads from 72 to zero.

Joined light groups stop their input search at the first powered member, then
propagate the result to every member as before. The 64-light check exercises
different source positions, fully unpowered groups and source removal. A source
at the traversal origin reduces input queries from 64 to one; unpowered groups
still query all 64 members.

Trapdoor power settlement marks a member dirty only when its saved power state
changes; changed channel signals are persisted separately even if the group's OR
stays high. A powered local channel also proves the group's OR without physical
queries. Across 256 unchanged events on 64 leaves, dirty calls fall from 16,640
to zero. With the receiving leaf's channel powered, local-input reads fall from
16,128 to zero. A 600-event oracle compares open states and full NBT under all
three trigger modes, coalesced physical/channel changes and final power loss.

## Trapdoor assembly and temporary data

Equal saved assembly lists become one immutable list after exact validation.
Subsequent group queries still check every member's loaded tile and compatibility,
but avoid comparing all positions against an equal list for every member. There
is no persistent world-topology cache. Tests cover a 64-leaf group, chunk-boundary
fallback, individual NBT edits, removed tiles, restored members and unlinking.

Each rendered diagonal leaf shares one group snapshot between its moving corners
and material layout. Closed material coordinates do not resolve the group's
opening direction. Fixed edge UV arrays are reused, corner arrays are allocated
once, and opposing-cover bounds are sampled once per layout.

## Block, Slab, Storage and Stairs chunk meshes

Housing state construction fills Forge's unlisted-property map in one pass,
preserving its listed-property transitions and canonical clean state. Neighbor
sampling reuses local mutable positions. Ordinary single-material Block/Slab/
Storage models also retain bounded immutable face lists; mixed overrides still
select each face independently. All model state remains safe for concurrent
chunk-building workers.

The checks compare 256 state configurations with Forge's original sequential
builder, and compare 12,288 model cases byte-for-byte with the 1.3 implementation.
Coverage includes every visibility mask, facing, slab half, tiling setting,
explicit/inherited storage art and mixed face overrides, plus concurrent reads
and cache bounds. Live Storage inventory/material/settings checks also pass. All 58 static benchmark images match within the existing
99.99% / 3-per-channel threshold, including Storage and Stairs.

Warm chunk-build measurements use 15 warmups and 31 measured samples, with eight
fixture rebuilds per sample. Values below are per 64-block fixture. This measures
chunk rebuild CPU work rather than steady drawing of the already-built chunk.

| Fixture | Before (ms) | After (ms) | Before allocation (bytes) | After allocation (bytes) |
| --- | ---: | ---: | ---: | ---: |
| Block, separated | 0.276 | 0.246 | 359,072 | 172,704 |
| Block, 8×8 floor | 0.179 | 0.143 | 287,392 | 108,192 |
| Block, 4×4×4 solid | 0.146 | 0.113 | 265,376 | 87,648 |
| Slab, separated | 0.296 | 0.256 | 366,624 | 180,256 |
| Storage, separated | 0.286 | 0.244 | 369,184 | 173,600 |
| Stairs, separated | 0.782 | 0.755 | 354,848 | 301,088 |

Vanilla control allocations are unchanged. Block/Slab/Storage show 11–23% lower
median rebuild time and 47–67% lower allocation across their fixtures. The small
Stairs timing difference is within ordinary variation; its allocation reduction
is measurable. Complete results, including controls, are in the mesh-build CSVs.

## Diagonal trapdoor collision

Collision queries previously reconstructed 16 slices, or 256 slices for an open
rotating panel, each with temporary corner and bounds arrays. The rigid corner
coordinates now select a reusable local collision mesh. The cache holds at most
128 meshes, copies its corner keys and contains no world or tile references.
Every query still uses the leaf's current geometry and owning position.

A cached union rejects misses before scanning individual boxes. Only intersecting
world-space boxes are allocated. The non-rendering checks compare 6,048 complete
collision lists against the 1.3 algorithm, including box ordering, all shapes,
facings and movements, grouped travel, large coordinates and strict edge contacts.
Additional checks cover caller-mutated corner arrays, concurrent access and the
cache limit. `benchmarkTrapdoorCollision` alternates the two algorithms with
64 queries per measured batch and reports CPU time and thread allocation.

| Collision batch | 1.3 median (ms) | Cached median (ms) | 1.3 allocation (bytes) | Cached allocation (bytes) |
| --- | ---: | ---: | ---: | ---: |
| Closed, all boxes retained | 0.388 | 0.062 | 573,440 | 69,120 |
| Rotated open, all boxes retained | 3.808 | 0.124 | 14,155,776 | 1,052,160 |
| Rotated open, partial overlap | 3.832 | 0.071 | 14,155,776 | 441,856 |
| Sliding open, all boxes retained | 0.303 | 0.040 | 507,904 | 69,120 |

These are warmed mesh-query measurements, excluding world/group lookup and corner
construction. Cold queries build the mesh before filtering; cache eviction can
therefore reduce the benefit. Complete measurements, including misses, are in
`collision.csv`.

## Material name lookup

Catalog texture names are resolved once after bootstrap, including door halves,
unlit light variants and storage top/side artwork. The checks compare 624 choices
with the previous lookup rules and repeat with filesystem entries and replacement
Custom-material providers. Numeric identities, retired-art fallback and missing
peer textures are unchanged. Only strings are retained; atlas sprites and Custom
material resolution remain live. `benchmarkTextureNames` measures name selection
separately from atlas lookup and rendering.

Across 14,880 mixed square/unlit/storage lookups, the paired median falls from
2.578 ms and 5,785,472 allocated bytes to 0.080 ms and zero allocation. This
measures name selection only, not the total cost of rendering a block.

The later live benchmark also passes all 58 static image comparisons. Per 64
instances, Diagonal Wall allocation falls from 35,360 to 17,952 bytes, Wall from
42,528 to 25,120, and Light from 57,888 to 23,584. Their small timing differences
are mostly within run variation; the allocation reductions are consistent. Door
submission cost remains largely unchanged. `render-after-later.csv` contains this
run, retaining the vanilla controls.

## Offset picking and collision queries

The traversed grid-cell path selects an immutable list of candidate coordinates.
Its iteration order exactly matches the previous HashSet, preserving precedence
for equally distant hits. The cache retains at most 32,768 candidate positions
and no worlds, tile entities or hit results. Every query still tests current
loaded blocks. Offset collision scanning also reuses a mutable position without
retaining it in returned bounds.

Checks compare 1,590 exact candidate lists and 1,600 full world-query results,
including chunk boundaries and removal between repeated rays. In the in-memory
empty-world benchmark, 256 warmed picking queries fall from 2.634 to 0.164 ms
and 5,855,232 to 106,496 allocated bytes. The corresponding offset collision
batch falls from 0.225 to 0.140 ms and 647,168 to 20,480 bytes. The synthetic world
isolates query overhead; real-world block lookup and actual leaf intersection
costs remain. `offset-interactions.csv` preserves these paired measurements.

## Reproduction

Use Java 8 for every Gradle command. Run the rendering clients sequentially to
avoid CPU contention. `VANDOR_LABS_COMPAT_MODS` selects the directory containing
the three compatibility test mods required by the live suite.

```bash
./gradlew build testNonRendering benchmarkTrapdoorMesh benchmarkHousingState benchmarkTrapdoorCollision benchmarkTextureNames benchmarkOffsetInteractions --no-daemon
bash testclient/test_viewscreen.sh --full
bash testclient/benchmark_programmable.sh
python3 testclient/compare_programmable_benchmarks.py BEFORE.csv AFTER.csv
python3 testclient/compare_programmable_images.py BEFORE_RUN AFTER_RUN
```

The live rendering benchmark uses Mesa software rendering. Its submission and
completion times are synthetic batch measurements, not hardware GPU or shader
performance claims. The CSVs retain the vanilla controls for comparison.
