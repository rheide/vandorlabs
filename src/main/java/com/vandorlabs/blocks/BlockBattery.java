package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityBattery;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class BlockBattery extends BlockVandorDirectional {
    public BlockBattery(){super("battery_block");}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityBattery();}
    private net.minecraft.item.ItemStack drop(TileEntity tile){
        net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(this);
        if(tile instanceof TileEntityBattery){net.minecraft.nbt.NBTTagCompound tag=tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound());tag.removeTag("id");tag.removeTag("x");tag.removeTag("y");tag.removeTag("z");stack.setTagInfo("BlockEntityTag",tag);}return stack;
    }
    @Override public void getDrops(net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> drops,net.minecraft.world.IBlockAccess world,BlockPos pos,IBlockState state,int fortune){drops.add(drop(world.getTileEntity(pos)));}
    @Override public void harvestBlock(World world,EntityPlayer player,BlockPos pos,IBlockState state,TileEntity tile,net.minecraft.item.ItemStack tool){spawnAsEntity(world,pos,drop(tile));}
    @Override public net.minecraft.item.ItemStack getPickBlock(IBlockState state,net.minecraft.util.math.RayTraceResult hit,World world,BlockPos pos,EntityPlayer player){return drop(world.getTileEntity(pos));}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(player.getHeldItem(hand).getItem() instanceof net.minecraft.item.ItemBlock)return false;
        if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_STORAGE_STATUS,world,pos.getX(),pos.getY(),pos.getZ());return true;
    }
}
