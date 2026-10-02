package com.vandorlabs.items;

import com.vandorlabs.blocks.BlockProgrammableTrapdoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Preserve the middle click band in tile settings before grouping on placement. */
public final class ItemProgrammableTrapdoor extends ItemBlock {
    public ItemProgrammableTrapdoor(BlockProgrammableTrapdoor block){super(block);}
    public static int placementPosition(EnumFacing side,float hitY) {
        if(side==EnumFacing.UP)return 0;
        if(side==EnumFacing.DOWN)return 2;
        return hitY<1/3F?0:hitY>2/3F?2:1;
    }
    @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,BlockPos pos,EnumFacing side,float x,float y,float z,IBlockState state) {
        ItemStack configured=stack;
        if(stack.getSubCompound("BlockEntityTag")==null) {
            configured=stack.copy();NBTTagCompound tag=new NBTTagCompound();
            tag.setInteger("TrapdoorPosition",placementPosition(side,y));
            configured.setTagInfo("BlockEntityTag",tag);
        }
        return super.placeBlockAt(configured,player,world,pos,side,x,y,z,state);
    }
}
