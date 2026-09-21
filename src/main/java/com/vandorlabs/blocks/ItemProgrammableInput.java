package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Item placement bridge for the half-input's three wall-height slots. */
public class ItemProgrammableInput extends ItemBlock {

    public ItemProgrammableInput(BlockProgrammableInput block) {
        super(block);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world,
            BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ,
            IBlockState newState) {
        if (!super.placeBlockAt(stack, player, world, pos, side,
                hitX, hitY, hitZ, newState)) {
            return false;
        }
        // Normal side placement is the wall-mounted mode. Sneaking and
        // top/bottom clicks retain the existing horizontal shelf behavior.
        if (side.getAxis().isHorizontal() && !player.isSneaking()
                && !newState.getValue(BlockProgrammableInput.KEYBOARD)) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityAnimatedScreenSelector) {
                ((TileEntityAnimatedScreenSelector) tile).setWallPosition(
                        BlockProgrammableInput.wallPositionForHit(hitY));
                IBlockState placed = world.getBlockState(pos);
                world.notifyBlockUpdate(pos, placed, placed, 3);
            }
        }
        return true;
    }
}
