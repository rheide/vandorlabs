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

The first live before/after pair passed all 56 static image comparisons: at least
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

## Reproduction

Use Java 8 for every Gradle command. Run the rendering clients sequentially to
avoid CPU contention. `VANDOR_LABS_COMPAT_MODS` selects the directory containing
the three compatibility test mods required by the live suite.

```bash
./gradlew build testNonRendering benchmarkTrapdoorMesh --no-daemon
bash testclient/test_viewscreen.sh --full
bash testclient/benchmark_programmable.sh
python3 testclient/compare_programmable_benchmarks.py BEFORE.csv AFTER.csv
python3 testclient/compare_programmable_images.py BEFORE_RUN AFTER_RUN
```

The live rendering benchmark uses Mesa software rendering. Its submission and
completion times are synthetic batch measurements, not hardware GPU or shader
performance claims. The CSVs retain the vanilla controls for comparison.
