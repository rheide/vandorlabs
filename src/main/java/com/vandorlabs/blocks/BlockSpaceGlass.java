package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.World;

/** Solid connected rails, with a separate low-alpha translucent glass pass. */
public class BlockSpaceGlass extends BlockGlassWall {
    public BlockSpaceGlass(String name) { super(name); }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new com.vandorlabs.tiles.TileEntitySpaceGlass();
    }
    @Override public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.SOLID; }
}
