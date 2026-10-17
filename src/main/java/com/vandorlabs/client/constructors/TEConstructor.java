package com.vandorlabs.client.constructors;

import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class TEConstructor extends UniversalConstructorFastTESR<TileEntityConstructor> {
    @Override protected boolean isController(TileEntityConstructor tile){return tile.formed();}
    @Override protected String machineId(TileEntityConstructor tile){return tile.core().design;}
    @Override protected EnumFacing facing(TileEntityConstructor tile){return tile.facing();}
    @Override protected BlockPos originOffset(TileEntityConstructor tile){return BlockPos.ORIGIN.subtract(tile.pivot());}
}
