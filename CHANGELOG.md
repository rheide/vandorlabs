# Changelog

## 1.3

See the [illustrated programmable block improvements](docs/gallery/task-improvements.md) and [filesystem texture setup](docs/filesystem-textures.md).

### Added

- Extra Large (2×2) landing-gear alignment with matching reservations and saved/copied size settings.
- Adjacent trapdoor Cover mode, preserving the cover tile outside the gear shaft and holding it open until retraction completes.
- Custom block/door texture sample slot across shared material menus, with vanilla/mod atlas artwork, upper/lower door mapping and non-consuming inventory selection.
- Fit/Tile housing controls for screens, half/full inputs and consoles. Static surface and material choices participate in Duplifier copying.

- Shared categorized texture picker with thumbnails across programmable materials, lights, doors and static screen/control surfaces. Adds full door artwork and static first-frame screen textures while preserving existing finish indices.
- Filesystem PNG categories under `config/vandorlabs/textures`, with first-run Example folder/sample panel, path-based saved identifiers and missing-client fallback. Different client/server catalogs are supported.

- Programmable lights offer centered Small/Full sizing and side-texture Fit/Tile. Small lights remain individual artwork panels.

- Loaded-only rectangular programmable trapdoor groups through 8×8, with shared outer hinges and scaled sliding travel. Diagonal trapdoors connect across opposite-slope rows and full-width vertical/depth offsets.

- Programmable Diagonal Trapdoor with wall-compatible half-width/full-height, full-width/full-height and full-width/half-height placement. Its 2px leaf leaves 1px clearance on each wall surface; pairs and 2×2 surface groups rotate or slide toward opposite sides. Shares textures, redstone channels, configuration and copying with programmable trapdoors.
- Programmable Trapdoor with 78 block finishes, Rotating or horizontal Sliding motion, Bottom/Middle/Top positions, physical redstone and virtual channels. Adjacent pairs and complete 2×2 squares open together toward opposite sides, without frames or hinge hardware. Includes Configurizer, Duplifier, configured items and Dynmap support.
- Non-rendering Minecraft/Forge regression checks for placement, diagonal vertex data, connected copying, ramp clearance and trapdoor geometry, grouping, persistence and redstone.

### Fixed

- Programmable light dialogs hide separate Off materials while keeping automatic on/off artwork, and grow to show up to seven textures plus the category heading.

- Native screen/control options use categorized thumbnail lists while retaining animation, framing and speed controls. Housing Fit/Tile is directly available in screen/console/input dialogs.
- Door artwork on ordinary blocks uses its square lower half. Replacement door faces offer saved/copied Fit/Tile and keep the native edge textures and hinges.

- Material lists use larger aspect-preserving thumbnails, alphabetic categories/entries and pinned category headings. Added explicit Off light choices without changing existing saved material numbers.
- Custom texture sampling reads configured programmable item materials; its Back/Use texture controls follow their revised order. Light sizing applies only to Frames, and propulsion dialogs use the list thumbnails instead of a separate housing preview.

- Replacement programmable-door artwork follows the native leaf bounds and retains selected hinge hardware, including bare/framed variants.

- Register custom atlas sprites before ordinary sprite registration, and reuse existing configured item geometry for the expanded material catalog.

- Light Frames sit against the supporting wall, floor or ceiling face. Their collision and rendering share the same bounds. Light Frame and Light Slab inventory models are centered, scaled to fit and show the lamp artwork.

- Rotating tall diagonal trapdoors open upward consistently across either slope.

- Configured Programmable Stairs now predict their saved appearance during client placement, matching programmable blocks and slabs.

- Plain Programmable Trapdoors select Bottom, Middle or Top from the clicked wall face thirds. Configured items retain their saved placement.
- Open programmable trapdoor leaves retain one pixel inside their own block, reducing overlap flicker against neighboring blocks for sliding and rotating motion, including diagonal leaves.

- Configured Programmable Blocks predict their saved texture during client placement, avoiding the default-finish flash while waiting for server tile data.
- Diagonal panels submit consistent winding, transformed normals and lightmap data for shader lighting. Hardware validation with Complementary Unbound 5.6.1 remains pending.
- Diagonal walls no longer disappear at the default tile-render distance; loaded chunks and the view frustum still bound rendering.
- Duplifier connected matching follows face, edge and corner neighbors, including vertically adjacent top/bottom slabs while preserving their halves.
- Ramp Controller clearance uses each source block's occupied bounds, allowing top slabs to descend 2.5 blocks onto the ground and bottom slabs to meet ceilings without accepting real obstructions.

## 1.2

### Added

- Duplifier's optional Connected Matching Blocks mode applies selected copied settings to a face-connected group with the exact same block type and original configuration, ignoring facing and rotation. It uses the supplied multi-block item icon, stays within loaded chunks and rejects groups larger than 4,096 occupied block cells before applying settings.
- 23 selected housing finishes from `textures2`, using the supplied 64x64 artwork and short descriptive menu names.
- 16 selected housing finishes from the shared `textures` artwork folder, with shorter names prefixed `T1`, available across Programmable Blocks and the other blocks that share the housing finish menu.
- 11 Hull Plating finishes from `hulls`, numbered 1–11 and available in the shared housing finish menu.
- The building guide now shows all 78 selectable finishes on Programmable Blocks, in groups of up to ten with names in display order.
- Extra Large Landing Gear doubles Large's model dimensions. Its centered mount and moving parts reserve a three-by-three footprint; extension remains configurable from 0–4 blocks.
- Programmable Light Frame, a one-pixel-deep light panel, and Programmable Light Slab, a half-height light with top and bottom placement. Both use Programmable Light artwork, brightness, joining, redstone channels and configuration.
- Ramp Controller start and end offset sliders with half-block steps from -16 to 16 blocks. The controller, moving cells, saved settings and copied items retain these intermediate positions.

