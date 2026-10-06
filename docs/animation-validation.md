# Animation validation

All **50 motion GIFs** passed the live-capture geometry and encoding checks: closed/open/closed state sequence, intermediate geometry in both directions, endpoint return, unclipped motion, 420×350 dimensions, animated frames, continuous loop and exact loop duration. The finished GIFs and decoded contact sheets were visually reviewed; camera framing covers edge-on patches and neighbor clearance.

| Group | Clips | Loop |
| --- | --- | --- |
| Programmable Door | 4 | 2.6 seconds |
| Normal trapdoors, offset mounts and neighbor clearance | 7 | 2.6 seconds |
| Diagonal trapdoors, all shapes/motions and group layouts | 31 | 2.6 seconds |
| Programmable Ramp modes and slope/tread examples | 8 | 4.6 seconds |
| Signal-level controls with connected consumers | 2 | 6 seconds |

The motion clips use a 50 ms timeline with the nearest actual captured pose. Capture medians range from **49.92 to 50.06 ms**; the largest frame gap across the selected clips is **63.42 ms**. The earlier pilot had gaps of roughly 0.6 seconds. Buffering PNG work outside motion, using G1 for this capture client and reducing the software-rendered view distance removed those long gaps in the final clips. Animation clocks and gameplay speeds remain unchanged.

The fifty motion files total **6,128,893 bytes** (5.84 MiB). Individual GIFs use a common 256-color palette per clip and merge repeated poses into longer frames. Frame counts and byte sizes are recorded in [the machine-readable results](animation-validation.json).

## Signal-level demonstrations

Two additional GIFs show a Thruster Lever controlling a Rocket Thruster and a
Wall Slider controlling a Programmable Light. Each records real clicks cycling
**0 → 5 → 10 → 15 → 0**, and checks that the consumer follows the control at every
level. The thruster's Medium-density particles activate at level 10. Both clips
use 420×350 images, a 100 ms output timeline and an exact six-second loop.

The clips contain 55 and 57 encoded frames from 110 captured frames each; their
combined size is 2,696,504 bytes. Decoded GIFs were reviewed for control movement,
consumer brightness, particle onset and framing. Their timing and size results
are included in the same machine-readable report.

## Evidence

- Java 8 standard build and non-rendering checks passed. The focused live dialog suite passed with sixteen dialog captures. Capture clients passed item/model, materials, storage, redstone, controllers, copying and landing gear save/load contracts before recording.
- Landing gear checks verify metadata/NBT restoration, manual extension, a manual retraction on a restored powered channel, unload preservation and subsequent power edges across three sizes.
- The Diagonal Screen uses the shared wedge mesh and was inspected in the live hotbar alongside a placed screen.

The launchers record the built JAR checksum and source commit in each raw capture directory. Raw PNGs/logs stay ignored; this report and the GIFs are committed. These are software-rendered documentation/runtime checks and do not measure hardware FPS or shader appearance.
