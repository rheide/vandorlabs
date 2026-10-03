# Animation validation

All **50 GIFs** passed the live-capture geometry and encoding checks: closed/open/closed state sequence, intermediate geometry in both directions, endpoint return, unclipped motion, 420×350 dimensions, animated frames, continuous loop and exact loop duration. The finished GIFs and decoded contact sheets were visually reviewed; edge-on patch cameras and the obstructed neighbor close-up were replaced before acceptance.

| Group | Clips | Loop |
| --- | --- | --- |
| Programmable Door | 4 | 2.6 seconds |
| Normal trapdoors, offset mounts and neighbor clearance | 7 | 2.6 seconds |
| Diagonal trapdoors, all shapes/motions and group layouts | 31 | 2.6 seconds |
| Programmable Ramp modes and slope/tread examples | 8 | 4.6 seconds |

The output uses a 50 ms timeline with the nearest actual captured pose. Capture medians range from **49.92 to 50.06 ms**; the largest frame gap across the selected clips is **63.42 ms**. The earlier pilot had gaps of roughly 0.6 seconds. Buffering PNG work outside motion, using G1 for this capture client and reducing the software-rendered view distance removed those long gaps in the final clips. Animation clocks and gameplay speeds remain unchanged.

The fifty files total **6,128,893 bytes** (5.84 MiB). Individual GIFs use a common 256-color palette per clip and merge repeated poses into longer frames. Frame counts and byte sizes are recorded in [the machine-readable results](animation-validation.json).

## Evidence

- Initial complete capture: `testclient/door-animation.RTyHwz`.
- Refined patch/neighbor cameras and ramp pit floors: `testclient/door-animation.tOuab1` (17 clips replace their initial captures).
- Joined Slide into wall for all three shapes: `testclient/door-animation.bEdSiC` (three clips).
- Java 8 standard build and non-rendering checks passed. The focused live dialog suite passed with sixteen requested dialog captures in `testclient/render-run.0hQq8F`. Capture clients passed item/model, materials, storage, redstone, controllers, copying and landing gear save/load contracts before recording.
- Landing gear checks verify metadata/NBT restoration, manual extension, a manual retraction on a restored powered channel, unload preservation and subsequent power edges across three sizes.
- The Diagonal Screen uses the shared wedge mesh and was inspected in the live hotbar alongside a placed screen.

The launchers record the built JAR checksum and source commit in each raw capture directory. Raw PNGs/logs stay ignored; this report and the GIFs are committed. These are software-rendered documentation/runtime checks and do not measure hardware FPS or shader appearance.
