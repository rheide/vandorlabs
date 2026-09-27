# Vandor Labs 1.2 task tracker

Version stays at **1.2** for this entire task set. Finished JARs stay in `build/libs/`.

## Conversation request

- Add per-face texture overrides to Programmable Block and Programmable Slab.
- Off by default; explicit dialog toggle; show face controls only while enabled.
- Main texture remains the default; each face can inherit it or override it.
- Face choices are relative to the block facing. No additional block IDs or generated combinations.
- Copifier support: copying disabled overrides keeps them disabled and does not copy face textures.
- Commit incrementally and update this document throughout.

## Additional requests (verbatim)

- When copying diagonals with WorldEdit: if I `//copy` diagonals and then `//flip` and `//paste`, then the pasted blocks are at the wrong angle.
- Let's add a new Programmable Diagonal Porthole (wall) block that support both full-width and half-width options just like our current Programmable Diagonal Wall block. It should have the same geometry options (hexagonal, square, octagonal, round) as the existing porthole block, and the same joining behavior/option.
- Our diagonal walls currently have a full-width and half-width option, and they are implicitly full-height. Can we add an option for half-height-but-full-width? It should essentially be a rotated version of the half-width option. We want this to connect to other half-height-but-full-width diagonal walls.
- Landing gear wheels: I added ~/LLMShareDrive/MCTrek-landing-gear-models.zip that contains a bunch of landing gear wheels, let's add these in. Instructions are in the zip.
- Diagonal wall connecting behavior: can we add a dialog option that will make the diagonal wall connect/stretch/fill the remainder of the block, toggleable for either the inside or the outside of the diagonal? Should be similar to the filled ramp behavior we have for the Programmable Ramp.
- Let's add some seat blocks, details and files are in ~/LLMShareDrive/MCTrek-connected-seating-models.zip
- There seems to be one edge of the Programmable Ramp that has a 1px gap to the next block, can we detect/fill that?
- Programmable Blocks and Diagonal Blocks seem to not render from a distance, whereas regular minecraft blocks do. Is that something we can fix? If this is costly, just write down/report back what we should do rather than doing it.
- Copifier: could we add a recipe that combines a Copifier with any (stack of) Programmable Blocks, that then applies the copifier's copied properties to the stack as crafting output?
- Programmable Half Input: this doens't seem to be placeable on (Programmable) slabs, let's fix that. (only allow top half placement for top slabs, only allow bottom half placement for bottom slabs)
- Programmable Half Console: can we add a new block that is just the diagonal half part? It should be half a block tall and deep, and allow placement against slabs and blocks, both in top and bottom slab positions, and the diagonal should be angled downwards or upwards depending on top or bottom placement.
- I'm seeing a lot of "Space ... door" when I search for 'door' in creative mode. Those shouldn't be showing up there, we just want Programmable door.
- Programmable Doors aren't placeable on Programmable Slabs. Fix that.
- Destroying a Programmable Door that was placed on a slab caused a client crash (but not a server crash), see latest.log and debug.log logs in LLMShareDrive
- For the Programmable Block texture list, can we include the texture that we use for the inside of the frame for Programmable Glass and Programmable Door? The door should have two: a frame texture and a door texture that's slightly darker. I want both.


## Follow-up requests

- Another client crash: inspect `~/LLMShareDrive/debug2.log` and `latest2.log`.

## Status and findings

