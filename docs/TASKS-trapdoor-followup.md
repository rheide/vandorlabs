# Programmable trapdoor follow-up

Requested 2026-10-02. Preserve saved material identifiers, loaded-only grouping/redstone and Creative configuration permissions. Commit fixes incrementally, update changelog/docs and validate the standard Java 8 JAR in the live client.

## Diagonal programmable trapdoor issues (verbatim)

- [ ] Toggling full/half width breaks a combined diagonal trapdoor rather than toggling them combined
- [ ] When selecting a door texture for a combined diagonal trapdoor, there should be an option to fit/stretch like with slabs etc. - currently when passing a regular minecraft door block for a custom door on a 2x2 it just uses a single door texture that gets stretched, would be nice if we could detect this and tile + mirror left/right. (and same for our own door textures)
- [ ] The top/side texture of the diagonal door should always be the standard door size texture, just like we fixed for the custom doors earlier
- [ ] There's some z-fighting going on with sliding diagonal trapdoors when sliding over a diagonal block

## Regular programmable trapdoor issues (verbatim)

- [ ] there's still some z-fighting with the neighboring block when rotating open
- [ ] Same side texture issue - should be the standard door side texture
- [ ] 2x2 door textures also stretch, should tile
- [ ] The 'cover' option for landing gears makes no sense to me at all. I can't seem to place a trapdoor in a way that would cover a landing gear block, and it also slides instead of rotates. For sliding motion I can already place a trapdoor in a way so that, when it's open, it covers the neighboring block. However, the problem I have with rotating trapdoors is that the rotating hinge side is always so that the untriggered position covers the top face of the block I placed it on. I want an option for it to cover the next block over from the hinge side.

## Findings and decisions

Initial checkout clean at `d4b9b2ff`. Read README, trapdoor and landing-gear guides, material/transparency guide, and project guidance.

The diagonal size control currently cycles width and height together; separate those controls. Capture the original group before predicting or applying settings, so combined tall width edits remain combined. Next-block coverage should be a placement option independent of Rotating/Sliding, with an explicit facing control for individual offset mounts. Retain loaded-only gear clearance notifications.

Door artwork gets a saved Fit/Tile control; Tile repeats one-block-wide, two-block-long artwork, mirroring alternating columns. Use standard native door leaf-edge artwork on trapdoor edges. Rendering and collision share motion/clearance geometry. Validate combined edits, saved/copied settings, upper/lower UVs, mirroring, edge sprites, open-state clearance, and real scenes/dialogs.

## Implementation slice

Implemented separate diagonal width/height controls, atomic group configuration/prediction, saved/copied Fit/Tile and hinge direction, next-block placement independent of motion, rotating clearance and lifted diagonal sliding. Door faces tile at native proportions with alternating mirrored columns; four thin edges use the native dark-wall leaf-edge texture.

Java 8 standard build, non-rendering contracts and filesystem checks pass (`/tmp/vl-trapdoor-contracts2.log`). Tests cover repeated combined width changes, rigid geometry, neighboring-plane clearance, continuation-wall separation, atlas bounds, complete tiled-face coverage, left/right mirroring and configured-item/copy settings. Live renderer/dialog scenes remain pending.

Added ten live follow-up scenes, with capture-time checks for actual client grouping, upper/lower Custom atlas sprites and standard edge artwork. Added server-authorized GUI checks for repeated combined width changes, Fit/Tile, independent next-block rotation and hinge direction, plus normal/diagonal dialog captures. Java 8 build and all non-rendering/filesystem checks pass (`/tmp/vl-trapdoor-live-fixtures-build.log`). Full live suite is next.
