package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.World;

/** Solid connected rails, with a separate low-alpha translucent glass pass. */
public class BlockSpaceGlass extends BlockGlassWall {
    public BlockSpaceGlass(String name) { super(name); }
    public String detail() {
        String id=getRegistryName().getResourcePath();
        return id.endsWith("_small")?"low":id.endsWith("_large")?"high":"medium";
    }
    @Override protected boolean canConnectTo(net.minecraft.block.Block other) { return other instanceof BlockSpaceGlass; }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new com.vandorlabs.tiles.TileEntitySpaceGlass();
    }
    @Override public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.SOLID; }
}