| Work | Status | Findings / validation |
| --- | --- | --- |
| Version / tracking | Done | Version 1.2 set in Gradle and mod metadata; baseline captured in `testclient/render-run.RA4YaV`. |
| Face overrides and Copifier | Done | Default-off behavior, inheritance, save/copy and responsive dialog verified in `render-run.OoewtY`. |
| WorldEdit diagonal flip | Done | WorldEdit actual transform tests cover all 8 metadata poses and X/Y/Z flips; diagonal wall and diagonal screen. |
| Diagonal porthole | Done | Four shapes, full/half width, lateral coplanar joining. Vertically adjacent slopes are not coplanar and do not join. |
| Diagonal half height | Done | Full-depth incline within eight pixels of height; upper/lower positions; matching straight runs. |
| Landing gear | Done | Four static gear designs; telescopic gear animates over one second, checks obstruction, reserves its lower cell, responds to redstone/right-click. |
| Diagonal inside/outside fill | Done | Independent inside/outside toggles, saved/copied with diagonal geometry; collision follows fill. |
| Connected seating | Done | Two styles with automatic single/end/middle variants, reserved backrest cell, sitting and cleanup. |
| Ramp edge gap | Done | User identified controller cube. Front/right-edge PNG alpha creates the seam; atlas loader repairs all 24 faces across both packs. Pixel regression passed (17,224 cutoff pixels), preserving solid RGB. |
| Distance rendering | Report complete | `docs/performance/render-distance-1.2.md`: full block/slab already use chunk meshes; diagonals inherit a 64-block tile cutoff. Recommended chunk-model work documented; no costly range increase. |
| Copifier crafting | Done | Shapeless tool + programmable item; tool retained. Normal crafting consumption processes stacks with shift-click; output respects selected copy settings. |
| Half Input slab placement | Done | Side placement matches the support slab half, including its wall slot. |
| Diagonal Half Console | Done | Half-height/depth incline; upper/lower placement matching slabs; configurable input artwork. |
| Creative door cleanup | Done | 42 legacy catalog entries hidden; registrations retained for existing worlds. |
| Door slab support | Done | Both slab halves accepted by door placement/support checks. |
| Client crash | Done | Mutable custom-sprite frame lists; load/mipmap/clear check and full live suite passed. Full external TextureFix pack not installed in test client. |
| Extra housing textures | Done | Added existing glass frame interior and door interior with actual 0xA8 tint; appended indices preserve saved choices. |
| Final validation / docs | Done | Java 8 build, original-texture packaging, final live runtime checks and pixel analyzers passed (`render-run.OoewtY`); illustrated guide and local documentation links checked. |

### Validation follow-ups

- Second crash logs: `latest2.log:23695` identifies build t49; its crash report lists version 1.1. `debug2.log:184139` and `184709` repeat TextureFix unloading an immutable frame list (`AbstractList.clear`). Covered by the mutable-frame-list fix and sprite cleanup regression in 1.2; the full external modpack remains outside the isolated suite.
- Second live run (`render-run.gtA6dE`) passed new runtime assertions but failed the model-loading gate. Corrected telescopic gear blockstate property ordering; final rerun required. Enabled face dialog now fits the test viewport.

- Final candidate (`render-run.OoewtY`): all runtime assertions passed, including new diagonal porthole join eligibility and slab-matched half-console placement. New screenshots visually checked: face overrides, joined seats, gear endpoints, four joined porthole shapes, half-height runs, fill modes, half consoles, and controller against stone. No missing-model errors. Remaining gallery/GUI capture and pixel analyzers are still running.
- Java 8 build passed; the standard JAR reports 1.2. Packaged bytecode/assets match the live client's JAR.
- Baseline comparison: the existing wall corner in `shot_programmable_corner_pair_top.png`, rectangle `(545,170)-(670,495)`, has zero pixels differing by more than 3/255. Whole-frame differences include moving mobs and the intended controller texture repair.


### Completed validation

- Final candidate completed normally at 15:26. All live pixel analyzers and runtime gates passed, including the missing-model gate. The launching shell ended early while its client continued; after normal client shutdown the remaining checks from `test_viewscreen.sh` were run directly against the same output directory. Evidence: `/tmp/vandorlabs-1.2-final.log` and `testclient/render-run.OoewtY/client.log`.
- Reviewed default/enabled face dialogs and diagonal shape/fill dialog at 1280x720. Exported the new scenes to `docs/images/gallery/v1.2/`; kept unrelated existing gallery images to avoid timestamp/animation churn.
- `build/libs/vandorlabs-1.2.jar` is the release artifact. Full live rendering used its default textures.
- All requested implementation tasks are complete. Distance rendering is report-only as authorized. Remaining acceptance is visual review in the owner's world/modpack; the isolated client does not include that full modpack.

