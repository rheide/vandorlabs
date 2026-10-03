# Unified materials and Custom textures

Programmable storage, blocks, slabs, stairs, walls, trapdoors, doors, lights and static screen/control surfaces share a texture picker. Categories expand and collapse, and each texture has a larger thumbnail with its original aspect ratio. Categories and texture names are sorted alphabetically, and the current category heading stays visible while scrolling within it. The category containing the selected material opens automatically. Options are grouped and sorted once per dialog; expanding a category reuses those groups. Only visible rows draw thumbnails, using the already-loaded Minecraft atlas. PNGs are not loaded on demand, so large catalogs still increase startup time and atlas memory use.

Built-in categories include Materials, Panels, Tech, Hull, Industrial, Lights, Doors,
Screens, Trapdoors, Windows and Storage. Dark Hull 1–12 replace the old
coordinate labels; moved texture-pack choices no longer carry T1/T2 prefixes.
Removed finishes are omitted from the picker while their saved identifiers
and artwork remain available to existing builds. The Example filesystem
category is hidden when its only entry is the default sample panel.
Storage offers fourteen matching top/side/front sets; [Programmable
Storage](programmable-storage.md) uses all three faces and other shapes use
the front artwork. Blue Hex and Amber Hex are paired light finishes, with
separate lit and unlit artwork. Newly placed normal and diagonal trapdoors use **Armored Hatch** from Trapdoors. Existing saved selections retain their artwork. The Screens entries are static first frames; screen animations remain available in their original menus. Light entries have separate On and Off choices. The On choices retain their automatic unlit artwork when used on lights. Programmable light dialogs hide the separate Off choices in both lists; select the On material and let the light switch its artwork automatically. Their taller dialog shows up to seven textures plus the pinned category heading, temporarily reducing Minecraft's GUI scale when necessary to fit, and restoring that setting on close. Door entries provide Small, Medium and Large detail tiers as full door artwork, rather than separate upper/lower choices. Existing saved housing indices are preserved.

Hull groups exterior plating and protective cladding; Panels groups interior sheets and access covers. Industrial holds fourteen pipe, grille, rib, bulkhead and machinery finishes. Microchip and the Matter color variants are in Tech. The [complete category moves](texture-category-proposal.md) preserve saved selections.

## Choose a surface

Shift-right-click in Creative mode, or open the block's menu with the Configurizer. Choose a category and material. Lights have separate face and housing lists; triggers have Off and On lists. Blocks, slabs and storage offer optional per-face overrides. Storage faces that inherit the main finish retain its matching top/side/front artwork.

For screens, inputs and consoles, open **Surface textures** to choose a static front surface. Consoles also have a second surface choice. **Use screen / animation** restores the original configured screen or control panel. The native screen/control lists now use the same categorized thumbnail presentation, containing their original options. They still select the original animated screen or control family: Off/Static/Animated, speed and frame controls retain their behavior. Selecting a native option restores that surface from a static override. Housing lists use the shared material catalog.

Door presets retain the existing frame, motion and hardware options. Selecting a different material replaces the door's front/back leaf artwork while keeping the native edge textures and hinge hardware. **Face texture: Fit/Tile** fits a block image once over the complete leaf or repeats it at one-block scale; door artwork retains its upper/lower mapping. The layout survives saving, configured items and Duplifier copying. Ordinary programmable blocks use the lower square half of built-in door artwork, avoiding a stretched two-high image. Trapdoors offer **Texture: Tile / mirror** and **Fit**. Tile repeats one-block-wide, two-block-long door artwork, mirroring alternating columns; Fit maps one image across the complete group. Single-row door surfaces use the lower half. Custom `BlockDoor` artwork retains its upper/lower atlas sprites. The standard programmable door’s metal side artwork covers the four thin leaf edges in either layout.

## Custom block and door artwork

Scroll to **Custom...** in any shared material list. The picker shows your inventory and a sample slot. Drag a block or door into that slot, or click an inventory item and then **Use texture**. Nothing is consumed or moved in your real inventory.

For configured programmable items, the picker reads the material saved in the item, including Custom and filesystem choices. Other selected blocks' models supply their atlas texture. This supports vanilla and other mods' registered block items and door items. Block metadata is included in the saved choice. Vanilla doors and mod doors inheriting Minecraft's door block use upper and lower artwork for programmable door leaves and connected trapdoors. Ordinary blocks use the model's representative particle texture; the source block's mesh, tile settings and animation are not imported. Models without usable atlas artwork fall back to the default wall panel.

Custom choices survive saves, configured items and Duplifier copies. A client missing the referenced block uses the default panel while keeping the choice. Each client's resource pack determines the source artwork it sees.

To import PNG files instead, see [filesystem texture categories](filesystem-textures.md). Clients and servers may have different filesystem catalogs.

## Housing Fit/Tile

Slabs, stairs and lights keep their side-layout controls. Screens, half/full inputs and consoles offer **Sides: Fit/Tile** directly in their main dialogs and inside **Surface textures**. Programmable Half-Input labels its main control **Wall texture: Tile/Fit**. Fit scales the complete housing image onto each shortened housing face; Tile retains the original pixel-space UV layout. Existing screen/input/console saves retain Tile until changed. The choice is saved and copied by the Duplifier.

## Transparency assessment

Custom door faces use the sampled texture's alpha pixels, without a hardcoded mask for individual door types. They retain the programmable door's leaf geometry and collision. Cutout pixels can be visually transparent, but do not create passable holes; smooth partial-alpha glass is not reliably supported by the replacement-face rendering path. Other mods' doors receive upper/lower sampling when they inherit Minecraft's `BlockDoor`; other implementations use their model's representative texture. The source door's mesh and glass rendering are not imported. Native programmable-door presets use a predefined set of glass designs and a separate blended glass pass; selecting a replacement face disables that native glass pass.

Transparent artwork does not generate holes in block geometry or collision. In particular, solid chunk-rendered programmable blocks do not yet offer reliable transparent apertures: their solid render layer and opaque-neighbor culling assume a complete surface. Slabs and tile-rendered housings also retain their existing collision shapes, regardless of image alpha.

Generating a porthole-shaped block from an arbitrary alpha mask needs a defined alpha threshold, geometry extraction and simplification, matching collision/selection shapes, neighbor face-culling rules, render-layer handling and loaded-client/server agreement. That larger feature is deferred for a separate design decision. Use the existing programmable porthole shapes for actual openings. Importing transparent PNGs is allowed, but is not an alpha-derived geometry feature.

Dynmap's existing material table covers the original finishes. New static catalog artwork, filesystem textures and Custom materials use its default panel fallback; client rendering remains independent.

Screen, input and console configuration dialogs preview artwork in their categorized list thumbnails. Separate preview panels are removed, including both Half-Console input panels. Off/Static/Animated modes, frame and size controls, and animation speed retain their existing behavior.

When a block uses a Custom sample, reopening its dialog selects and reveals the **Custom** row, with the sample name and current thumbnail. The sample remains selected until you choose another material or confirm a replacement in the Custom picker.
