# Texture picker and trapdoor follow-ups — 3 October 2026

## Owner requests

- Make the wall, porthole, trapdoor and diagonal texture lists show more entries.
- Include Programmable Block and use two columns, with buttons beside the list.
- Preserve the clicked diagonal's orientation when placing another on top.
- Make opened diagonal trapdoors that slide over the wall easier to select.
- Remove Screens, Lights and Doors from general texture lists; retain each in its dedicated block family and retain Trapdoors in general lists.
- Add “Slide over surface” to regular programmable trapdoors.
- Fix the apparent overlapping button in trapdoor and Programmable Block dialogs; remove “Changes apply to the group” and place Done at the bottom of the right column.

## Implemented

- Responsive two-column wall/block/slab/storage and trapdoor dialogs use the available height. At the live client's 240-pixel GUI height the block picker shows seven rows, versus four before; taller GUI sizes show up to twelve.
- General pickers filter the three dedicated categories. Dedicated pickers retain general materials plus their own category. Existing saved texture choices remain valid.
- Stacked diagonal placement preserves the clicked orientation when a direct continuation is unavailable; side-click placement keeps its previous convention.
- Off-cell selection searches for diagonal leaves and traces their actual slanted surfaces, including leaves displaced across both horizontal axes.
- Regular trapdoors have an additional surface-slide movement: lift above the neighbouring full block's top face, then slide. Old movement settings retain their behaviour. Saved items, group settings, network packets, Duplifier options and Dynmap carry the new setting.
- Added regression coverage for accepted repeated diagonal planes, clipping, open-leaf selection, stacked orientation, and regular surface-slide clearance/persistence/copying.

## Validation

- Java 8 standard build and non-rendering tests passed, including 525,974 diagonal trapdoor assertions plus repeated-plane and neighbour-clipping checks.
- Focused live suite passed: `testclient/render-run.H5EBpB`, with 21 gallery captures, saved-selection reopening, all five movement options, server/client surface-slide settings, and mouse-selection checks.
- Inspected block/slab and trapdoor GUI screenshots. The new option fits the sidebar and its channel field stays above Done.
- The live check caught a missing surface-style argument in the GUI packet; fixed it and reran the suite successfully.
- Final standard artifact: `build/libs/vandorlabs-1.3.jar` (SHA-256 `9af415a167f57d29893986457bb43af115dd48dca5e29e9b8b87d0f1de392477`).
- Full-gallery pixel analyzers and hardware shader acceptance were outside this focused run.
- Initial live screenshots confirm the taller block/slab layouts. The previous live GUI motion check needed one more button press because of the new movement option; updated to check all five options explicitly.

## Dialog button follow-up

- Owner screenshot `~/LLMShareDrive/dialog.png` shows the Done button sampling outside Minecraft's 200-pixel button texture because the button spans the enlarged dialog. Changed shared wall/block/slab/storage and regular/diagonal trapdoor Done buttons to 154 pixels, bottom-aligned in the right column.
- Removed “Changes apply to the group” and used the freed space for an additional trapdoor texture row.
- Applied the shared responsive layout to Door, Light, Screen, Input, Console, Half Console, Trigger and Thruster pickers too. Multiple texture targets use tabs, with full-height lists and sidebar controls. Static surface overrides use the same arrangement; light settings no longer change the owner's GUI scale.
- Added live checks for button texture width, viewport bounds, control/list/channel separation and actual tab clicks at 320x240, 460x340 and the client viewport.
- Java 8 standard build and `bash testclient/test_viewscreen.sh --focus dialogs` passed. Artifacts: `testclient/render-run.eBDEwk`; all nine texture dialog classes passed viewport/overlap/tab checks and fifteen fresh GUI captures passed validation. Inspected console, input, light, door, diagonal trapdoor and block screenshots. Saved Custom selections still reopen correctly for block, slab, door and trapdoor.
- Standard artifact: `build/libs/vandorlabs-1.3.jar`, SHA-256 `9cf5bfd86d316113953a478757cf0170644b05bc9bb5ed3bcb912a87a5c62da1`.
- Two generic gallery attempts hit unrelated gallery fixture setup failures before reaching GUI checks. Added the focused dialogs mode to test actual dialog fixtures directly. The final focused run passed; hardware shader visuals remain outside this UI validation.

## Face, screen-category and side-texture refinements

- Face Overrides now exposes Main and six individual face buttons across both columns. Slabs also expose Side in that row.
- Removed the separate Static Face selector. Screen and Controls each use the standard categorized material picker, plus an expandable category for their own artwork. Screens contains full-height artwork; Controls contains half-height input artwork. Housing and general block pickers exclude both artwork groups. Display, animation, speed, framing and redstone controls remain available. Native artwork retains its animation identity; material choices use the existing saved face override internally.
- Portholes (wall, diagonal and full-block), slabs and stairs have independent Main and Side texture choices. Porthole sides default to their existing metal rim; slab/stair sides inherit Main. The default-side button clears an override. Save data, client updates, configured items and Duplifier settings preserve the separate choice. Explicit per-face overrides take precedence.
- Added live assertions for artwork-category height separation, Main/Side tab selection, side save/legacy/copy/reset behavior, and top/bottom versus side sprites on both slab halves.
- First updated focused live run passed all nine dialog classes and sixteen fresh captures: `testclient/render-run.2uB4Gg`. Inspected Console, Controls and Porthole screenshots. Final rerun includes the slab sprite assertions and frame-selection highlight refresh.
- Final Java 8 standard build and focused live suite passed: `testclient/render-run.E6vx2G`, all nine dialog classes, artwork-category assertions, both slab-half sprite checks, persistence/copy checks and sixteen fresh GUI captures. Inspected the face-button row as well. `git diff --check` and packaged class inspection passed; the removed Static Face component is absent from the JAR.
- Final standard artifact: `build/libs/vandorlabs-1.3.jar`, SHA-256 `ff5ea3de553eca37b64cf8ac617292c6a2ee5f912b8f143fb7119ddf9a59e66f`.
