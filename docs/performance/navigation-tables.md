# Navigation table rendering

Joined tables render one cached map surface from their drawing owner. Physical
map scale and button size stay fixed as modules are added. Layout planes and
visibility bounds are retained, with no per-frame member scans or image uploads.
Player markers and loading transitions use a separate lightweight overlay.
Pending map edges pulse dark grey while the previous image pans or scales.
The recenter control uses a drawn crosshair icon.

## Focused measurements

The `navigation-performance` live focus renders the actual table TESR into a
512 × 512 framebuffer. Each case uses 12 warm-up draws and 31 measured draws.
CPU values measure submission; frame values include `glFinish`. Allocation is
measured on the client render thread. Both runs used Java 8, Forge 1.12.2 and the
same software-renderer fixture, with the benchmark run exclusively. These are
relative software measurements; they do not establish hardware FPS or shader
pack acceptance.

| Assembly | Median CPU, ms before → after | Allocated bytes before → after | Median finished draw, ms before → after |
| --- | --- | --- | --- |
| flat 1 × 1 | 0.033 → 0.062 | 1,464 → 1,792 | 0.220 → 0.288 |
| flat 4 × 4 | 0.091 → 0.047 | 29,728 → 1,792 | 0.328 → 0.246 |
| flat 8 × 8 | 0.355 → 0.048 | 429,216 → 1,792 | 0.624 → 0.270 |
| flat 16 × 16 | 2.660 → 0.073 | 6,960,288 → 1,760 | 3.028 → 0.408 |
| angled 1 × 1 | 0.029 → 0.040 | 1,464 → 1,760 | 0.202 → 0.232 |
| angled 4 × 1 | 0.044 → 0.043 | 3,360 → 1,760 | 0.246 → 0.232 |
| angled 8 × 1 | 0.057 → 0.051 | 8,352 → 1,760 | 0.267 → 0.256 |
| angled 16 × 1 | 0.086 → 0.049 | 29,728 → 1,760 | 0.320 → 0.266 |

The 256-module flat table reduces median CPU submission by about 36× and removes
almost all per-draw allocation. The richer controls, markers and loading background add a fixed cost
to small tables, whose prior submission was already inexpensive. Larger tables
benefit from drawing one surface and avoiding repeated member/layout work.

The fixture measures the map/controls path and shared chip holder; the new
separate module bay is not included in these draw timings.

Raw measurements: [before](navigation-tables/before.csv) and
[after](navigation-tables/after.csv).

Baseline JAR SHA-256:
`0c7d065a1c44c0694b834ff7c60e92ded87fea23d04e72e137a6f17cc02e4e96`.

Measured final JAR SHA-256:
`f0645ebd4f1b8a8fa9dba9113d8625ab6236159b8bd5f1ae59a1a57c59c416a7`.

## Related validation

Focused navigation tests cover fixed world scale, control hit mapping,
recenter-to-player/pin behavior, actual marker pixels, animation during pending
work, deep biome sampling without chunks, and cache reuse across displays and
return visits. Geographic sampling caches have no time expiry, retry errors,
and use bounded retained memory with world/generator invalidation.

The OptiFine navigation focus checks actual angled table housing/map depth at
12 approach poses, including large coordinates. Chip interactions, source
configuration, all observer formation facings, inverted geometry, and real
Extender/swing-arm handoffs are separate live regressions. These targeted
checks are not a complete release review.

The final carry/save-only patch uses JAR SHA-256
`17f543f1ecf563e02981a8f6cc8c4708e171b464e114bc4a93ca007c8450c098`.
Its rendering code and benchmark inputs are unchanged; validation adds saved
module-link/facing rotation and preservation of geographic payloads. The native
1.21.1 conversion is a separate adapter effort.

Coverage queries now use a bounded 1,024-block square-tile index. Full tiles
avoid exact-circle work; partial tiles retain block-exact circle tests. A
128-area sparse fixture performs 128 exact checks instead of up to 16,384
unindexed checks, with 20,133 portable assertions covering edges, negative
coordinates and the memory-budget fallback. Saved modules still use compact
circle triples. A full 1,024-area item is 12,422 NBT bytes; table updates send a
136-byte summary and keep coverage arrays out of ordinary tile packets.
