# Further rendering work

## Request (2026-09-26)

> Sounds good, implement these. Commit what you have so far first.

> When you're at 80% quota left, find a good stopping point and leave it there.

The previous work was clean and committed through `0f10158d` before this pass.
Quota was 100% at the start. Stop at a tested commit near 80%.

## Before-state coverage

- [x] Capture and commit benchmark variants for 8x8 floors and 4x4x4 volumes
  of programmable blocks/slabs and vanilla stone/slabs.
- [x] Capture and commit closed/open programmable and iron-door fixtures.
- [x] Run baseline Java 8 build and live benchmark before changing production rendering.

## Implementation

- [x] Move programmable blocks and slabs to cached chunk geometry.
- [x] Cull hidden full faces while preserving partial slab faces.
- [ ] Reduce programmable door stationary and moving rendering costs.
- [ ] Run full live suite and compare before/after images and performance.

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
