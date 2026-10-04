# Existing-world rendering comparison

The same saved structure and companion mods were loaded with release 1.3
(`9c433a63`), the original 1.4 alpha (`2d18b157`) and the merged/boundary diagonal
renderer (`f431cb30`). Each revision received the same opt-in measurement
harness. The latest revision also includes the Programmable Wall distance fix.

The measured area contains 495 diagonal walls, 942 programmable blocks,
696 slabs, 219 plain walls, 48 diagonal portholes and 74 lights, alongside other
blocks and entities. Registry counts match across all three versions. The save's
existing copied controller state was retained rather than repaired between runs.

## Render-thread CPU time

Median milliseconds per rendered frame, after settling each camera:

| View | 1.3 | First 1.4 alpha | Merged/boundary renderer |
| --- | ---: | ---: | ---: |
| East wall | 15.184 | 14.872 | 12.753 |
| West wall | 19.181 | 18.858 | 16.295 |
| Overview | 13.637 | 13.362 | 11.568 |

The latest measurements are about 15–16% lower than 1.3 and 13–14% lower than
the original 1.4 alpha. These are software-renderer CPU results, not predictions
of hardware FPS. The overview also includes the cost of plain walls that now
remain visible beyond their former tile distance cutoff.

## Render-thread allocation

Median bytes per rendered frame:

| View | 1.3 | First 1.4 alpha | Merged/boundary renderer |
| --- | ---: | ---: | ---: |
| East wall | 2,558,888 | 1,822,496 | 1,444,904 |
| West wall | 2,573,080 | 1,837,520 | 1,454,976 |
| Overview | 2,547,288 | 1,763,424 | 1,476,376 |

Allocation is approximately 42–44% lower than 1.3 and 16–21% lower than the first
1.4 alpha. These totals include rendering by the other installed mods.

## Method and limits

Each fixed camera samples 240 rendered frames after at least 15 seconds and
90 warm-up frames. Render distance, camera, companion mods and graphics settings
are held constant. OptiFine is enabled and shaders are disabled. The launcher
uses software rendering; no hardware shader acceptance is implied.

The CSV records submission/completed wall time, render-thread CPU/allocation and
the integrated server's mean tick time separately. Server means remain near one
millisecond in this scene; this does not characterize heavy redstone changes,
chunk loading or active machinery. Baselines have one run each, so small timing
differences should not be treated as statistically established improvements.

The latest cameras are also repeated after an F3+A-style renderer reload.
The structure remains visually consistent; this check has not reproduced the
reported intermittent missing-wall condition. Loading/rebuild spikes and
hardware GPU costs require separate testing.

See [raw measurements](world-render.csv), including the intermediate merged
renderer and reload repetitions, and the
[reproduction procedure](../../../testclient/benchmark-world.md). The world and
its screenshots are external test data and are not included in the repository.
