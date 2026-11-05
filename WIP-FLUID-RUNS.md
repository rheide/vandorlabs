# Experimental contained-fluid run kernel

This branch extracts the current longest-axis fluid run builder into a Java 8 generic kernel. Platform adapters create actual boxes and cells directly. World sampling, side mapping, cache ownership and deferred face construction stay in Forge. The historical Cartesian cell/eager-face implementation is not imported.

The extraction is **not accepted for master** because warmed allocation costs increased. A Java 8 profile with 20,000 warm-up builds and three 2,000-build samples measured:

| Implementation | Allocated bytes/build |
| --- | ---: |
| Current production implementation | 23,664 |
| Static generic prototype | 25,664 |
| Typed kernel retained here | 25,744 |

The representative fixture produces 120 compact cells without materializing faces. These allocation results are not frame-rate measurements. The extra allocation must be removed before integration; sharing code does not justify increasing the production cost.

All 26 actual volume fixtures retained ordered raw-double cell coordinates and the small-grid face sequence, with golden `b3433c8496835cde2bae2f3f635b0ec32b432b1a2927bae06f65342742c5d465`. The previous-grid ownership comparison remains 2,284,318 Cartesian cells versus 19,100 compact cells. Fourteen small cases materialize faces once; twelve large cases remain deferred. Tile cache identity, uncached worker isolation and next-tick cache reuse also remain unchanged.

The narrow comparison is `testFluidVolumeCompaction`; allocation measurement is enabled with `-PfluidVolumeProfile`. Native contained-fluid behavior is not implemented by this experiment.
