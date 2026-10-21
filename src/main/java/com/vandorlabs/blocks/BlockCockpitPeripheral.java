package com.vandorlabs.blocks;

import com.vandorlabs.cockpit.CockpitAssembly;
import com.vandorlabs.tiles.TileEntityCockpit;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.List;

/** Screen/yoke peripheral exposes the same surface controls without adding a seat. */
public final class BlockCockpitPeripheral extends BlockVandorDirectional {
    public final String design;
    public BlockCockpitPeripheral(String design){super(design);this.design=design;setLightOpacity(0);}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing face,float x,float y,float z,int meta,EntityLivingBase placer){return getDefaultState().withProperty(FACING,placer.getHorizontalFacing());}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityCockpit(design);}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return CockpitAssembly.bounds(world,pos);}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return null;}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> boxes,Entity entity,boolean actual){CockpitAssembly.collision(world,pos,query,boxes);}
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){return CockpitAssembly.ray(world,pos,start,end);}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack){if(!world.isRemote)CockpitAssembly.install(world,pos,placer instanceof EntityPlayer?(EntityPlayer)placer:null);}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){return CockpitAssembly.activate(world,pos,player,hand);}
    @Override public void getDrops(NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){ItemStack item=new ItemStack(this);TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileEntityCockpit)item.setTagInfo("BlockEntityTag",tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));drops.add(item);}
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult hit,World world,BlockPos pos,EntityPlayer player){NonNullList<ItemStack> drops=NonNullList.create();getDrops(drops,world,pos,state,0);return drops.get(0);}
}
