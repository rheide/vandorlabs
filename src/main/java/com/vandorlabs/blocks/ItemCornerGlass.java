package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Diagonal corners take the size of the diagonal block they are placed against. */
public final class ItemCornerGlass extends ItemBlock {
    public ItemCornerGlass(BlockProgrammableCornerGlass block){super(block);}

    @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,BlockPos pos,EnumFacing side,
            float hitX,float hitY,float hitZ,IBlockState state){
        BlockProgrammableCornerGlass corner=(BlockProgrammableCornerGlass)block;
        int size=corner.sloped()?corner.placementSize(world,pos.offset(side.getOpposite()),stack):-1;
        if(!super.placeBlockAt(stack,player,world,pos,side,hitX,hitY,hitZ,state))return false;
        if(size>=0&&world.getTileEntity(pos) instanceof TileEntityProgrammableGlass)
            ((TileEntityProgrammableGlass)world.getTileEntity(pos)).setCornerMode(size);
        return true;
    }
}
