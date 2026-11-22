package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Diagonal Corner Walls take the size of the diagonal wall they are placed against. */
public final class ItemCornerWall extends ItemBlock {
    public ItemCornerWall(BlockProgrammableCornerWall block){super(block);}

    @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,BlockPos pos,EnumFacing side,
            float hitX,float hitY,float hitZ,IBlockState state){
        BlockProgrammableCornerWall corner=(BlockProgrammableCornerWall)block;
        int size=corner.sloped()?corner.placementSize(world,pos.offset(side.getOpposite()),stack):-1;
        if(!super.placeBlockAt(stack,player,world,pos,side,hitX,hitY,hitZ,state))return false;
        if(size>=0&&world.getTileEntity(pos) instanceof TileEntityAnimatedScreenSelector)
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).setDiagonalGeometry(size,0);
        return true;
    }
}
