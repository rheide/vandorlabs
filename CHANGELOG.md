# Changelog

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
