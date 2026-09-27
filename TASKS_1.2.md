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


## Status and findings

| Work | Status | Findings / validation |
| --- | --- | --- |
| Version / tracking | Done | Version 1.2 set in Gradle and mod metadata; baseline captured in `testclient/render-run.RA4YaV`. |
| Face overrides and Copifier | Live suite passed; layout follow-up | `render-run.AlF3qw`: runtime checks passed. Screenshot exposed tall enabled dialog; responsive list/preview added for next run. |
| WorldEdit diagonal flip | Live suite passed | WorldEdit actual transform tests cover all 8 metadata poses and X/Y/Z flips; diagonal wall and diagonal screen. |
| Diagonal porthole | Implemented, validating | Four shapes, full/half width, lateral coplanar joining. Vertically adjacent slopes are not coplanar and do not join. |
| Diagonal half height | Implemented, validating | Full-depth incline within eight pixels of height; upper/lower positions; matching straight runs. |
| Landing gear | Implemented, validating | Four static gear designs; telescopic gear animates over one second, checks obstruction, reserves its lower cell, responds to redstone/right-click. |
| Diagonal inside/outside fill | Implemented, validating | Independent inside/outside toggles, saved/copied with diagonal geometry; collision follows fill. |
| Connected seating | Implemented, validating | Two styles with automatic single/end/middle variants, reserved backrest cell, sitting and cleanup. |
| Ramp edge gap | Pending | Reproduce and inspect geometry. |
| Distance rendering | Pending | Investigate and record cost before changing. |
| Copifier crafting | Implemented, validating | Shapeless tool + programmable item; tool retained. Normal crafting consumption processes stacks with shift-click; output respects selected copy settings. |
| Half Input slab placement | Live suite passed | Side placement matches the support slab half, including its wall slot. |
| Diagonal Half Console | Implemented, validating | Half-height/depth incline; upper/lower placement matching slabs; configurable input artwork. |
| Creative door cleanup | Implemented, validating | 42 legacy catalog entries hidden; registrations retained for existing worlds. |
| Door slab support | Live suite passed | Both slab halves accepted by door placement/support checks. |
| Client crash | Regression passed | Mutable custom-sprite frame lists; load/mipmap/clear check and full live suite passed. Full external TextureFix pack not installed in test client. |
| Extra housing textures | Implemented, validating | Added existing glass frame interior and door interior with actual 0xA8 tint; appended indices preserve saved choices. |
| Final validation / docs | In progress | Baseline and first 1.2 live suite passed. New asset/geometry tests and eight new screenshot scenes added; final suite pending. |
