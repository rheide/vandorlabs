package com.vandorlabs.blocks;

import com.vandorlabs.cockpit.CockpitAssembly;
import com.vandorlabs.tiles.TileEntityCockpit;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.List;

/** Seated cockpit is a genuine Pilot Seat with authored multiblock collision. */
public final class BlockCockpit extends BlockPilotSeat {
    public final String design;
    public BlockCockpit(String design){super(design);this.design=design;setCreativeTab(null);}
    @Override public void getSubBlocks(net.minecraft.creativetab.CreativeTabs tab,NonNullList<ItemStack> items){}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityCockpit(design);}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){return state;}
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,Block block,BlockPos from){}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack){if(!world.isRemote)CockpitAssembly.install(world,pos,placer instanceof EntityPlayer?(EntityPlayer)placer:null);}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return CockpitAssembly.bounds(world,pos);}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return null;}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> boxes,Entity entity,boolean actual){CockpitAssembly.collision(world,pos,query,boxes);}
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){return CockpitAssembly.ray(world,pos,start,end);}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){return CockpitAssembly.activate(world,pos,player,hand);}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){CockpitAssembly.remove(world,pos,state.getValue(FACING));super.breakBlock(world,pos,state);}
    @Override public void getDrops(NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){ItemStack item=new ItemStack(this);TileEntity t=world.getTileEntity(pos);if(t instanceof TileEntityCockpit)item.setTagInfo("BlockEntityTag",t.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));drops.add(item);}
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult hit,World world,BlockPos pos,EntityPlayer player){NonNullList<ItemStack> drops=NonNullList.create();getDrops(drops,world,pos,state,0);return drops.get(0);}
}
