# Unified materials and Custom textures

Canopies, visors, programmable storage, blocks, slabs, stairs, walls, trapdoors, doors, lights and static screen/control surfaces share a texture picker. Categories expand and collapse, and each texture has a larger thumbnail with its original aspect ratio. Categories and texture names are sorted alphabetically, and the current category heading stays visible while scrolling within it. The category containing the selected material opens automatically. Options are grouped and sorted once per dialog; expanding a category reuses those groups. Only visible rows draw thumbnails, using the already-loaded Minecraft atlas. PNGs are not loaded on demand, so large catalogs still increase startup time and atlas memory use.

Built-in categories include Materials, Panels, Tech, Hull, Industrial, Lights, Doors,
Screens, Trapdoors, Windows, Storage and Glass. Dark Hull 1–12 replace the old
coordinate labels; moved texture-pack choices no longer carry T1/T2 prefixes.
The sixteen retired built-in finishes are omitted from both the picker and
runtime JAR/atlas. Their saved identifiers remain valid and render with Dark
Wall Panel; reorganized selectable finishes keep their artwork. The Example filesystem
category is hidden when its only entry is the default sample panel.
The bundled block-material list currently has **132 selectable designs** across Materials, Panels, Tech, Hull, Industrial, Trapdoors, Windows, Storage and Corp. See the [current block gallery](gallery/building.md#programmable-block-finishes) for every design. Screen, light and door menus add their specialized artwork; filesystem catalogs vary by installation.

Storage offers fourteen matching top/side/front sets; [Programmable
Storage](programmable-storage.md) uses all three faces and other shapes use
the front artwork. Blue Hex and Amber Hex are paired light finishes, with
separate lit and unlit artwork. Newly placed normal and diagonal trapdoors use **Armored Hatch** from Trapdoors. Existing saved selections retain their artwork. The Screens entries are static first frames; screen animations remain available in their original menus. Light entries have separate On and Off choices. The On choices retain their automatic unlit artwork when used on lights. Programmable light dialogs hide separate Off choices in the ON tab; select an On material for automatic switching, or choose explicit artwork in the OFF tab. Their taller dialog shows up to seven textures plus the pinned category heading, temporarily reducing Minecraft's GUI scale when necessary to fit, and restoring that setting on close. Door entries provide Small and Large detail tiers as full door artwork, rather than separate upper/lower choices. Existing saved housing indices are preserved.

Hull groups exterior plating and protective cladding; Panels groups interior sheets and access covers. Industrial holds fourteen pipe, grille, rib, bulkhead and machinery finishes. Microchip and the Matter color variants are in Tech. The [complete category moves](texture-category-proposal.md) preserve saved selections.

## Choose a surface

Shift-right-click in Creative mode, or open the block's menu with the Configurizer. Choose a category and material. Lights have separate face and housing lists; triggers have Off and On lists. Blocks, slabs and storage offer optional per-face overrides. Storage faces that inherit the main finish retain its matching top/side/front artwork.

For screens, inputs and consoles, open **Surface textures** to choose a static front surface. Consoles also have a second surface choice. **Use screen / animation** restores the original configured screen or control panel. The native screen/control lists now use the same categorized thumbnail presentation, containing their original options. They still select the original animated screen or control family: Off/Static/Animated, speed and frame controls retain their behavior. Selecting a native option restores that surface from a static override. Housing lists use the shared material catalog.

Door presets retain the existing frame, motion and hardware options. Selecting a different material replaces the door's front/back leaf artwork while keeping the native edge textures and hinge hardware. **Face texture** offers Stretch, Tile and Fit (aspect); door artwork retains its upper/lower mapping. The layout survives saving, configured items and Duplifier copying. Ordinary programmable blocks use the lower square half of built-in door artwork, avoiding a stretched two-high image. Trapdoors offer the same three layouts. Tile repeats one-block-wide, two-block-long door artwork, mirroring alternating columns; Stretch maps one image across the complete group. Single-row door surfaces use the lower half. Custom `BlockDoor` artwork retains its upper/lower atlas sprites. The standard programmable door’s metal side artwork covers the four thin leaf edges in either layout.

Canopy and both front-window sizes put all settings beside the same large categorized list, without settings pages or separate glass/material lists. **Glass** combines the three original shades and sixteen dye colors. Selecting a normal material replaces the glazing; **Texture layout** offers Stretch, Tile and Fit (aspect), with the same aspect-ratio rules as block housings. See [canopy settings and examples](canopies.md).

## Custom block and door artwork

Scroll to **Custom...** in any shared material list. The picker shows your inventory and a sample slot. Drag a block or door into that slot, or click an inventory item and then **Use texture**. Nothing is consumed or moved in your real inventory.

For configured programmable items, the picker reads the material saved in the item, including Custom and filesystem choices. Other selected blocks' models supply their atlas texture. This supports vanilla and other mods' registered block items and door items. Block metadata is included in the saved choice. Vanilla doors and mod doors inheriting Minecraft's door block use upper and lower artwork for programmable door leaves and connected trapdoors. Ordinary blocks use the model's representative particle texture; the source block's mesh, tile settings and animation are not imported. Models without usable atlas artwork fall back to the default wall panel.

Custom choices survive saves, configured items and Duplifier copies. A client missing the referenced block uses the default panel while keeping the choice. Each client's resource pack determines the source artwork it sees.

To import PNG files instead, see [filesystem texture categories](filesystem-textures.md). Clients and servers may have different filesystem catalogs.

Programmable Glass and Programmable Diagonal Glass use an opaque **Strong** finish for both native glass colors and replacement materials, including Clear. Mild and Normal keep their translucent appearance. Strong panes use the solid render pass so they occlude geometry consistently with shaders.

## Texture layouts

Blocks, slabs, stairs, walls, glass, lights, doors, trapdoors, canopies and display housings offer three texture layouts:

- **Stretch** scales the complete image to the face, allowing distortion. This is the old Fit behavior.
- **Tile** repeats the image at block scale while preserving its aspect ratio.
- **Fit (aspect)** stretches the complete image only when its aspect ratio matches the surface; otherwise it uses Tile.

Existing saves preserve their previous Fit or Tile behavior. The choice is saved on configured items and copied by the Duplifier. Connected wall/glass artwork shares a stable origin across horizontal and vertical rows, including diagonal folds.

## Transparency assessment

Custom door faces use the sampled texture's alpha pixels, without a hardcoded mask for individual door types. They retain the programmable door's leaf geometry and collision. Cutout pixels can be visually transparent, but do not create passable holes; smooth partial-alpha glass is not reliably supported by the replacement-face rendering path. Other mods' doors receive upper/lower sampling when they inherit Minecraft's `BlockDoor`; other implementations use their model's representative texture. The source door's mesh and glass rendering are not imported. Native programmable-door presets use a predefined set of glass designs and a separate blended glass pass; selecting a replacement face disables that native glass pass.

Transparent artwork does not generate holes in block geometry or collision. In particular, solid chunk-rendered programmable blocks do not yet offer reliable transparent apertures: their solid render layer and opaque-neighbor culling assume a complete surface. Slabs and tile-rendered housings also retain their existing collision shapes, regardless of image alpha.

Generating a porthole-shaped block from an arbitrary alpha mask needs a defined alpha threshold, geometry extraction and simplification, matching collision/selection shapes, neighbor face-culling rules, render-layer handling and loaded-client/server agreement. That larger feature is deferred for a separate design decision. Use the existing programmable porthole shapes for actual openings. Importing transparent PNGs is allowed, but is not an alpha-derived geometry feature.

Dynmap reads the bundled material catalog for Programmable Blocks, Slabs, Stairs, Walls, Diagonal Walls, Trapdoors and programmable door leaves. Rectangular client sprites have matching map-readable crops. Filesystem textures and Custom materials retain the default panel fallback. Joined Fit/Stretch layouts and animated content remain in-game features. Rerender affected map tiles after upgrading.

Screen, input and console configuration dialogs preview artwork in their categorized list thumbnails. Separate preview panels are removed, including both Half-Console input panels. Off/Static/Animated modes, frame and size controls, and animation speed retain their existing behavior.

When a block uses a Custom sample, reopening its dialog selects and reveals the **Custom** row, with the sample name and current thumbnail. The sample remains selected until you choose another material or confirm a replacement in the Custom picker.

## Programmable armor

[Programmable armor](programmable-armor.md) uses the default block-material list, plus an **Armor** category with eight role designs and six company designs filtered to the held piece. Helmets additionally offer an Open variant for each role, and chestplates offer Short Sleeves; leggings and boots retain their standard role designs. New pieces use Civilian Staff. Block materials are centered on each visible armor face; native role atlases retain their own layout.

Shift-right-click a world block face with armor to copy the displayed artwork in creative or survival. The resolver reads the actual and extended model state, including programmable face overrides and storage top/side/front textures. This differs from the inventory Custom picker, which uses a block item's representative texture. The saved sample appears in the armor picker under **Sampled**; selecting another design replaces it.

Programmable armor also offers all fifteen light designs as separate static **On** and **Off** textures under **Lights**. These choices change artwork without emitting light. Use a Configurizer on an armor stand to edit a mounted piece directly.

The armor picker includes **Custom...** with the same non-consuming block/door inventory sampler as Programmable Blocks. These selections persist on the armor and copy unchanged through the Duplifier. Native armor role designs adapt to the destination armor piece instead.
## Runtime texture resolution

Built-in single-block artwork uses a maximum of 128 pixels per block edge.
Two-block doors use 128 by 256 pixels; square double-door artwork may use
256 by 256 pixels. Smaller native pixel-art textures retain their resolution.
Model UV atlases and animation sheets retain their authored layouts and frame
counts. Texture pickers expose one resolution; old saved size choices resolve
to the standard artwork without changing their material or design IDs.


## Company artwork

The top-level **Corp** category contains logos, backgrounds and murals for Pinnacle, Ashiro, Paladyne, Vektor, Seyr and Vandor. Murals retain their wide aspect ratio in picker previews. The main **Lights** category also contains six paired company light designs, available only in programmable light dialogs. Active designs use the company name without an On suffix. Select a company design for automatic matching Off artwork as the light switches, or use the OFF tab to choose a separate finish. General block, screen, tank and armor material menus omit these light-only pairs. Existing material IDs remain unchanged.


Programmable Blocks with the same block facing, visible-face material and layout
join their coplanar faces automatically. **Stretch** maps one complete image over
each rectangular group, including groups crossing a chunk border. **Fit (aspect)**
preserves the image proportions and contains the complete image inside that rectangle, centered with opaque dark margins. A square image on a 2x3 panel occupies 2x2 blocks, leaving half a block of margin above and below.
**Tile** repeats independently on each block. Different materials, facings or
layouts start a new group; irregular areas split into filled rectangles. Joined
scans are limited to 256 cells and rectangles of at most 16 blocks per side.
Other block shapes retain their individual layout rules described above.

Duplifier status messages report how many blocks received settings, including a
single-block application.

New Programmable Blocks default to **Tile**, including ordinary placement and
new command-placed blocks. Existing saved modes and configured item modes remain
unchanged.
