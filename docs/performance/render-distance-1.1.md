# Render distance in 1.1

## Finding

Programmable Block and Programmable Slab already use chunk-baked models in this
checkout. Their tile render passes are disabled. Their visible faces therefore
follow the loaded terrain render distance, like ordinary blocks. Increasing the
tile-entity render distance would not change these two blocks.

Programmable Diagonal Wall, Diagonal Screen, portholes and the new diagonal half
console still use tile-entity rendering. Forge 1.12.2's `TileEntity` returns
`4096` from `getMaxRenderDistanceSquared()`; `TileEntityRendererDispatcher`
compares that with camera distance. These blocks consequently disappear around
64 blocks away even while surrounding terrain remains visible.

Verified against the locally installed Forge 1.12.2 source and the current
`ProgrammableHousingModel`, `ModBlocks.onModelBake`, and
`TileEntityAnimatedScreenSelector.shouldRenderInPass` implementations.

## Recommendation

Move opaque static diagonal housing into chunk meshes, following the block/slab
implementation. Pass an immutable snapshot of shape, joining, finish and fill
settings into the model. Keep animated screen artwork and translucent porthole
glass in their separate rendering paths. Cache reusable geometry and rebuild
chunks when configuration or neighbors change.

This is a separate rendering change with substantial visual verification:
joined corners, inversion, height/width, fill, light sampling, texture alignment,
transparent glass and resource reloads all need coverage. The cost comes mainly
from that work, rather than new block IDs.

Raising the tile range to 128 blocks is a small code change, but can draw up to
four times as many fixtures across a uniformly populated horizontal area. It
adds per-frame work and does not give these blocks the terrain rendering path.
The render distance remains unchanged.

Full cubes disappearing beyond a distance threshold require a separate
compatibility check with the active renderer mods and settings: the tile
renderer cutoff does not apply to their terrain meshes.
