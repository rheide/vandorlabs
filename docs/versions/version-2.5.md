# Vandor Labs 2.5

Version 2.5 fixes a dedicated-server compatibility crash when retracting modular
landing gear in Hover mode with Thaumic Augmentation installed.

Carried tiles live in a read-only simulation world. Previously, saving a changed
gear target could allocate a synthetic chunk and invoke global chunk capability
handlers. Thaumic Augmentation interpreted the remote simulation as a client world
and attempted to load a client-only class on the dedicated server.

Dirty tile updates now use the existing vehicle snapshot queue. The same handling
protects carried controls, seats, screens and other tiles. Replaced tile instances
cannot dirty the current snapshot, and comparator updates cannot call foreign
blocks inside the simulation. Solidity and opacity queries use captured blocks;
light and sky readers sample loaded parent-world positions.
Loaded-chunk lookups from global collision handlers also remain non-allocating,
including canopy discovery during a saved vehicle's gear movement.

Existing vehicles retain their saved configuration. Install 2.5 on the server and
clients. See [server compatibility](../server-compatibility.md) for the regression
checks and physical-server test options, and the [changelog](../../CHANGELOG.md)
for the complete changes.

## Bridge machines and construction guides

Dynmap also gains selected bundled materials for programmable blocks, slabs, stairs, walls and trapdoors; saved diagonal geometry; authored door frame/leaf geometry; and the complete canopy material palette. Atlas-aware PNG sharing prevents texture-file parsing failures. Existing map tiles need rerendering after the upgrade.

New decorative bridge equipment uses the existing multiblock and Data Chip Mount systems:

- Pinnacle Meridian Drive and Vector Fold Array.
- Vektor Longburn Drive, with its rectangular 3 × 4 footprint and recessed nozzle.
- Paladyne and Seyr Navigation Computers, built as three-block columns.
- Flat Navigation Tables and Angled Navigation Consoles, placed as matching modules that share one chip holder.

The drive and computer Cores use Programmable Matter Blocks and the same frame validation, corner detection and dismantling as the Universal Constructors. Each assembly has a [build guide with screenshots for every stage](../multiblocks/README.md), including guides for the existing Constructors.

Integrated chip holders reuse [Data Chip Mount behavior](../data-mounts.md), including company filters, channels and return animations. Drive activity follows chip-reader output. The navigation computers cycle through Off, Idle and Computing appearances, using their supplied animated textures. Screens remain available for future gameplay; these machines do not perform crafting, propulsion, navigation calculations or dimension transfer yet.

Hover steering now reverses its left/right response while travelling backwards. Stationary turning remains available.

See the [changelog](../../CHANGELOG.md) for the full changes.
