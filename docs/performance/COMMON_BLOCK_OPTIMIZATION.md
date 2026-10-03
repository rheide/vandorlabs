# Common programmable block optimization

User request: "Alright, start implementing the fixes you described in the plan"

Additional request: "One thing while you're in there: we changed the diagonal blocks so they appear from further away, but we haven't made that change for the landing gear or blocks affected by the pgroammable ramp, let's make those pop in at the same distance"

The accepted plan covers Programmable Diagonal Wall geometry caching, precomputed Programmable Door replacement-material geometry/model quads, and avoiding redundant appearance updates for Programmable Block. It also calls for unclipped surfaces to bypass polygon clipping. Opaque wall chunk models and combining door draw calls are later options to investigate after these changes have measurements and visual acceptance.

- [x] Give landing gear and ramp-controlled blocks the diagonal-wall render distance policy, retaining their frustum bounds.
- [x] Capture unchanged live-suite and expanded benchmark baselines.
- [x] Cache finished diagonal-wall geometry with bounded storage and invalidation for nearby blocks, tile settings, chunk boundaries, world unload and resource reload.
- [x] Preserve live lighting, explicit normals and shader-aware vertex submission.
- [x] Precompute filtered replacement-door quads and cache Fit/Tile material vertices while preserving existing animation, hinges and native leaf depth.
- [x] Skip clipping/copying for surfaces requiring no clipping.
- [x] Suppress appearance notifications when existing settings already match.
- [x] Verify byte-identical geometry, cache invalidation, settings behavior and the Java 8 build.
- [x] Run the live client suite and compare static before/after images.
- [x] Record repeatable CPU submission/allocation measurements and update the changelog.

Measurements here will be relative software-renderer/CPU submission results. Hardware FPS and Complementary shader acceptance remain separate validation.

## Implemented behavior

Diagonal-wall vertices, atlas UVs and normals are retained until the owning block, its immediate neighborhood, tile geometry, chunk lifecycle or resource models change. Each client world has an LRU limit of 4,096 meshes and 524,288 vertices (16 MiB of float payload). Small invalidation ranges inspect at most 125 candidate owners rather than scanning the full cache during bulk geometry edits. Entries hold weak tile references; world unload and model baking detach/clear the cache. Integrated-server chunk events are ignored by this client cache.

Lightmap values and shader vertex packing remain live. Meshes submit through the active `BufferBuilder` format rather than copying packed buffers, preserving explicit normals and allowing shader integrations to generate their attributes. No geometry or UV change is intended.

Programmable Doors retain their existing draw calls and animation transforms. Replacement-material leaf vertices and the native model's filtered quads are reused. Resource model baking clears both model and material caches. Default doors keep their previous rendering path.

Settings setters track actual configuration changes. The GUI sync handler and Duplifier skip their extra block notification when nothing changed, including clamped values. The Duplifier still reports an applicable unchanged copy as successful, preserves disabled face choices and uses the current diagonal-geometry tag instead of applying the redundant legacy width field first.

Landing gear and ramp-controlled cells now return the same `Double.MAX_VALUE` tile distance as diagonal walls. Normal loaded-chunk and frustum visibility still applies; no chunk loading is added. Gear reservation/animation bounds and ramp cell geometry are unchanged. More distant visible gear/ramp tiles can now draw, so this intentional visibility improvement can increase scene work.

## Validation evidence

