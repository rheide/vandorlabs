package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityProgrammableTrigger;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Solid programmable cube with separate artwork for redstone off and on. */
public final class BlockProgrammableTrigger extends BlockProgrammableBlock {
    public BlockProgrammableTrigger() { super("programmable_trigger_block"); }

    @Override public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityProgrammableTrigger();
    }

    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos,
            net.minecraft.block.Block block, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, block, fromPos);
        if (!world.isRemote) world.notifyBlockUpdate(pos, state, state, 3);
    }
}
