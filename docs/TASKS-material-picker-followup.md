# Material picker follow-up

Requested 2026-10-02. Preserve existing saved material identifiers and animated screen behavior.

Status: complete. All fourteen changes are implemented, documented and live-validated.

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

First live follow-up exposed an obsolete picker assertion tied to numeric order and 12px rows. Replaced it with alphabetic selection, scrolling and pinned-category folding checks. Moved light option buttons above the Light level label to leave explicit clearance. Corrected live rerun follows.

Visual capture caught unsupported test doors falling out of the new Fit/Tile gallery scenes. Added supporting blocks to those scenes and the existing materials scene, plus a client capture-time assertion that the door and its selected material/layout remain present. Recapture is required before publishing these images.

Catalog performance: group and sort once per dialog instead of rescanning/sorting the catalog on each category toggle. Rendering visits only visible rows; thumbnails reuse atlas textures loaded at startup, rather than reading files on scroll. Very large catalogs can still affect startup/atlas memory and dialog creation; no texture-count limit or FPS claim is made.

The full live suite at `testclient/render-run.sUQUgP` passed every runtime marker and all six image analyzers. Reviewed light/propulsion/door/native screen dialogs: light level is clear, block lights have no Size control, propulsion has no separate preview, and native screen/controls/materials show categorized thumbnails. Half-Input now shows Wall texture Tile/Fit; also reduced its oversized dialog to 240px and moved Done into the right column so the title stays on-screen at the test GUI scale. Final source recapture will include corrected supported-door examples and cached grouping.

Final validation: standard Java 8 build, non-rendering and filesystem contracts pass. Full Forge live suite `testclient/render-run.qg1MEr` exited successfully with every runtime marker and all six image analyzers. Reviewed supported Fit/Tile doors, square door halves, categorized screen/control/material lists, light and propulsion dialogs, and Half-Input title/layout. Exported all 164 curated screenshots through the gallery exporter; checked documentation links, whitespace and packaged classes/catalog/license. These are software-rendered client checks, not hardware shader acceptance.

Tested source: `087d6ddac62c31ea7060e38afba47c695fd6a6f4`. Standard artifact: `build/libs/vandorlabs-1.3.jar`, SHA-256 `c55f0fbf947ac354814d049e2d970f08d4861b2a54681a9b32cab52c51e3f383`. Setup and limitations are in [unified materials](unified-materials.md); new door examples are in the [illustrated improvements](gallery/task-improvements.md).

## Light picker refinement (2026-10-02)

- [x] Hide separate Off light artwork in both programmable light texture lists; retain it in the shared catalog for ordinary materials and saved IDs.
- [x] Make the light dialog taller, with up to eight list rows (seven textures and a pinned category); adapt to the available GUI height and move all lower controls together.
- [x] Document Custom door alpha handling and other-mod door recognition.

Initial build, non-rendering checks and full live suite `render-run.txZxJ2` passed. Screenshot review caught Auto GUI scale retaining only four rows at 720p. Added temporary scale reduction for this dialog, restoration on close, and a focused real-client capture/check route. Final validation pending.
