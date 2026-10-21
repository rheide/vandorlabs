package com.vandorlabs.items;

import com.vandorlabs.blocks.*;
import com.vandorlabs.cockpit.CockpitAssembly;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Validate the complete rotated reservation before vanilla places the anchor. */
public final class ItemCockpit extends ItemBlock {
    public ItemCockpit(Block block){super(block);}
    @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,BlockPos pos,EnumFacing side,float x,float y,float z,IBlockState state){
        String id=block instanceof BlockCockpit?((BlockCockpit)block).design:((BlockCockpitPeripheral)block).design;
        if(!CockpitAssembly.available(world,pos,state.getValue(BlockVandorDirectional.FACING),id,player,stack))return false;
        ItemStack configured=stack.copy();net.minecraft.nbt.NBTTagCompound tag=configured.getOrCreateSubCompound("BlockEntityTag");tag.setString("CockpitDesign",id);tag.removeTag("CockpitSeatSlide");tag.removeTag("CockpitScreenLift");tag.removeTag("CockpitScreenButton");tag.removeTag("CockpitSeatButton");
        return super.placeBlockAt(configured,player,world,pos,side,x,y,z,state);
    }
}
