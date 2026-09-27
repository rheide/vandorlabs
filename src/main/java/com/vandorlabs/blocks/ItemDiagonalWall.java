package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Plain items continue the clicked diagonal's proportions; configured items keep theirs. */
public final class ItemDiagonalWall extends ItemBlock {
    public ItemDiagonalWall(BlockProgrammableWall block) { super(block); }

    @Override public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world,
            BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, IBlockState state) {
        int mode = ((BlockProgrammableWall) block).placementGeometry(world,
                pos.offset(side.getOpposite()), stack);
        if (!super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, state)) return false;
        if (stack.getSubCompound("BlockEntityTag") == null
                && world.getTileEntity(pos) instanceof TileEntityAnimatedScreenSelector) {
            ((TileEntityAnimatedScreenSelector) world.getTileEntity(pos)).setDiagonalGeometry(mode, 0);
        }
        return true;
    }
}
