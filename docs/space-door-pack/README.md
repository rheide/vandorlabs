# Sci-fi Industrial Door Pack — revision 2

Five coordinated door families: Observation, Airlock, Standard, Security and Reactor Service. Gunmetal steel, cyan inset lights, amber controls and chunky stepped edges. Handles are on the right. Hinges are separate geometry, never painted into a door leaf.

## Files and dimensions

| Folder | Each of five door textures | Double frame with glass | Hinge atlas |
|---|---|---|---|
| `low` | 128 × 256 | 256 × 256 | 32 × 32 |
| `medium` | 256 × 512 | 512 × 512 | 64 × 64 |
| `high` | 512 × 1024 | 1024 × 1024 | 128 × 128 |

Every door/frame detail level was generated as a separate design. The low and medium files are **not reductions of the high files**. Each independent source was exported to its exact requested resolution with nearest-neighbor sampling and restrained palette quantization. Designs deliberately vary in panel layout, bolt count and bevel treatment across detail levels. The hinge uses the same seven-cuboid geometry and three natively authored material atlases.

Each level has a Minecraft 1.12.2 resource layout. Use **one** level at a time. Copy its `assets/scifidoors/` folder to `src/main/resources/assets/scifidoors/` in your mod, or package that level's `pack.mcmeta` and `assets/` at the root of a resource-pack ZIP. These are assets and integration helpers; this is not a compiled mod and does not register new blocks by itself. Replace the `scifidoors` namespace everywhere if your mod uses another ID.

Texture paths in `textures/blocks/`:

- `observation.png`: large rectangular transparent porthole in the top half; lower grille.
- `airlock.png`: sealed, heavy pressure spine and locking braces; no window or grille.
- `standard.png`: simple sealed panel and bottom grille.
- `security.png`: heavier plates, locking bars and bottom grille.
- `reactor.png`: sealed service hatch, broad latch straps and amber hazard markings.
- `double_frame_glass.png`: the requested combined transparent frame and glass-shimmer image.
- `double_frame_metal.png`, `glass_overlay.png`: separate rendering layers of the same frame.
- `hinge.png`: four-tile native hinge material atlas.

The Observation texture has a genuinely transparent porthole with faint cyan glass shimmer. Its old opaque smoked pane has been removed. All steel and the other four door types remain opaque. The same independently authored glass material is used in both the porthole and the frame. Reflections are distributed across the surface, including its center. The frame remains mirrored horizontally and vertically.

## Frame geometry and clipping without stretching

The square frame represents a **2 × 2 block** double doorway. Each of its four rails is **1/16 block wide**, which is 1/32 of the square texture: 8, 16 or 32 pixels respectively. Use a physical frame depth of 1/16 block too if desired; the front image does not prescribe depth.

A full door leaf represents **1 × 2 blocks**, with one unchanging texel density. Keep that complete texture mapping behind the frame. Expose fewer pixels using geometry clipping or occluding frame rails; never stretch the remaining UVs over a different width. For a right leaf, mirror U so both handles meet at the center seam. Mirror the geometry/pivot separately.

At low detail, 128 pixels = 1 block. The ordinary configurations are:

| Configuration | Full source | Visible source rectangle, exclusive end | Visible size |
|---|---|---|---|
| Unframed | 128 × 256 | `[0,0]` to `[128,256]` | 128 × 256 |
| Single frame | same source | `[8,8]` to `[120,248]` | 112 × 240 |
| Double frame, left leaf | same source | `[8,8]` to `[128,248]` | 120 × 240 |

Multiply pixel coordinates by 2 or 4 for the other detail levels. Double doors have **no central rail**. The handles remain visible in every configuration. A conservative additional crop check covers left 12.5%, right 6.25%, top and bottom 6.25%; minor peripheral trim may disappear, while identifying door features survive. See `previews/configurations.png` and `previews/lod_comparison.png`.

For a single-door frame, reuse the top/bottom/side rail strips as a nine-slice: keep the rail thickness and corner dimensions fixed and shorten only the long runs. Do not squeeze the square frame into a portrait rectangle. The supplied square frame contains glass highlights; the separate glass layer lets you choose which surface receives them.

## Borderless repeating glass

`glass_tile.png` is the standalone **256 / 512 / 1024 square** repeating glass texture. It contains no rails, bolts, dark fill or corner vignette. Opposite edges match, with a narrow clear gutter. Repeat it normally in X and Y; do not mirror alternate copies. Its alpha is zero in clear areas and at most 51/255 in reflection strokes.

`glass_interior.png` is the exact **240 / 480 / 960 square** opening used inside the frame; it also tiles without seams. You can crop the same area directly from `double_frame_glass.png` by removing 8 / 16 / 32 pixels from each edge. `glass_overlay.png` retains the frame-sized transparent margins for exact alignment with the metal frame; use `glass_tile.png` or `glass_interior.png` for repeating panes instead.

`observation.png` is still the complete single-leaf texture, now with true porthole transparency. Optional `observation_metal.png` and `observation_glass.png` separate the two render passes without changing their UVs. `observation_window_mask.png` is a white-inside/black-outside registration mask. The glass is a native-density, wrapped sample of the matching `glass_tile.png`; it is not the old blue pane with reduced opacity. The metal, cyan rim and controls retain their original pixels. Apply the same opening to the back face of a two-sided door, and avoid putting an opaque face behind the porthole.

