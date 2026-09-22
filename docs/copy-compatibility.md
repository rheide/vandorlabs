# World-copy compatibility

Supported/tested external builds:

- WorldEdit Forge 6.1.10 for Minecraft 1.12.2
- Better Builder's Wands 0.11.1 (`245+69d0d70`)

## WorldEdit

`ForgeWorld.getBlock` serializes a block as its numeric ID and metadata. If a
tile entity exists, `TileEntityBaseBlock` also calls `writeToNBT`. On paste,
`ForgeWorld.setBlock` restores the state with `getStateFromMeta`, rewrites the
NBT coordinates, and recreates the tile entity. Vandor Labs therefore remains
compatible by keeping its metadata codecs lossless and all programmable
settings in `TileEntityAnimatedScreenSelector.writeToNBT/readFromNBT`.

WorldEdit 6.1.10 uses its own vanilla-only direction registry for `//rotate`.
When WorldEdit is installed, Vandor Labs adds the facing metadata for every
Vandor Labs door and propulsion block to that registry after block registration.
The upper half of a door keeps its hinge, open, and power bits; its facing comes
from the rotated lower half. Triangular thrusters also rotate their corner block
variant on floors and ceilings. WorldEdit is optional: the integration loads only
when that mod is present and does not modify its jar or other installed files.

A WorldEdit selection must contain every cell of a multi-cell block. In
particular, copy both the lower and upper cell of a door or chair; copying one
cell is invalid for the same reason that half of a vanilla door is invalid.

## Better Builder's Wands

BBW obtains an item with `getPickBlock`, converts the item damage back to an
`IBlockState`, and writes that state directly with `World.setBlockState`. It
does not read or write tile entities. Consequently, placement-only state can
be lost and a programmable block otherwise receives a fresh, default tile.

`BetterBuildersWandsCompat` listens for a BBW right-click on a Vandor Labs block.
After BBW finishes, it consumes BBW's own `bbw.lastPlaced` destination list and
restores the clicked block's exact state and tile NBT at only those positions.
It also reconstructs both cells of Vandor Labs doors and chairs. The integration is
reflection-free and has no hard dependency, so Vandor Labs loads normally without
BBW installed.

Programmable blocks open a GUI on an ordinary empty-hand right-click. As with
other interactive Minecraft blocks, sneak-right-click with the wand when the
GUI would otherwise consume the interaction.

## Regression tests

- `tools/test_copy_compat.py` disassembles the installed jars and pins the
  external metadata/NBT and destination-journal contracts used above.
- `CopyCompatibilityRuntimeChecks` validates every Vandor Labs metadata codec, then
  runs representative programmable-block and two-cell door copies through the
  WorldEdit adapter and BBW compatibility boundary in the live client. It also
  runs WorldEdit's actual 90-degree clipboard block transform for every door and
  propulsion ID, including floor/ceiling triangle corners, and pastes
  representative rotated blocks through the WorldEdit adapter.
- `testclient/test_viewscreen.sh` installs both test jars into its isolated
  client and requires the `copy-compat-runtime PASS` marker.
