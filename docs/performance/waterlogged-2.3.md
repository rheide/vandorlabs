# Waterlogged rendering in 2.3

The shared contained-fluid renderer batches both sides into one draw, reuses the fluid descriptions already stored with its validated surface mesh, and emits triangulated faces without temporary vertex arrays. Empty meshes skip rendering setup. Vertex order, dynamic lighting, fluid tint, atlas animation and connection geometry are preserved.

## Measurements

The live Minecraft 1.12.2 / Forge client compared the pre-optimization renderer from `fed903e0` with the optimized renderer in the same OpenGL context. Each fixture warmed both paths for 1,000 renders, then alternated nine batches of 1,024 renders per path. The table shows median elapsed microseconds per render, including a GPU completion fence. Fixtures use a 128 by 128 framebuffer and mixed water/lava compartments where available. Hardware: AMD Radeon Graphics; Java 8.

Across one instance of each family, the sum of these medians decreased by 11.3%. This is an isolated fluid-rendering benchmark, not an overall gameplay FPS measurement. The 2% slab result is small enough to treat cautiously; the larger short-run anomalies disappeared with the longer warm-up and samples.

| Programmable block | Previous (us) | Batched (us) | Reduction | Allocated bytes, previous / batched |
| --- | ---: | ---: | ---: | ---: |
| Slab | 8.12 | 7.96 | 2.0% | 1448 / 1400 |
| Stairs | 6.81 | 5.93 | 12.9% | 1008 / 921 |
| Wall | 13.20 | 11.61 | 12.1% | 2648 / 2600 |
| Porthole Wall | 12.70 | 11.97 | 5.8% | 2648 / 2600 |
| Glass | 13.35 | 11.75 | 12.0% | 2648 / 2600 |
| Diagonal Wall | 12.97 | 10.72 | 17.4% | 2488 / 2312 |
| Diagonal Porthole | 11.60 | 10.48 | 9.6% | 2424 / 2312 |
| Diagonal Glass | 11.60 | 9.94 | 14.3% | 2424 / 2312 |
| Door | 9.91 | 8.72 | 12.0% | 1208 / 1160 |
| Trapdoor | 11.34 | 9.68 | 14.6% | 2192 / 2144 |
| Diagonal Trapdoor | 11.19 | 10.09 | 9.8% | 2424 / 2312 |

[Raw measurements](waterlogged-2.3.csv) include allocation and thread CPU counters. The Windows thread CPU counter is quantized at about 15.6 ms per batch in this run, so its per-render values are too coarse to support a CPU-time comparison. Elapsed measurements above use the high-resolution timer.

## Release regression coverage

`verifyRelease` runs the live comparison automatically as part of the gameplay/fluid suite. The frozen reference renderer is retained in `WaterloggedRenderChecks`. The suite requires exact rendered-pixel equality for 145 cases: all eleven block families, dry/either-side/both-side/mixed-fluid states, all three diagonal wall geometries and both inversions, and open/closed doors and trapdoors. It also verifies cached fluid data after filling and draining.

The release gate additionally covers:

- 1,878 eye-immersion and viewpoint checks, including the exact open-door center, four facings, three placement depths, and eyes below/at/above the dry upper half.
- The transition water -> open door -> water -> one-sided porthole -> air, using the actual camera state, fog color and density.
- 15,552 native-fluid contact cases for source/flowing water and lava, plus the existing diagonal join, visible frame, still-artwork, corner-height and submerged-glass checks.

The remaining per-frame work includes dynamic light queries, biome tint lookup and vertex submission. This change does not move rendering to another thread or change the transparency pipeline. Further changes to chunk batching or GPU-resident meshes should be driven by profiling large scenes and retain these visual regressions.