See `previews/glass_corrections.png` for the transparent windows over contrasting backgrounds and a repeated glass surface. Checker patterns in that preview are only transparency demonstrations and are not part of any glass texture.

## Transparency and lighting

Render frame steel in the solid pass. Render `observation_metal.png` with alpha cutout, so the porthole is open. Render `observation_glass.png` and `glass_overlay.png` with normal source-alpha blending in the translucent pass, after opaque geometry. Retain the faint alpha values in the glass pass rather than discarding them with the opaque-pass alpha threshold. Separate the surface from other faces by a tiny depth offset to avoid z-fighting. The combined frame PNG is also available where your renderer handles mixed alpha in one material. Glass is a static painted shimmer, not a time-based animation.

Attach or hide the glass overlay according to the actual glazed surface in your model. Do not leave an entire glass sheet floating across an open doorway unless that is intended. Cyan and amber are bright painted texels; true emissive light or bloom needs an emissive pass in your renderer/shader. Vanilla Forge 1.12.2 does not make colored texels automatically emit light.

## Hinge import

`hinge/hinge.obj` plus `hinge.mtl` and `hinge.png` is a portable mesh. It has named `fixed` and `moving` groups. Units are blocks; the pin is at the origin, +Y up. `hinge_animated.gltf` includes the same two parts and a two-second open/hold/close preview animation. Keep `hinge.png` beside both mesh exports.

Minecraft model JSONs are included in every detail level under `models/block/`:

- `hinge_fixed.json`: frame mount, pin and two caps.
- `hinge_moving.json`: door mount, rotating sleeve and cyan inset.
- `hinge_combined.json`: static assembled model for inspection/import.

JSON coordinates use a pivot of **[8,8,8]** in Minecraft's 16-unit model space. Static model JSON does not itself provide a continuous hinge animation. For runtime animation, render the fixed and moving parts separately, or use the included **`ForgeHingeRenderer.java`** helper in your existing TileEntitySpecialRenderer. The OBJ/glTF files are interchange exports; they are not automatically loaded by vanilla block JSON.

## Attachment and animation

Use two hinges per rotating leaf. For a 2-block-tall leaf, place their pin centers **6/16 and 26/16 blocks above the bottom**. Each hinge is 6/16 block tall. The fixed mount extends 1.5/16 block toward the frame; the moving plate extends 2.5/16 block inward across the door. These are deliberately chunky low-poly fittings: **7 cuboids, 42 quads / 84 triangles per hinge**.

Place the vertical pin at the visible hinge-side edge of the leaf. The pin is **1/16 block in front of the closed door face**. In construction coordinates the closed door face is Z=0 and the pin is Z=1 model pixel; OBJ/glTF and the Java helper subtract this offset so their origin is the pin itself. The fixed mount's rear face lies on the door/frame front plane. Align the frame lip and the door front at that plane or add a small shim in your own frame geometry. Never bake the fixed mount into the moving door mesh.

Both hinges share the same vertical swing axis. Rotate the door slab, moving plates and sleeves together around that axis; keep pins, caps and frame mounts fixed. Use 0° closed and ±90° open. For the opposite leaf, mirror hinge geometry across X and reverse the swing direction. If mirroring a culled mesh with a negative scale, reverse triangle winding or adjust culling for that draw.

The Java helper expects the pin position in the caller's block-space coordinate system. Example inside a client TESR, after you have applied the block-facing transform and set the world lightmap:

```java
float a = ForgeHingeRenderer.angle(prevOpen, open, partialTicks, false);
ForgeHingeRenderer.render(pinX, 6.0/16.0, pinZ, 0, a);
ForgeHingeRenderer.render(pinX, 26.0/16.0, pinZ, 0, a);
// Render your door under exactly the same vertical-axis rotation.
```

Store `prevOpen` and `open` on the TileEntity, not on the shared TESR. Advance opening progress in ticks, interpolate with `partialTicks`, and synchronize target state from the server. A 10-tick swing is a reasonable starting point. The helper applies smoothstep easing. It binds its texture; the caller supplies lighting, white draw color, opaque blend/depth state and restores any state needed for subsequent drawing. Keep this class in client-only initialization to avoid loading Minecraft client classes on a dedicated server.

For a sliding door, use exactly the same leaf texture, omit the rotational hinge geometry, and translate the complete slab sideways. Sliding and swinging collision bounds, interaction areas and server synchronization belong to your mod implementation.

The Java helper targets the Minecraft 1.12.2 `BufferBuilder` / `Tessellator` / `GlStateManager` API. It is integration source, not a compiled JAR. This environment has not run a Forge client or compiled against your MDK. JSON, image dimensions, alpha, mesh winding, UV ranges, animation accessors and crop layouts were checked; in-game rendering remains an integration check in your mod.

## Sources and authoring

The attached image was inspiration only. No reference pixels were copied into the deliverable textures. `AUTHORING_PROMPTS.json` records the built-in image-generation prompts and repairs; `ASSET_MANIFEST.json` records dimensions and mesh counts.

Forge references used for integration:

- [Forge 1.12.x: model resource locations](https://docs.minecraftforge.net/en/1.12.x/models/introduction/)
- [Forge 1.12.x: TileEntitySpecialRenderer](https://docs.minecraftforge.net/en/1.12.x/tileentities/tesr/) — architectural guidance; some prose on that legacy page retains older method names. Use your 1.12.2 MDK's actual method signatures.

Open `hinge/hinge_preview.html` for a self-contained interactive mechanical preview. It needs no server or external libraries.
