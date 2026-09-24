# Voxel Industrial Wall Pack

An eight-block pack: four plain blocks and four optional dark-bordered blocks built on a **16 × 16 × 16 integer grid**. This replaces the smooth-wall design with physical stair steps, a square-stepped porthole, and quieter, lower-contrast **32 × 32 steel textures**. Previous packs are not modified.

## Geometry

- **Bottom diagonal:** full 16-pixel width at every height; nine axis-aligned bands step from the near side to the middle of the block. Every tread moves back exactly one pixel. Interior bands are two pixels high; the first and last bands are one pixel high to preserve the exact end alignment.
- **Top diagonal:** the bottom geometry rotated 180° around Z through the center of the block. Same width, steps and depth.
- **Regular wall:** six pixels thick, with front at Z=8 and back at Z=14.
- **Porthole wall:** same six-pixel wall with an actual stepped opening. Every part of the rim uses square one-pixel corners; there are no chamfered or angled edges. The visible clear area is eight pixels high, with row widths 4, 6, 8, 8, 8, 8, 6, 4. Four muted cyan rim pixels echo the earlier door theme.

All geometry is axis aligned. No rotated cuboids, slanted polygons, diagonal planes, subpixel bevels or triangular side fills are present. Each solid band is six pixels deep. Width never tapers. Stack bottom diagonal, regular/porthole wall, and top diagonal with the same facing to make the assembly in the preview. Both outside ends project toward the player. Any number of vertical wall rows or adjacent columns can be added.

The model origin is the block's bottom north-west corner; Y points upward, the unrotated front faces north (-Z). The lower diagonal starts at Z=0 and ends at Z=8. Both joint surfaces match the straight wall exactly.

## Materials

Steel and its shaded variants are **32 × 32**, sampled without blur. Cyan and glass remain **16 × 16**. The geometry is still on the original 16-pixel block grid. The steel uses a small palette of closely spaced shades to reduce grain contrast. The bezel uses a lighter grainy steel; the inside of the porthole is darker. There are no hidden high-resolution maps, normal maps, smooth gradients or tiny details.

The porthole contains a transparent glass plane with coarse one-pixel shimmer from the shared glass style. The glass is in the model opening; the background seen in previews is not part of the texture. The glass remains solid to entities in the supplied block code. Cyan is a texture color, not an emissive shader.

## Optional borders

Each of the four shapes also has a separate `_bordered` block ID. These use a subtle dark perimeter two texture texels wide (one model pixel) on the front/back face texture. Their center pixels are identical to the plain texture. Choose either set or mix them; geometry, collision and joints match exactly. Both sets have their own inventory models and registration entries.

`previews/plain_and_bordered.png` compares both sets using actual model renders.

## Files and use

| Folder/file | Contents |
|---|---|
| `resources/assets/scifidoors/models/block/` | Eight native Minecraft cuboid JSON models |
| `resources/assets/scifidoors/models/item/` | Inventory models |
| `resources/assets/scifidoors/blockstates/` | Four facing variants per block |
| `resources/assets/scifidoors/textures/blocks/` | 32 × 32 steel PNGs, 16 × 16 cyan/glass and unblurred-sampling metadata |
| `exchange/` | Textured OBJ + MTL + PNG and self-contained GLB versions |
| `src/main/java/example/scifidoors/` | Forge 1.12.2 block, registration, client model and collision examples |
| `previews/models.png` | Four actual model renders |
| `previews/assembly_and_profile.png` | Actual assembled wall and visible stair-step side profile |
| `GEOMETRY.json` | Complete exported mesh vertices and UVs |
| `VALIDATION.json` | Integer-grid, step, joint, texture and export checks |

All resource names start with `voxel_` to avoid replacing the earlier assets. Import the native block JSON into Blockbench as a Java block/item model, or use the exchange files in a mesh editor. Exchange model units are blocks, so one pixel is 1/16 unit. Glass is a zero-thickness plane with both north and south faces; OBJ includes both windings, while GLB uses a double-sided material.

## Forge 1.12.2 integration

This is an asset/source package, not a compiled mod JAR. Copy `resources/assets/` into your existing mod's `src/main/resources/assets/`, and add or merge the three Java classes into your existing Forge project. Keep your existing pack metadata if already present. The classes assume an existing `@Mod` class with ID `scifidoors`; change packages and all namespace/registry references together if your mod uses another ID. Register each block once.

These are native cuboid JSON models: **no OBJ loader is needed in Minecraft**. The OBJ/GLB files are optional exchange versions only. Collision follows the exact same nine stepped bands used in the diagonal models. The regular/porthole collision is the six-pixel slab, including the glass. Selection uses enclosing boxes. Models are not full opaque cubes, and block light opacity is zero in the example; adjust lighting behavior for your mod as needed.

The porthole block renders in the translucent pass to preserve shimmer. Its steel texture pixels remain opaque. Check transparency sorting next to other translucent blocks in-game. Forge source compilation and Minecraft client integration have not been performed here.

## Verification and authoring

The previews are rendered from the exported model geometry, not generated concept images. Validation checks every vertex is on the integer grid, every face normal is axis aligned, the upper geometry is the rotated lower geometry, step depths are exactly one pixel, body depth is six pixels, and the endpoint cross-sections meet the wall. Steel textures are 32 × 32; glass and cyan are 16 × 16. JSON references and exchange-file indices are checked.

The grainy material was created using built-in image generation, exported with nearest-neighbor sampling to 32 × 32 and a compressed steel palette of at most eight shades. The models, UVs and collision logic were built anew from grid-aligned cuboids. The new porthole is a voxel mask, not the earlier smooth octagonal mesh.
