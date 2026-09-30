# Programmable Diagonal Trapdoor

## Request

> Cool, commit and update changelog. Then I want you to work on the same thing, but for our diagonal wall blocks: let's build a Programmable Diagonal Trapdoor. Same principles: double or 2x2 blocks should open together. Placement should align with our existing options for diagonal walls (full-width-full-height, half-width-full-height, full-width-half-height). These trapdoors should be slightly thinner than the walls, 1px gap on each side.

## Status

- [x] Commit existing 1.3 work and changelog: `310e710e`.
- [x] Diagonal geometry and wall-compatible placement for all three modes, with 1px gaps.
- [x] Texture, Rotating / Sliding, physical/virtual redstone and configuration.
- [x] Opposing pairs and 2×2 groups, persistent links, copying and configured items.
- [x] Mesh, collision/ray selection, item assets, recipe and Dynmap.
- [x] Non-rendering regressions, standard build, documentation and changelog.
- [ ] Hardware client / shader visuals (software renderer prohibited).

## Findings

- Existing wall panels are 4px thick in their depth coordinate; the leaf is inset 1px on both surfaces, leaving 2px thickness.
- Full-height panels rise through a voxel with 6px or 12px diagonal span. Half-height panels span 6px vertically over the full block depth, with upper/lower band placement.
- Motion uses the surface's horizontal width; Sliding never moves vertically. Rotating pivots around a side edge along the slope.

## Verification — 2026-09-30

- Java 8 `./gradlew build testNonRendering --no-daemon` passes, including portable geometry, codecs, external copy contracts and Dynmap checks.
- Diagonal suite: 369,821 assertions. Regular trapdoor suite: 51,769 assertions; earlier placement/connectivity/ramp checks also pass.
- Covers all geometry modes, wall placement and off-hand configuration, 1px insets, rigid motion, submitted normals/UV/lightmap data, collision/selection, all 24 square placement orders for each facing and inversion, reversed coplanar rows, save/reload, vanilla removal ordering, repair, group copying, local/virtual power, permissions, loaded chunks and actual recipe matching.
- Paired motion leaves the closed surface orientation intact. Square groups persist a common placement basis so reversed wall rows address the same four cells.
- Changing tall width preserves links; switching tall/shallow dissolves the old layout links. Group inversion changes preserve each row's relative inversion rather than flattening reversed rows.
- Standard `build/libs/vandorlabs-1.3.jar` inspected: registered classes, recipe, catalog/localization, blockstate, Dynmap entries and all 78 configured finish models. Documentation links and `git diff --check` pass.
- Hardware GUI, gameplay and Complementary Unbound 5.6.1 visual acceptance remain pending. The available live suite forces software rendering and was not run.
