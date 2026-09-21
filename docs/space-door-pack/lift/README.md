# Sci-fi Industrial Lift Doors

Six upward-opening door designs, supplied as a separate additive texture pack. The previous packs are unchanged. Reuse your existing frame; no new frame textures, frame models, or hinge assets are included.

| Design | Intended use | Surface |
|---|---|---|
| Cargo Lift | Freight elevators and loading bays | Three broad steel cassettes and amber caution band |
| Blast Shield | Bunkers and high-pressure compartments | Stacked overlapping armor plates |
| Glazed Hangar | Vehicle workshops and observation bays | Large transparent upper window, armored lower panel |
| Quarantine Seal | Isolation chambers and clean-room access | Hermetic panels, pale identification band and amber status lights |
| Reactor Barrier | Reactor access and radiation containment | Reinforced chevrons, cooling lights and radiation marking |
| Modular Shutter | Arbitrarily wide or tall industrial openings | Repeating slats; seamless horizontally AND vertically |

## Files and sizes

Choose one complete detail level:

| Folder | Each door PNG |
|---|---|
| `low` | 128 × 256 |
| `medium` | 256 × 512 |
| `high` | 512 × 1024 |

Textures are in `assets/scifidoors/textures/blocks/` beneath each level. Names start with `lift_`, so they do not replace textures in the earlier packs. Each level contains six principal RGBA door PNGs and three optional Glazed Hangar layers. The three detail levels were independently drawn as separate atlas columns; the small versions are not resizes of the high-detail door. Each column was then exported to its required native dimensions with nearest-neighbor sampling and a limited palette.

The directory layout and pack metadata follow the earlier Minecraft 1.12.2 pack. These are texture resources for your mod, not a mod that registers new functional doors. Select one level and copy its resources into your project or resource pack. Minecraft-client integration has not been run here.

## Bare, single-frame and double-frame placement

As in the previous packs, one texture serves all three placements. A framed version is made by placing the existing frame over the leaf margins, not by stretching or resizing the leaf texture. Use the whole image for a bare door, including its finished edge. The Modular Shutter intentionally has no outer endcap, so it also functions as an unframed repeating panel.

The native leaf is 1:2, consistent with the original door sizes. Place one leaf for a normal door opening, or two identical leaves side by side for a 2:2 double-width opening; both lift together. The double preview repeats each leaf without mirroring. The same sheet may also be mirrored if your model requires it.

The previews use the existing frame's actual thin rails: at the low detail level the single preview covers 8 pixels on each side and 8 at top/bottom; the double preview places the original 256 × 256 frame over two 128 × 256 leaves. The window and main controls remain visible. A conservative overlap check also covers 1/16 of each texture dimension on every edge. Decorative edge bolts or upper/lower accent lights may be partly covered, as intended.

The frame is visible only in `previews/configurations.png`, assembled from the original frame art. It is not duplicated in the resource folders. These previews show the frame's metal layer so that glass already present in the door is not blended twice.

## Transparent glass

`lift_glazed_hangar.png` has real RGBA transparency in its window. The dark/light checker squares seen in previews are the background behind the door, not part of the PNG. Window reflections use the same distributed glass shimmer material from the corrected earlier pack. Most window pixels are fully transparent; reflection pixels have low opacity. Steel and cyan trim remain opaque.

Optional files:

- `lift_glazed_hangar_metal.png`: steel with a clear window opening.
- `lift_glazed_hangar_glass.png`: isolated translucent shimmer at matching UV coordinates.
- `lift_glazed_hangar_window_mask.png`: white window, black outside; an authoring mask, not a rendered texture.

Use the combined PNG, or render the metal and glass layers separately. Do not draw both the combined PNG and the glass layer. If the frame's glass overlay is also used, avoid overlapping duplicate panes. Use alpha blending for the shimmer; a binary alpha cutoff alone will discard the subtle reflections. Keep filtering consistent with pixel art. Lighting strips are colored artwork; emissive behavior must be implemented in your renderer.

## Seamless Modular Shutter

`lift_modular_shutter.png` tiles in X and Y at every detail level. Its opposite edge RGBA pixels match exactly, and it contains no unique corner, perimeter border or one-off center control. Each LOD uses its own generated slat artwork, assembled with mirrored quadrants to guarantee periodic boundaries. The mirrored construction is deliberate and visually suits symmetrical industrial slats.

Repeat whole texture tiles to enlarge the door, with the frame around the outside of the whole opening. Preserve the native 1:2 tile aspect ratio for the intended slat proportions. Do not add a frame between tiles. For a texture atlas, repeat geometry or restart UVs inside the sprite for each tile; texture wrapping outside a sprite can sample neighboring sprites. The supplied texture metadata requests unblurred sampling and unclamped wrapping for the shutter, but atlas-safe repetition is still the model/renderer’s responsibility.

## Upward motion

Keep the existing frame stationary. Move the leaf or the entire multi-leaf group upward along local Y. A rigid sliding door needs a pocket above the opening: its displacement at fully open must be at least the opening height. For a smooth animation, use `s = t*t*(3 - 2*t)` with `t` clamped to 0…1, then translate by `s * openingHeight` along local +Y. Move both leaves with the same value for a double door.

For a rolling shutter, the texture can be used on separate horizontal slats, but slat geometry and rotation into an overhead drum belong to your model/animation implementation. Static texture PNGs do not implement this motion. No side-hinge model is needed for a rigid lift.

## Included checks and previews

- `previews/overview.png`: all six designs at medium detail.
- `previews/configurations.png`: bare, existing single frame and existing double frame at native low-detail pixels.
- `previews/lod_comparison.png`: independently drawn detail levels at a common display size.
- `previews/tileability.png`: 3 × 3 native low-detail Modular Shutter tiles.
- `ASSET_MANIFEST.json`: exact asset sizes, crop margin and glass bounds.
- `VALIDATION.json`: dimension, alpha, seams, resource-name and original-file checks.
- `AUTHORING_PROMPTS.json`: generation prompts and export method.
- `SHA256SUMS.txt`: file integrity hashes.

Created with the built-in image-generation tool, then mechanically sliced, palette-limited, alpha-composited and checked for exact game-asset requirements.
