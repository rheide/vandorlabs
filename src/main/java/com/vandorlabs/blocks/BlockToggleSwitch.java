package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.tileentity.TileEntity;

/** Compact split rocker using the shared latch and configurable solid mounting base. */
public final class BlockToggleSwitch extends BlockVandorSwitch {
    public BlockToggleSwitch(String name){super(name,false);}
    @Override protected BlockStateContainer createBlockState(){return new net.minecraftforge.common.property.ExtendedBlockState(this,new net.minecraft.block.properties.IProperty[]{FACING,ON,ROTATION},new net.minecraftforge.common.property.IUnlistedProperty[]{MountedControlGeometry.MOUNT});}
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos){return ((net.minecraftforge.common.property.IExtendedBlockState)state).withProperty(MountedControlGeometry.MOUNT,MountedControlGeometry.mount(world,pos));}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return MountedControlGeometry.bounds(state,world,pos);}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return getBoundingBox(state,world,pos);}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,java.util.List<AxisAlignedBB> boxes,net.minecraft.entity.Entity entity,boolean actual){for(AxisAlignedBB box:MountedControlGeometry.boxes(state,world,pos))addCollisionBoxToList(pos,query,boxes,box);}
    private ItemStack configured(IBlockAccess world,BlockPos pos){ItemStack stack=new ItemStack(this);TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileEntityRedstoneChannel)stack.setTagInfo("RedstoneChannelSettings",((TileEntityRedstoneChannel)tile).configuration());return stack;}
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult ray,World world,BlockPos pos,EntityPlayer player){return configured(world,pos);}
    @Override public void getDrops(NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){drops.add(configured(world,pos));}
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean willHarvest){return willHarvest || super.removedByPlayer(state,world,pos,player,false);}
    @Override public void harvestBlock(World world,EntityPlayer player,BlockPos pos,IBlockState state,TileEntity tile,ItemStack tool){super.harvestBlock(world,player,pos,state,tile,tool);world.setBlockToAir(pos);}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase player,ItemStack stack){super.onBlockPlacedBy(world,pos,state,player,stack);NBTTagCompound settings=stack.getSubCompound("RedstoneChannelSettings");TileEntity tile=world.getTileEntity(pos);if(!world.isRemote && settings!=null && tile instanceof TileEntityRedstoneChannel)((TileEntityRedstoneChannel)tile).readMount(settings);}
}
