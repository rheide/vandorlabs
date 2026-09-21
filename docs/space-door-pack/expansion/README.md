# Sci-fi Industrial Door Expansion

Four additional door families in the same industrial gray, cyan and amber style. This is a separate additive pack: the original door pack and its corrected glass version are unchanged.

| Door | Main texture | Design |
|---|---|---|
| Viewport | `door_viewport.png` | Tall, narrow transparent porthole inspired by the supplied example; cyan rim and a low vented kick plate. |
| Laboratory | `door_laboratory.png` | Two stacked horizontal transparent windows and a sealed lower service hatch. |
| Cargo | `door_cargo.png` | Opaque X-braced freight bulkhead, central locking hub and amber hazard band. The side tabs are locking dogs, not animated hinges. |
| Ventilation | `door_ventilation.png` | Opaque broad upper louvers, paired lower grille banks and a central service coupler. |

## Sizes and installation

| Detail folder | Each of the four door textures |
|---|---|
| `low` | 128 × 256 PNG |
| `medium` | 256 × 512 PNG |
| `high` | 512 × 1024 PNG |

Each level was drawn independently with the built-in image-generation tool, then exported to its exact pixel dimensions. Lower detail levels are not reductions of the high-detail doors. Broad structural features stay consistent, while panels, bevels and fasteners vary by level. `AUTHORING_PROMPTS.json` records the original prompts and the two control-placement corrections.

Choose one detail folder. Copy its `assets/scifidoors/` into your mod's `src/main/resources/assets/scifidoors/`, or package that folder's `pack.mcmeta` and `assets/` at a resource pack's root. This expansion uses **new resource names only**, so its texture files can coexist with the original pack. It does not register blocks or add compiled mod code.

Texture identifiers are `scifidoors:blocks/door_viewport`, `scifidoors:blocks/door_laboratory`, `scifidoors:blocks/door_cargo` and `scifidoors:blocks/door_ventilation`.

## Glass and rendering

Viewport has one genuine transparent opening; Laboratory has two. Their steel and cyan rims are opaque. The clear areas use exactly the corrected pack's glass material, with sparse translucent cyan reflections distributed across the surface. Cargo and Ventilation are completely opaque, including their dark recesses and grilles.

The main `door_*.png` files already combine metal and glass. For a renderer with separate passes, the windowed doors also include:

- `door_*_metal.png`: steel with fully open window cutouts.
- `door_*_glass.png`: only the registered translucent glass, with identical UVs.
- `door_*_window_mask.png`: white inside the opening, black elsewhere.

`expansion_glass_tile.png` is a copy of the matching corrected glass material under a new resource name. It is 256, 512 or 1024 square, with matching repeat edges and no borders. None of these files contain the checker backgrounds shown in the previews.

Draw metal with alpha cutout and glass with source-alpha blending after opaque geometry. Preserve faint glass alpha rather than using the opaque pass's discard threshold. For a two-sided door, give both faces matching window openings and avoid placing opaque geometry behind the portholes. Cyan and amber pixels are painted lights; emissive rendering remains an option in your mod.

## Existing frames and hinges

Use the same door geometry, square frame, hinge model and texel density as the original pack. Every texture is a single left-opening leaf with controls on the right. Mirror U for the opposite double-door leaf; handle the hinge geometry and pivot separately.

At low detail, a complete 128 × 256 leaf represents 1 × 2 blocks. An existing 1/16-block rail covers 8 pixels. A single frame exposes `[8,8]` to `[120,248]`; a double frame's left leaf exposes `[8,8]` to `[128,248]`. Coordinates use exclusive upper bounds. Multiply pixel positions by 2 or 4 for the other levels. Keep the full texture mapping; let frame geometry occlude the margins instead of stretching the image. Additional trim may disappear under a more conservative crop, while the window openings and controls stay visible.

Two control assemblies were moved inward during review to prevent frame overlap. The new doors share the original separate hinge placement and swing/sliding rules. No hinge or frame replacements are included in this expansion.

## Previews and validation

- `previews/overview.png`: all four doors at medium detail; checker squares demonstrate window transparency.
- `previews/lod_comparison.png`: all twelve independently drawn variants at a common display size.
- `previews/configurations.png`: each low-detail door unframed, in a single frame and in a double frame, using the same source pixels.
- `VALIDATION.json`: dimensions, alpha, window counts, control clipping and unchanged-original checks.

The PNGs and configuration previews were inspected. Original pack files were compared by SHA-256 and left unchanged. This asset expansion has not been run inside a Minecraft client.
