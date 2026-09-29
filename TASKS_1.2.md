# Version 1.2 follow-ups

This file tracks the requested work and findings for this update.

- [x] Investigate the latest server `ConcurrentModificationException` and whether Vandor Labs caused it. The latest crash iterated chunk tile entities and did not identify a mod; an earlier crash included the Ramp Controller. Deferred load callbacks and loaded-neighbor power reads address the mod's likely contribution.
- [x] Check whether lights were already fixed. Version 1.1 deferred the older redstone light; other interactive tiles still used load-time callbacks, so these were deferred as well.
- [x] Bump the mod version to 1.2.
- [x] Import the first `LLMShareDrive/textures` pack into the shared housing finish catalog, with item models, menu labels and Dynmap texture entries; later retain only the 16 choices selected by the owner.
- [x] Remove the other 16 first-pack finishes and packaged assets without save migration, as requested. Older saved finish numbers may now resolve to different artwork.
- [x] Add all 46 images from `LLMShareDrive/textures2` to the same housing catalog. Preserve their T/R/C source codes as menu labels and retain their source dimensions.
- [x] Audit other interactive blocks for loaded-chunk and tile-load behavior. Covered programmable lights, redstone channels, screens, doors, ramps, sequenced displays and landing gear.
- [x] Let attachments such as Mekanism Glow Panels stick to the bottom of a Programmable Slab. The slab now reports the matching outer face as solid.
- [x] Make Tile the default side layout for new Programmable Slabs and their unconfigured item icon, while preserving explicitly saved Fit layouts.
- [x] Let a full-height Programmable Diagonal Wall with Fill Inside or Fill Outside support a Programmable Door. Thin and half-height diagonals remain unsupported.
- [x] Align framed Programmable Doors exactly with near/far block edges, while keeping bare doors one pixel inset so sliding leaves retract behind the neighboring block. Generated model bounds differ by frame, motion and hinge setting. The Java 8 build and live door checks pass.
- [x] Give the Ramp Controller start/end offset sliders half-block steps and extend each endpoint to -16..+16 blocks. The live client verified 1.5 and -2.5 block travel and controller/cell persistence; its menu screenshot shows both sliders. Picked items and copied settings store the half-step fields, with the existing placement and copy checks still passing. The new endpoint check covers a 32-block stroke, saved settings, return travel and cleanup.
- [ ] Investigate viewing-angle lighting on Programmable Diagonal Walls. Their TESR emitted position and texture coordinates without normals; added per-face normals for the opaque diagonal wall and will compare live screenshots. Shader-specific behavior still needs an owner check with the shader pack in use.
- [x] Add Extra Large Landing Gear at twice Large's model dimensions, centered in a three-block-wide footprint.
- [x] Scale and center the Extra Large Landing Gear item icon within a hotbar slot, without changing its placed model.
- [x] Replace Dynmap's missing Programmable Slabs, fixed-position doors and triangular Ramp cell placeholders with renderers that read block or tile state. The deployed Ramp cells use their clipped cell boxes and saved programmable main finish; other source materials use stone until per-source texture lookup is available.
- [x] Thicken the Dynmap-only propulsion emitter face from a quarter pixel to one pixel so the front texture survives map ray tracing.
- [ ] Confirm the new slab, door, propulsion and Ramp cell appearance after a future server deployment and Dynmap tile rerender. Dynmap 3.7 loaded the custom renderers and generated tiles in the isolated test client without renderer errors. The running server remains read-only and is not being restarted for this work.
- [x] Flip Programmable Input and Programmable Half Input screens when placed beneath a ceiling.
- [x] Let ceiling-mounted Programmable Inputs, Half-Inputs and Half-Consoles use near/middle/far slots across the underside, selected by the click position. Placement, saved tile state, TESR geometry and hitboxes passed the Java 8 build and live client suite.
- [x] Add Programmable Light Frame as a thin panel with Programmable Light settings and emission.
- [x] Add Programmable Light Slab with the same settings and emission.
- [x] Apply the selected wall texture to all propulsion housing faces and show a preview in the propulsion dialog.
- [x] Investigate the intermittent test-client chunk-packet crash. ReproLab was mutating the integrated-server world from the client thread during bulk setup; move those writes to the server thread. Client-side runtime checks still touch test-world blocks, so the race is reduced rather than proven eliminated. The production-server crash remains unattributed by its stack trace.
- [x] Investigate missing Dynmap Programmable Block, Slab, Stairs and Wall rendering. The running server's 1.2 Dynmap log lists these as unknown blocks. Their Minecraft JSON models are intentionally empty, while bundled Dynmap data omitted eight programmable block families and the Controlled Ramp. Added static fallback geometry for every blockstate and strengthened the support validator. Stairs already had 40 state models. Also removed nine invalid model records for three texture-only names reported by Dynmap at startup. Live server remains untouched; the corrected JAR needs a future deployment and tile rerender to confirm the map result.
- [x] Complete the Java 8 standard-JAR build, live client checks and visual review of the new shapes and Ramp Controller menu. The shader-specific diagonal-wall effect and future Dynmap rerender still need checks in their target environments.