## Follow-up batch (version remains 1.2)

- [ ] Add Programmable Stairs with the block/slab texture options, including optional face overrides.
- [ ] Explain and improve Programmable Door animation on slow servers; compare ramp timing.
- [ ] Fix Better Builder's Wands `/wandOops` undo for restored diagonal states.
- [ ] Support Immersive Engineering slabs as ramp material (inspect installed mod read-only).
- [ ] Make ramp selection distinguish programmable finishes; verify behavior.

Initial findings: door easing uses synchronized world time; BBW undo compares exact state strings after our orientation fix; IE slabs use `TileEntityIESlab`; ramp flood fill compares only IBlockState.

- [ ] Join half-width diagonal portholes stacked into one continuous slope (reversed upper piece).
- [ ] Add half-height, full-width diagonal portholes.

- [ ] Fix inside/outside fill on joined diagonal corners: retain arms and clip fill to the corner.


Follow-up implementation checkpoint: stairs use vanilla corner/collision geometry with cached per-face retexturing; door visuals use a monotonic paused-aware client clock; BBW undo retains restored state groups; IE slab NBT is preserved and its half matched; ramp discovery compares effective face textures. Stacked portholes now compare actual surface planes, and the half-height option is enabled. Corner fill retains arms and clips to the joined span. Java 8 build passed; expanded live validation is running with the read-only source IE JAR copied into the isolated test client.

- [ ] Combine two Programmable Slabs into a Programmable Block, preserving placed settings.

Live follow-up checks passed in `render-run.toeUYM`: upper/lower slab combining, stair metadata/corners/configured drops, effective ramp texture matching, real IE slab deploy/recover for each slab type, stacked porthole joins in four directions and shapes, half-height collision, filled-corner bounds, and the actual BBW undo command. The first attempt needed Trove 3 on the minimal client's classpath; the restarted client runs successfully. Visual capture and analyzers remain in progress.

Door timing scope: visual transitions now complete in 9 client visual ticks (450 ms) after receipt of an open/closed change, independent of server world-time corrections. Pausing an integrated game freezes the visual clock. This improves smoothness, not server response latency or model draw cost; ramp motion remains tied to server collision time.

- [ ] Luxury and Military Seats: default legs +1 pixel; creative shift-right-click Join toggle and height −2/default/+2 pixels; keep model, collision, rider height and saved settings consistent.

- [x] Remove original-textures packaging workflow and extra JAR; build only the standard JAR (owner instruction).
- [ ] Replace all old landing gear IDs with `small_landing_gear` (Small Landing Gear) and `large_landing_gear` (Large Landing Gear), no remaps. Both telescopic; fix retraction, add creative shift-right-click redstone Disabled/On/Off, channel and 0–4 block extension slider.

### Seat and landing gear implementation checkpoint

- Seats: implemented default +1px legs, Join On/Off and −2/default/+2px height. Mesh, collision, rider height, NBT, drops and copy settings share the selected height; unlike heights do not join.
- Gear: only `small_landing_gear` and `large_landing_gear` are registered. Both animate with preserved tile identity; extension is 0–64 pixels. Owned lower cells reserve the entire path and are released as retraction clears them. Added redstone modes/channel and validated configuration packet.
- Java 8 build and portable checks passed. Expanded live suite is running; final screenshots and GUI packet assertions still pending.
- The isolated suite now loads the installed Immersive Engineering JAR read-only for real slab coverage. Its known optional `revolver_einhorn.png` cosmetic texture is absent; only that exact texture report is exempted. All other texture/model errors still fail the suite.
