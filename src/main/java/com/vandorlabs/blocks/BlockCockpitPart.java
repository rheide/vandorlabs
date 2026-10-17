package com.vandorlabs.blocks;

import com.vandorlabs.cockpit.CockpitAssembly;
import com.vandorlabs.tiles.TileEntityCockpitPart;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.List;

/** Invisible occupied cell; collision and interaction resolve the owning cockpit. */
public final class BlockCockpitPart extends BlockVandor {
    public BlockCockpitPart(){super("cockpit_part");setCreativeTab(null);setLightOpacity(0);}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityCockpitPart();}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return CockpitAssembly.bounds(world,pos);}
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return null;}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> boxes,Entity entity,boolean actual){CockpitAssembly.collision(world,pos,query,boxes);}
    @Override public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){return CockpitAssembly.ray(world,pos,start,end);}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){return CockpitAssembly.activate(world,pos,player,hand);}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){CockpitAssembly.remove(world,pos);super.breakBlock(world,pos,state);}
    @Override public void getDrops(NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){}
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult hit,World world,BlockPos pos,EntityPlayer player){com.vandorlabs.tiles.TileEntityCockpit root=CockpitAssembly.root(world,pos);return root==null?ItemStack.EMPTY:world.getBlockState(root.getPos()).getBlock().getPickBlock(world.getBlockState(root.getPos()),hit,world,root.getPos(),player);}
}
