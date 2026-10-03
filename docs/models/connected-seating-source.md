# MCTrek connected seating

Luxury airline-style seating and military harness seating. Luxury headrests have no side wings. Each style has single, left end, middle, and right end variants. A couch is two neighboring chair blocks using matching end variants, not a separate wide in-game block.

All geometry uses integer voxel coordinates (16 units per Minecraft block). Each chair occupies a 16×16 footprint. The luxury back is 24 units (1.5 blocks) tall; military is 20 units (1.25 blocks) tall. Textures are 16×16 PNGs with at most one texel per voxel on the mapped surfaces. No bevels, sloping faces or sub-voxel geometry.

## Contents

- `blockbench/`: editable Java Block/Item `.bbmodel` files with embedded textures. Four variants per style plus assembled two-chair couch examples.
- `assets/mctrek/models/block/seating/`: eight Minecraft Java block models.
- `assets/mctrek/models/item/`: item models using each style's single-chair variant.
- `assets/mctrek/textures/blocks/seating/`: shared PNG textures, including military shoulder/lap harness graphics and a raised square buckle texture.
- `assets/mctrek/blockstates/`: sample `facing` + `part` blockstate mappings for each style.
- `obj/`: textured OBJ versions, including assembled couches. Keep `assets` alongside this directory for texture resolution. Export scale: one Minecraft block = one metre.
- `preview.png`, `previews/`: renders from the actual exported geometry and textures, including reverse views.
- `integration/connection_rules.md`: exact neighbor selection and orientation rules.
- `tools/build_models.py`: source generator (Python with Pillow and NumPy).

## Connected appearance

| Part | Local -X arm | Local +X arm | Cushion width |
|---|---|---|---|
| single | present | present | X=2..14 |
| left | present | removed | X=2..16 |
| middle | removed | removed | X=0..16 |
| right | removed | present | X=0..14 |

Left/right names are local coordinate labels, not screen-left/right labels. Canonical chairs face north (-Z). Put the `left` model in the lower-X block and the `right` model in the adjacent higher-X block. Each cushion expands to meet its neighbor at the shared block boundary; each military seat retains its own harness and buckle, and each luxury seat retains its own headrest. Middle pieces allow longer benches.

## Integration scope

This is a model asset bundle, not a compiled mod. The asset paths and pack metadata follow the project's Forge 1.12.2 conventions. Copy `assets/mctrek` into your mod's resource tree and register the corresponding block/item IDs (`luxury_seat`, `military_seat`), or adapt the namespace and paths to your existing IDs. The example blockstates expect `facing` and `part` properties.

The models alone cannot detect adjacent blocks. Implement the rules in `integration/connection_rules.md` in your block state logic to select the supplied models, including updating both neighbors after placement/removal. Do not join different styles or different facings.

Use non-opaque/non-full-cube rendering and appropriate collision and selection boxes. These chairs extend above one block: handle headroom, selection and renderer bounds accordingly. Seating entities, interactions, automatic joining, lighting emission, and gameplay are not implemented in these art assets. Cyan/amber pixels are surface colors, not emissive shaders.

## Checks

Validated integer coordinates, positive cuboid sizes, 16-pixel UV bounds, texture resolution, no overlapping cuboid volumes, physically connected geometry, and flush joining cushion edges. Static previews were visually reviewed. In-game behavior and Blockbench application loading have not been tested.
