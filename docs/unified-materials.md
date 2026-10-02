# Unified materials and Custom textures

Programmable blocks, slabs, stairs, walls, trapdoors, doors, lights and static screen/control surfaces share a texture picker. Categories expand and collapse, and each texture has a small thumbnail. The category containing the selected material opens automatically.

Built-in categories include Materials, Texture Pack 1, Texture Pack 2, Hull Plating, Lights, Doors and Screens. The Screens entries are static first frames; screen animations remain available in their original menus. Light entries include their On/Off artwork. Door entries provide Small, Medium and Large detail tiers as full door artwork, rather than separate upper/lower choices. Existing saved housing indices are preserved.

## Choose a surface

Shift-right-click in Creative mode, or open the block's menu with the Configurizer. Choose a category and material. Lights have separate face and housing lists; triggers have Off and On lists. Blocks and slabs retain their optional per-face overrides.

For screens, inputs and consoles, open **Surface textures** to choose a static front surface. Consoles also have a second surface choice. **Use screen / animation** restores the original configured screen or control panel. The original animation controls remain available. Housing lists use the same catalog.

Door presets retain the existing frame, motion and hardware options. Selecting a different material replaces the door's leaf artwork. Connected trapdoors map built-in full door artwork over the connected surface; a single trapdoor uses its lower half. Custom doors likewise use upper and lower atlas artwork across the connected surface.

## Custom block and door artwork

Scroll to **Custom...** in any shared material list. The picker shows your inventory and a sample slot. Drag a block or door into that slot, or click an inventory item and then **Use texture**. Nothing is consumed or moved in your real inventory.

The selected block's model supplies its atlas texture. This supports vanilla and other mods' registered block items and door items. Block metadata is included in the saved choice. Vanilla doors and mod doors inheriting Minecraft's door block use upper and lower artwork for programmable door leaves and connected trapdoors. Ordinary blocks use the model's representative particle texture; the source block's mesh, tile settings and animation are not imported. Models without usable atlas artwork fall back to the default wall panel.

Custom choices survive saves, configured items and Duplifier copies. A client missing the referenced block uses the default panel while keeping the choice. Each client's resource pack determines the source artwork it sees.

To import PNG files instead, see [filesystem texture categories](filesystem-textures.md). Clients and servers may have different filesystem catalogs.

## Housing Fit/Tile

Slabs, stairs and lights keep their side-layout controls. Screens, half/full inputs and consoles now offer **Sides: Fit/Tile** inside **Surface textures**. Fit scales the complete housing image onto each shortened housing face; Tile retains the original pixel-space UV layout. Existing screen/input/console saves retain Tile until changed. The choice is saved and copied by the Duplifier.

## Transparency assessment

Transparent artwork does not generate holes in block geometry or collision. In particular, solid chunk-rendered programmable blocks do not yet offer reliable transparent apertures: their solid render layer and opaque-neighbor culling assume a complete surface. Slabs and tile-rendered housings also retain their existing collision shapes, regardless of image alpha.

Generating a porthole-shaped block from an arbitrary alpha mask needs a defined alpha threshold, geometry extraction and simplification, matching collision/selection shapes, neighbor face-culling rules, render-layer handling and loaded-client/server agreement. That larger feature is deferred for a separate design decision. Use the existing programmable porthole shapes for actual openings. Importing transparent PNGs is allowed, but is not an alpha-derived geometry feature.

Dynmap's existing material table covers the original finishes. New static catalog artwork, filesystem textures and Custom materials use its default panel fallback; client rendering remains independent.
