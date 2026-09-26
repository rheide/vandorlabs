# Further rendering work

## Request (2026-09-26)

> Sounds good, implement these. Commit what you have so far first.

> When you're at 80% quota left, find a good stopping point and leave it there.

The previous work was clean and committed through `0f10158d` before this pass.
Quota was 100% at the start. Stop at a tested commit no later than 80%.

## Before-state coverage

- [x] Capture and commit benchmark variants for 8x8 floors and 4x4x4 volumes
  of programmable blocks/slabs and vanilla stone/slabs.
- [x] Capture and commit closed/open programmable and iron-door fixtures.
- [x] Run baseline Java 8 build and live benchmark before changing production rendering.

## Implementation

- [x] Move programmable blocks and slabs to cached chunk geometry.
- [x] Cull hidden full faces while preserving partial slab faces.
- [x] Remove no-op tile renderer submissions for baked blocks/slabs.
- [x] Avoid submitting the unused upper tile of every two-block door.
- [x] Cache the four immutable door motion model lookups.
- [ ] Reduce programmable door stationary and moving rendering costs further.
- [x] Run final full live suite and compare before/after images and performance.

Report limits and any remaining work at the requested quota threshold.

Baseline: Java 8 Gradle build PASS; benchmark PASS in
`testclient/render-benchmark.LRWJiK`. The 36-image self-comparison passed.
CSV and six representative PNGs are retained in `docs/performance/next/`.
The full baseline JAR is in `build/libs/vandorlabs-pre-chunk-bake.jar`.
64-case medians: stone floor 0.0268 ms / volume 0.0137 ms; programmable
block floor 0.1624 ms / volume 0.1191 ms. Vanilla slab floor 0.0575 ms /
volume 0.0373 ms; programmable slab floor 0.1769 ms / volume 0.1484 ms.
Open iron door 0.0523 ms; open programmable door 1.129 ms. These are
synthetic submission times, not FPS.

Door resource-cache slice: Java 8 build PASS; live benchmark PASS in
`testclient/render-benchmark.2vGIU0`; all 36 baseline images match. An initial
shortcut around Minecraft's item rendering changed door pixels, so the opaque
path retains the standard renderer while reusing stable stacks. Glass models
are cached and cleared when models reload. The first altered run is excluded.

Block/slab chunk geometry: live benchmark PASS in
`testclient/render-benchmark.VbEemt`; all 36 baseline images match within the
3/255 tolerance. The full `testclient/test_viewscreen.sh` suite passed in
`testclient/render-run.pgAphy`. Programmable block floor/volume submission
medians fell from 0.1624/0.1191 ms to 0.0437/0.0271 ms. Programmable slab
floor/volume medians fell from 0.1769/0.1484 ms to 0.0339/0.0299 ms.
Dense 4x4x4 block geometry is 384 vertices, matching vanilla stone; slabs
use 768 vertices. These are synthetic submission times, not FPS.

Door frame before-state: `testclient/render-benchmark.MCgQ3i` passed with
41 static images (including glass, sliding, center placement, no hinges, and
paired doors). The door images and complete timing CSV are in `next/`.

The door benchmark now includes both half tiles and honors the pass-0 render
filter, matching ordinary world dispatch. Before changing the upper-half
filter, `testclient/render-benchmark.xZmRaI` passed all 41 image comparisons
against the earlier fixture; its CSV is `next/door-upper-before.csv`.

Rejected door experiments: baking the frame into terrain changed roughly 4%
of door fixture pixels after matching its item tint, due to differences between
the item and terrain render paths. Reusing opaque item VBOs and combining a
closed frame and leaf preserved all 41 images but gave no reliable submission
gain in this benchmark. Those experimental changes were removed.

Upper tile filter: `testclient/render-benchmark.WR8NLZ` passed the 41-image
comparison against `xZmRaI`. For 64 closed default doors, median submission
fell from 1.256 to 1.130 ms and median allocation from 219,168 to 185,888
bytes. The benchmark includes 128 candidate tiles for 64 two-block doors;
the upper 64 no longer reach the renderer. Other variants showed smaller or
noisy timing changes. The full live suite passed in `testclient/render-run.JR082q`.

Door motion model lookup cache: `testclient/render-benchmark.SudWW7` matched
all 41 images. For 64 default doors, median allocation fell from 185,888 to
161,312 bytes per measured submission; median submission time was 1.130 vs
1.155 ms, within run-to-run noise. The four registry models are resolved once
and reused. A first benchmark launch crashed in vanilla's concurrent chunk
packet iteration before measurement; the retry passed. Final live suite is
PASS in `testclient/render-run.hFvzt6` for this small cache change.

Baked housing renderer filter: `testclient/render-benchmark.3C80oM` matched
all 41 images against `SudWW7`. Programmable Block default/floor/volume
median submissions fell from 0.0601/0.0520/0.0318 to
0.0445/0.0362/0.0212 ms for 64 blocks. Slab floor/volume fell from
0.0313/0.0369 to 0.0261/0.0243 ms. Zero tile renderers are submitted for
these cases; Programmable Trigger keeps its tile renderer. Final full live
suite passed in `testclient/render-run.j0sGtn` for this filter.

The tested change set is committed through `b99893de` with a clean worktree.
The final quota check showed 96% remaining, above the requested 80% stop limit.
Further door frame batching remains a follow-up because the tested terrain
approach changed the rendered image and the item VBO approach showed no
reliable timing gain.