- Original source endpoint: `d24338e4d1f2666a4429a1df88830e74a7fa0c6f`.
- Unchanged full client artifacts: `testclient/render-run.vmPZQn`. Gameplay markers and door/gear/light/diagonal image analyzers pass. The main analyzer failed because it still sampled the previous three-column console GUI. The current GUI already used one tabbed list; its visible screen thumbnails were present. Validation now captures and checks all three actual tabs.
- Expanded unchanged benchmark: `testclient/render-benchmark.b90zVT`, including Fit/Tile/Custom doors and filled/shallow/neighbor-clipped walls.
- Java 8 build and `testNonRendering` pass. Added checks compare 384 diagonal-wall fixtures, live lightmap changes, cache hits, nearby block/tile/packet changes, atlas replacement, adjacent chunk load/unload, world unload, resource clearing, LRU limits, door material vertices/model filtering, and no-op versus changed settings. Clipping matches the previous implementation on 2,000 deterministic cases.
- First completed post-fix benchmark: `testclient/render-benchmark.2k1Q3W`. All 44 stationary fixture images match within the existing 3/255, 99.99%-pixel threshold. Four door fixtures carry animation state across cases, so their final pose depends on wall-clock time; settled motion/hinge/glass appearance is covered by the full client gallery. The benchmark completed, then asynchronous shutdown allowed another tick to re-enter a probe; the lab now leaves its probe state before shutting down.
- Standard packaged JAR includes the new rendering classes and registry data, and excludes regression-test classes.

The full post-fix suite passed at `testclient/render-run.afIbPm`, including real door, gear, ramp, redstone, copying and resource/model checks. Its distance fixture confirms one gear, 24 controlled ramp cells and four diagonal walls beyond 64 blocks. The clean repeat benchmark passed at `testclient/render-benchmark.ed2xjH`. The focused daylight capture passed at `testclient/render-run.ykFhji`, with 12-chunk view distance, a nearby view to compile the scene, then the fixed camera about 80 blocks away. Gear, wall and ramp pixels are checked in the distant image and are visible on inspection. Early focused attempts reached the distance assertions before the newly generated scene was ready for useful screenshots; the fixture now settles chunks and both views for 100 ticks. Hardware FPS and Complementary Unbound shader visuals remain unmeasured; the cached format keeps the existing shader submission path but does not establish hardware acceptance.

## Measured result

Each live case has 15 warmups and 31 samples on Mesa llvmpipe. Timings measure CPU/GL submission for the named batch of 64 instances, with cached VBO terrain plus the actual TESRs. They exclude normal chunk streaming, scene visibility, GC pause attribution and hardware GPU/FPS behavior. [Raw before](common-blocks/before.csv), [first after](common-blocks/after-first.csv), and [repeat after](common-blocks/after-repeat.csv) include the full case list and vanilla controls.

| 64 instances | Before submission ms | After range ms (two runs) | Before / after allocation B |
| --- | ---: | ---: | ---: |
| Diagonal wall | 0.776 | 0.435–0.448 | 1,672,736 / 35,360 |
| Filled wall | 0.836 | 0.438–0.491 | 1,708,576 / 35,360 |
| Shallow wall | 0.757 | 0.427–0.432 | 1,665,568 / 35,360 |
| Clipped wall + neighbor cubes | 1.234 | 0.495–0.505 | 2,812,992 / 35,392 |
| Door material Fit | 1.287 | 1.131–1.243 | 255,008 / 154,144 |
| Door material Tile | 1.286 | 1.194–1.240 | 320,544 / 154,144 |
| Door material Custom | 1.438 | 1.179–1.236 | 448,544 / 154,144 |
| Default door | 1.129 | 1.135–1.160 | 161,312 / 161,312 |
| Programmable Block | 0.045 | 0.045–0.046 | 64 / 64 |
| Vanilla stone | 0.056 | 0.049–0.064 | 64 / 64 |

Wall allocation fell about 98–99% and submission about 41–60%. Replacement-door allocation fell 40–66%; timing improvements were smaller (roughly 3–18%) and should be interpreted cautiously given the control variation. Programmable Block retains its cached terrain path; its improvement applies to repeated identical settings/copy operations, for which the regression test verifies zero dirtying, appearance notifications or light checks. Default doors are unchanged within timing variation.

The repeat run's strict image comparator flags six stationary door captures with color differences of at most 6/255 per channel; all their foreground silhouettes are identical. This includes default doors, whose renderer is unchanged. Minecraft's lightmap contains randomized torch flicker, which the existing benchmark does not freeze; this is a likely source of the small color variation. The first before/after comparison passed the stricter 3/255 threshold for all 44 stationary fixtures, and submitted wall/material vertex bytes plus lightmap response match exactly in the buffer tests. No image threshold was relaxed to accept the repeat.
