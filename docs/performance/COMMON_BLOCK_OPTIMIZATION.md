# Common programmable block optimization

Static diagonal-wall and replacement-door surfaces use bounded geometry caches, while identical programmable settings avoid redundant appearance updates. Landing gear and ramp-controlled blocks share the diagonal-wall render-distance policy. Measurements are relative CPU submission/allocation costs from a software renderer; hardware FPS and shader acceptance require separate validation.

## Implemented behavior

Diagonal-wall vertices, atlas UVs and normals are retained until the owning block, its immediate neighborhood, tile geometry, chunk lifecycle or resource models change. Each client world has an LRU limit of 4,096 meshes and 524,288 vertices (16 MiB of float payload). Small invalidation ranges inspect at most 125 candidate owners rather than scanning the full cache during bulk geometry edits. Entries hold weak tile references; world unload and model baking detach/clear the cache. Integrated-server chunk events are ignored by this client cache.

Lightmap values and shader vertex packing remain live. Meshes submit through the active `BufferBuilder` format rather than copying packed buffers, preserving explicit normals and allowing shader integrations to generate their attributes. No geometry or UV change is intended.

Programmable Doors retain their existing draw calls and animation transforms. Replacement-material leaf vertices and the native model's filtered quads are reused. Resource model baking clears both model and material caches. Default doors keep their previous rendering path.

Settings setters track actual configuration changes. The GUI sync handler and Duplifier skip their extra block notification when nothing changed, including clamped values. The Duplifier still reports an applicable unchanged copy as successful, preserves disabled face choices and uses the current diagonal-geometry tag instead of applying the redundant legacy width field first.

Landing gear and ramp-controlled cells now return the same `Double.MAX_VALUE` tile distance as diagonal walls. Normal loaded-chunk and frustum visibility still applies; no chunk loading is added. Gear reservation/animation bounds and ramp cell geometry are unchanged. More distant visible gear/ramp tiles can now draw, so this intentional visibility improvement can increase scene work.

## Validation evidence

The Java 8 build and `testNonRendering` pass. Checks compare 384 diagonal-wall fixtures, live lightmap changes, cache hits, nearby block/tile/packet changes, atlas replacement, adjacent chunk load/unload, world unload, resource clearing, LRU limits, door material vertices/model filtering, and no-op versus changed settings. Clipping matches the previous implementation on 2,000 deterministic cases. The standard packaged JAR includes rendering classes and registry data and excludes regression-test classes.

All 44 stationary benchmark fixture images match within 3/255 per channel for at least 99.99% of pixels. Four door fixtures carry animation state across cases, so their final pose depends on capture time; settled motion, hinge and glass appearance is covered by the full client gallery. The expanded benchmark covers Fit/Tile/Custom doors and filled/shallow/neighbor-clipped walls.

The full live suite passes door, gear, ramp, redstone, copying, all three material-dialog tabs and resource/model checks. Its distance fixture confirms one gear, 24 controlled ramp cells and four diagonal walls beyond 64 blocks. A daylight capture with a 12-chunk view distance and camera about 80 blocks away verifies visible gear, wall and ramp pixels after the scene settles. Hardware FPS and Complementary Unbound shader visuals remain unmeasured; retaining the shader submission path does not establish hardware acceptance.

## Measured result

Each live case has 15 warmups and 31 samples with software rendering. Timings measure CPU/GL submission for the named batch of 64 instances, with cached VBO terrain plus the actual TESRs. They exclude normal chunk streaming, scene visibility, GC pause attribution and hardware GPU/FPS behavior. [Raw before](common-blocks/before.csv), [first after](common-blocks/after-first.csv), and [repeat after](common-blocks/after-repeat.csv) include the full case list and vanilla controls.

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
