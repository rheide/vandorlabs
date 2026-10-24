# Fluid presence checks during rendering

Programmable Doors called `WaterloggedParts.wet` during render-pass selection, fast-renderer selection and fluid drawing. A dry aperture scanned six loaded neighbours on every call. An aperture beside water also resolved both fluid sides, decoding fluids and rebuilding its closed-leaf plane. Trapdoors used the same path. Walls and diagonal walls already gated their fluid pass on stored fluid; they did not perform this aperture neighbour scan.

The rendering path now reuses a native-water adjacency mask in a bounded, 2,048-position cache per client world. Block/render updates and chunk load/unload invalidate nearby masks. Stored fluids are read live, and the current opening plane is evaluated when adjacent water exists. Gameplay queries retain the existing implementation; vehicle simulation views bypass the adjacency cache. Gameplay fill, flow, bucket, sponge and opening behavior remain unchanged.

A shared `NeighbourhoodCache<V>` supplies the same bounded position storage and neighbouring-range invalidation to the adjacency cache and the existing fluid surface mesh cache. No world reference is stored in the cache value; world unload releases its listener and entries.

## Measurements

The retained `WaterloggedParts.wet` implementation is the before reference. Both paths run in the same Forge client process, against identical loaded client-world fixtures, after warm-up. Each CPU/allocation result is the median of seven interleaved samples of 5,000 queries. These measure the query path, not complete frames or GPU performance. Repeated queries at one position favour warm caches; the results are not an FPS prediction.

| Scenario | Before CPU, ns / 5,000 | Cached CPU, ns / 5,000 | Before allocations, bytes | Cached allocations, bytes |
| --- | ---: | ---: | ---: | ---: |
| Dry door | 594,816 | 123,271 | 920,000 | 0 |
| Door beside water | 2,567,889 | 406,713 | 3,000,000 | 280,000 |
| Dry wall | 316,273 | 84,949 | 0 | 0 |
| Wall with stored water | 157,987 | 97,843 | 0 | 0 |
| Dry diagonal wall | 314,650 | 84,569 | 0 | 0 |
| Diagonal wall with stored water | 157,696 | 98,164 | 0 | 0 |

The door query samples used approximately **79% less CPU when dry** and **84% less beside water**. The dry path eliminated allocation; the wet aperture path reduced allocation by about 91%.

Independent counted-world checks performed the same 5,000 queries. Dry doors changed from **40,000 block-state reads to zero** after warm-up; doors beside water changed from **125,000 to 15,000**. Plain and diagonal walls changed from 10,000 reads when dry, or 5,000 with stored water, to zero. The wall result comes from reading the tile's stored-fluid bit directly, without an adjacency cache.

[Raw client measurements](fluid-render-presence/queries.csv)

## Regression coverage

`testNonRendering` compares 24,576 combinations against the old query: regular and Large Programmable Doors, ordinary trapdoors, all three diagonal trapdoor profiles, four facings, both halves, open/closed state, all 64 native-water adjacency masks and empty/water/lava storage. It also tests same-tick edits, unloaded boundaries, chunk loading/unloading, cache capacity, world unload and vehicle snapshot replacement.

The live fluid suite records real-client CPU/allocation samples and checks ordinary client-world water placement/removal. Existing render regressions compare 145 waterlogged-render cases with the reference renderer, plus the native-fluid contact, surface-height, underwater and solid-contact matrices. `verifyRelease` runs these checks against the same standard JAR as the navigation, glass and other release regressions.

To repeat the non-rendering checks, use Java 8 and run `./gradlew testNonRendering testDedicatedServerLinkage --no-daemon`. Run `bash testclient/test_viewscreen.sh --focus fluids` for the isolated client checks. Their output includes `fluid-presence-query-cost.csv`; external compatibility fixture mods must be available to the client harness.
