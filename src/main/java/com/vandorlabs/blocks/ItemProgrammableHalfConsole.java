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

/** Preserves the underside click position for a ceiling-mounted half console. */
public final class ItemProgrammableHalfConsole extends ItemBlock {
    public ItemProgrammableHalfConsole(BlockProgrammableHalfConsole block) { super(block); }

    @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,
            BlockPos pos,EnumFacing side,float hitX,float hitY,float hitZ,IBlockState state) {
        if (!super.placeBlockAt(stack,player,world,pos,side,hitX,hitY,hitZ,state)) return false;
        if (side==EnumFacing.DOWN) {
            TileEntity tile=world.getTileEntity(pos);
            if (tile instanceof TileEntityAnimatedScreenSelector) {
                TileEntityAnimatedScreenSelector selector=(TileEntityAnimatedScreenSelector)tile;
                selector.setCeilingMounted(true);
                selector.setCeilingPosition(CeilingPlacement.position(
                        state.getValue(BlockAnimatedScreenSelector.FACING),hitX,hitZ));
            }
        }
        return true;
    }
}
