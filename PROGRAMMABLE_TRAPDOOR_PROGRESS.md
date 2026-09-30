# Programmable Trapdoor

## Request

> Alright. Now you will add a block called Programmable Trapdoor. It will be like the vanilla trap door, except we will be able to select a block texture like with Programmable Block, and it will have redstone channel like Programmable Doors. Also, like Programmable Doors, we will have a movement direction option to toggle between Rotating and Sliding. The block should be directional, which will determine the rotate/slide direction. Note that we only support sliding sideways, not up/down, for obvious reasons. Like with doors, we should allow for placement at the top, middle, or bottom edge of the block. Top and bottom should be inset by 1px to avoid the sliding door showing through the next block since we have no frame for these.

> Extra thing: when I place two trapdoors next to each other, they should open together to opposite sides, much like two doors would, in both sliding and rotating mode.

> Sounds good, we don't need hinges for these btw

> Ok, one more thing, a 2x2 area of trap doors should also open together

## Status

- [x] Block registration, catalog, item, name, and distinct recipe.
- [x] Shared Programmable Block texture choices, configured drops/pick-block/items.
- [x] Manual interaction, physical redstone, virtual channels, persistence/synchronization.
- [x] Horizontal facing determines rotation hinge and slide direction; no vertical sliding.
- [x] Rotating / Sliding settings; Top / Middle / Bottom placement with 1px edge insets.
- [x] Creative shift-right-click configuration and Configurizer support with server validation.
- [x] Adjacent pairs and 2×2 groups open together in opposite directions; no hinge hardware.
- [x] Geometry, collision, selection, rendering, item previews and Dynmap.
- [x] Duplifier settings and connected applications.
- [x] Regression checks, documentation, standard 1.3 JAR.
- [ ] Hardware live-client visual validation (software rendering prohibited).

## Findings

- Existing doors animate visually without ticking their tiles. Reuse that animation timing and the event-driven loaded-chunk redstone channel registry.
- Use vanilla trapdoor thickness (3px): closed Bottom 1–4px, Middle 6.5–9.5px, Top 12–15px.
- The earlier instruction prohibiting software-renderer runs remains in force. Non-rendering checks can run; hardware visual testing remains a separate validation requirement.

## Verification — 2026-09-30

- Java 8 standard build passes, including portable geometry/save-format checks and Dynmap validation.
- `./gradlew testNonRendering --no-daemon` passes: 51,769 trapdoor assertions, plus earlier placement, diagonal, connectivity and ramp regressions. Uses Minecraft/Forge classes without starting a renderer.
- Covers 78 finishes, three heights, both motion modes, four facings, mesh winding/normals/UV/lightmap data, configured client placement, pair motion, all 24 square placement orders, world save/reload, broken/replaced-member repair, physical and virtual redstone, copying, edit permissions, packet encoding and actual recipe matching.
- Documentation: `docs/gallery/programmable-trapdoor.md`, gallery navigation, README, Configurizer and Duplifier guides.
- Standard release artifact: `build/libs/vandorlabs-1.3.jar`.
- Geometry/render code is implemented and buffer-tested; actual GUI, in-world collision/selection and shader appearance still require hardware live-client acceptance. The available test launcher forces software rendering, so it was not run. No shader-pack compatibility claim is made from the non-rendering checks.
