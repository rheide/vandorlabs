package com.vandorlabs.gear;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Validate the complete oriented footprint before changing any cell or consuming an item. */
public final class ItemLargeGear extends ItemBlock {
    public ItemLargeGear(LargeGearBlock block){super(block);}
    @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,BlockPos pos,EnumFacing side,float x,float y,float z,IBlockState state){
        EnumFacing facing=state.getValue(GearBlock.FACING);
        for(int n=0;n<4;n++){
            BlockPos at=pos.add(LargeGearBlock.offset(facing,n&1,n>>1));
            if(!GearAssembly.loaded(world,at)||!world.getBlockState(at).getBlock().isReplaceable(world,at)||!player.canPlayerEdit(at,side,stack)||!world.isBlockModifiable(player,at))return false;
        }
        return super.placeBlockAt(stack,player,world,pos,side,x,y,z,state);
    }
}
