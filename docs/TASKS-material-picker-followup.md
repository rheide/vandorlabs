# Material picker follow-up

Requested 2026-10-02. Preserve existing saved material identifiers and animated screen behavior.

## Requested tasks (verbatim)

- [x] Texture thumbnails are too small, should definitely be at least twice as large. Fine for the text size to increase as well
- [x] Door textures on a block: this stretches and looks terrible, we should pick top or bottom, don't care which.
- [x] Light textures: they're included in the list, but only the 'on' texture, let's include the 'off' ones as well
- [x] Let's organize the categories and textures alphabetically
- [x] Can we always keep the Category line in view even when we scroll down?
- [x] Custom texture: doesn't work when giving it a programmable block - we should read the actually used texture of the item for that
- [x] Back and Use texture buttons should be swapped
- [x] Programmable Light dialog: can't quite read "Light level" line due to overlap with the list
- [x] For the Programmable screen/console/input blocks, can we use the categorized texture list for the screens list as well? It should contain the same screen options now (but we obviously still want to keep the animation controls and behavior as before)
- [x] The 7 propulsion blocks still show a separate Preview area for the side texture, we can remove that now
- [x] Door textures in the preview thumbnail are stretched, let's show these with correct aspect ratio
- [x] Programmable Lights: the Size option was only meant for the Frame, not the slab or block
- [x] Programmable Doors: for when we pass a custom block as a texture, we should have an option to tile or fit. Also, the side of the door shows the same custom texture, but we should show the standard side texture we use for our own standard programmable doors
- [x] Programmable Half-input: I'm still not seeing a Tile/fit option for the wall texture like I asked for in the original tasks list

## Validation and findings

Initial checkout clean at `ccfd062c`. Read README, project guidance and unified material documentation. Implement and commit in slices, then build the standard Java 8 JAR and run the live client suite.

First slice: 16px thumbnails with aspect-preserving door previews, alphabetic display ordering without renumbering saved choices, pinned current-category heading, explicit Off light choices appended to the catalog, configured programmable item material sampling, swapped Custom buttons, light controls clear of the reduced visible list rows, Frame-only sizing, and wider propulsion material list without the separate preview. Java 8 compilation passes; live acceptance remains pending.

Second slice: native screen/control selectors reuse the categorized picker without changing animation families or mode/speed/frame controls. Direct housing Fit/Tile controls are visible in display dialogs, including Half-Input. Ordinary blocks use a square lower door half; door thumbnails and leaves retain full art. Replacement door faces offer Fit/Tile and retain native edges/hardware. Door layout persists in NBT, configured items, paired updates and Duplifier copies. Added atlas-bound/coverage/save tests and close-up scenes. Java 8 build, non-rendering and filesystem checks pass; final live GUI/renderer acceptance is pending.