### Fixed

- Removed the other 16 first-pack housing finishes and their packaged textures. Existing saved finish numbers are not migrated and may resolve to different artwork.
- Removed the other 23 second-pack housing finishes and their packaged textures. Existing saved finish numbers are not remapped.
- Extra Large Landing Gear now uses a smaller, centered hotbar icon that fits its slot.
- Dynmap now reads Programmable Slab halves, Programmable Door facing and open state, and the clipped shapes and saved main finish of deployed Ramp cells. Propulsion emitter faces have enough depth to remain visible on the map.
- Newly placed Programmable Slabs now use Tile for their side layout by default; saved Fit selections still render as Fit, and the inventory model matches the default.
- Filled, full-height Programmable Diagonal Walls now support Programmable Doors above them.
- Framed Programmable Doors align with the block boundary at either depth edge. Frameless doors keep one pixel of clearance so sliding leaves retract behind the neighboring block.
- Programmable Diagonal Walls now provide per-face normals for shader lighting.
- Defer programmable-light joining, saved-state channel changes and redstone-channel registration until after chunk tile loading finishes. Landing Gear and door load callbacks now use the same safe point.
- Ramp controllers, doors, displays and Landing Gear read power only from loaded neighboring chunks, preventing redstone checks from loading another chunk during a tile callback.
- Programmable Slabs report their full outer face as solid, so ceiling and floor attachments such as Mekanism Glow Panels can be placed on the matching half.
- Programmable Input and Programmable Half Input now show their screen on the underside when mounted beneath a ceiling.
- Ceiling-mounted Programmable Inputs, Programmable Half-Inputs and Programmable Half-Consoles now use near, middle or far positions across the ceiling face based on where it was clicked.
- Propulsion wall textures now cover the housing's sides, top, bottom, rear and front recess across the Rocket Thruster, Ion Drive, Plasma Vent, Impulse Engine and three hover fixtures. Their configuration dialog shows the selected texture.
- The live-client test lab now performs bulk integrated-server scene setup on the server thread, reducing its chunk-packet tile-map race during setup.
- Dynmap now has fallback geometry for programmable blocks and walls. Removed model entries for three texture-only names that Dynmap rejected as air.

## 1.1

See the [illustrated guide](docs/gallery/version-1.1.md) for examples and settings.

### Added

- Optional per-face texture overrides for Programmable Block, Slab and the new Programmable Stairs. Disabled by default; faces inherit the main texture unless overridden.
- Programmable Diagonal Portholes with four opening shapes, half/full width, half-height/full-width geometry, glass shade and joining controls.
- Half-height diagonal walls, independent Fill Inside/Outside controls, and a standalone Programmable Diagonal Half Console.
- Luxury and Military Seats with joining controls and adjustable height (±2 pixels). Default legs are one pixel taller than the original models.
- One configurable Landing Gear block with Small, Medium and Large sizes, animated extension/retraction, redstone modes and channels, and immediate size/length previews. Extension ranges from 0–4 blocks in half-block steps; all sizes use equally thick arms with tiled frame-interior metal.
- Glass Frame Interior and Door Interior finishes in programmable texture menus.
- Copifier/Duplifier crafting: combine a configured tool with programmable items to apply selected settings while retaining the tool. Copying also supports the new block options and face overrides.

### Changed

- Diagonal placement follows clicked edges; a dialog control reverses the slope. Tall and shallow portholes join along their diagonal, including compatible reversed pieces.
- Two stacked Programmable Slabs become a Programmable Block, preserving the placed slab's settings.
- Programmable Ramp texture matching is optional (enabled by default). Ramps accept Immersive Engineering slabs and preserve each moved block's finish.
- Programmable Door animation uses the client clock for smoother motion on slow servers.
- Diagonal Porthole and Luxury/Military Seat recipes use Industrial Alloy instead of Programmable Matter.
- Landing Gear uses the single `landing_gear` block ID; earlier side, static and separate-size variants were removed without remapping. Legacy Space Doors are hidden from the creative menu.
- Updated block guides, configuration documentation and screenshot galleries.

### Fixed

- Programmable Light housing showing through the artwork, including distant single/joined lights rendered through Forge's batched renderer.
- Missing or flickering glass at joined Circular Porthole seams.
- Diagonal corner fills extending past corners or removing connecting sections; filled shallow undersides now use the main texture with correct UVs.
- Diagonal orientation after WorldEdit flips, and BetterBuildersWands `/wandOops` undo for affected blocks.
- Programmable Door and Half Input placement on Programmable Slabs, plus door removal crashes.
- A one-pixel seam on the Programmable Ramp controller.
- Landing Gear retraction, obstruction/reservation cleanup and saved settings; seat and gear inventory icons fit their slots.
- TextureFix sprite cleanup crashes and startup crashes caused by redstone-light initialization loading neighbouring chunks.
- Copying a block with face overrides disabled leaves them disabled without replacing stored face textures.

## 1.0

- Initial release.
