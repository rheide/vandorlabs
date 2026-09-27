# MCTrek landing gear source archive

This document describes the supplied source ZIP. Current game blocks are
one Landing Gear block with Small, Medium and Large sizes. Small and Medium
retain the authored Small and Large models; the new Large has a 14-pixel wheel
and full-block width. Runtime arms are uniformly 4×4 pixels and tile the glass-frame interior metal.
Old IDs and static variants are removed. See the [current gear guide](../gallery/version-1.1.md#landing-gear).

Five designs, intentionally simple. All geometry is axis-aligned; all endpoint coordinates are whole voxels. One Minecraft block is 16 model units. Small wheels are 8 units in diameter and 4 wide; large wheels are 12 in diameter and 6 wide. Wheel rotation axis is X, rolling direction is Z. The 16×16 textures use restrained grey shading, with a cyan indicator and amber button on mounting plates. The colored pixels are not emissive by themselves.

## Files

- `blockbench/`: editable `.bbmodel` projects with textures embedded. The four standard models and telescopic endpoint projects use Java Block/Item format. The `...telescopic_RIG.bbmodel` uses Generic Model format, with three named groups and a one-second `extend` preview animation.
- `assets/mctrek/models/block/`: Minecraft Java model JSONs.
- `assets/mctrek/models/item/`: item parents.
- `assets/mctrek/textures/blocks/landing_gear/`: five shared 16×16 PNG textures.
- `assets/mctrek/blockstates/`: example north/east/south/west facing mappings for the four static variants.
- `obj/`: portable textured OBJ exports, one block = one metre. Keep the adjacent asset folders for texture resolution.
- `preview.png`, `preview-telescopic.png`, `previews/`: renders made from the supplied geometry and textures, including reverse views.
- `tools/build_models.py`: reproducible source generator (Python, Pillow, NumPy).

## Designs

| Model | Wheel | Cuboids | Bounds |
|---|---|---:|---|
| top_small | 8×4 | 12 | within one block |
| top_large | 12×6 | 14 | within one block |
| side_small | 8×4 | 8 | within one block |
| side_large | 12×6 | 10 | within one block |
| top_small_telescopic | 8×4 | 13 | 1 block retracted, 2 blocks extended |

Canonical top attachment is +Y. Canonical side attachment is -X (west). The four example facing states rotate the entire model around Y; for side mounts the attached face rotates from west to north, east, south respectively. Rename the namespace/IDs or change state mappings to match your registration.

## Telescopic animation

Open `blockbench/landing_gear_top_small_telescopic_RIG.bbmodel` for the grouped animation source. The Java JSON endpoint files are static reference poses; they do not carry animation.

The fixed mounting plate remains at Y=14..16. The tire bottom moves from Y=0 to Y=-16, so the extended assembly spans exactly 32 units (two blocks) from Y=-16 to Y=16. The upper block is the logical block origin; the extra block is below it.

For extension fraction `t` from 0 to 1:

| Group | Transform |
|---|---|
| `fixed_mount` | unchanged |
| `wheel_assembly` | translate `(0, -16*t, 0)` |
| `piston` | scale `(1, 1+8*t, 1)` about pivot `(8,12,8)` |

The piston is initially Y=10..12. Its upper end stays at Y=12 and its lower end follows the moving fork at Y=10-16*t. This deliberately simple stretching piston meets the fixed sleeve and moving fork at every pose. It uses a plain low-contrast metal texture. Reverse `t` to retract; apply your preferred easing. The rig's one-second preview uses linear interpolation. Runtime animation and texture behavior are yours to implement. Fractional coordinates during motion are expected; resting geometry stays on the voxel grid.

## Integration

Asset layout and pack metadata follow the project's Forge 1.12.2 conventions. This archive is a model asset bundle, not a compiled mod: it does not register blocks or items. Copy `assets/mctrek` into your mod resources and connect the IDs to your block implementation. If the IDs are already registered, the same asset tree can override their appearance through a resource pack.

Use a non-full-cube/non-opaque block implementation and suitable collision/selection boxes. Custom animation needs a dynamic renderer (for example, a tile-entity renderer in Forge 1.12.2); a baked block JSON cannot animate its elements. Account for the extra occupied space below the telescopic block and expand renderer bounds to avoid culling its extended wheel. Emission and button/status behavior require mod code. This delivery includes geometry and a preview rig, not those gameplay systems.

## Checks

The generator verifies integer endpoint coordinates, positive cuboid dimensions, no overlapping cuboid volumes, and a connected assembly. All texture references resolve locally. The telescopic animation endpoints were checked against the exported pose JSONs. Previews were visually reviewed. The models have not been tested inside Minecraft or the Blockbench application.
