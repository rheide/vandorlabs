package com.vandorlabs.garage;

import com.vandorlabs.blocks.BlockVandorDirectional;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.List;

/** Native segments remain in place and retain ownership throughout opening. */
public final class GarageDoorBlock extends BlockVandorDirectional {
    public static final PropertyBool TOP=PropertyBool.create("top"),BOTTOM=PropertyBool.create("bottom"),BAKED=PropertyBool.create("baked");
    private static final AxisAlignedBB CLOSED=new AxisAlignedBB(0,0,.5-GarageDoorGeometry.HALF_THICKNESS,1,1,.5+GarageDoorGeometry.HALF_THICKNESS);
    public GarageDoorBlock(){super("garage_door");setDefaultState(getDefaultState().withProperty(TOP,true).withProperty(BOTTOM,true).withProperty(BAKED,false));setLightOpacity(0);setHardness(2.5F);}
    @Override protected BlockStateContainer createBlockState(){return new net.minecraftforge.common.property.ExtendedBlockState(this,new net.minecraft.block.properties.IProperty<?>[]{FACING,TOP,BOTTOM,BAKED},new net.minecraftforge.common.property.IUnlistedProperty<?>[]{com.vandorlabs.blocks.ProgrammableHousingState.FINISH});}
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos){TileEntity raw=world.getTileEntity(pos);return state instanceof net.minecraftforge.common.property.IExtendedBlockState?((net.minecraftforge.common.property.IExtendedBlockState)state).withProperty(com.vandorlabs.blocks.ProgrammableHousingState.FINISH,raw instanceof GarageDoorTile?((GarageDoorTile)raw).texture:-1):state;}
    @Override public boolean canPlaceBlockAt(World world,BlockPos pos){if(!super.canPlaceBlockAt(world,pos))return false;int height=1;for(EnumFacing side:new EnumFacing[]{EnumFacing.UP,EnumFacing.DOWN})for(BlockPos at=pos.offset(side);world.isBlockLoaded(at)&&world.getBlockState(at).getBlock()==this;at=at.offset(side))if(++height>GarageDoorTile.MAX_HEIGHT)return false;return true;}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new GarageDoorTile();}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){TileEntity tile=world.getTileEntity(pos);GarageDoorTile door=tile instanceof GarageDoorTile?(GarageDoorTile)tile:null;return state.withProperty(TOP,door==null||door.isRoot()).withProperty(BOTTOM,door==null||door.topOffset==door.height-1).withProperty(BAKED,door!=null&&door.clientBaked);}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing face,float x,float y,float z,int meta,EntityLivingBase placer){for(BlockPos other:new BlockPos[]{pos.up(),pos.down()})if(world.isBlockLoaded(other)&&world.getBlockState(other).getBlock()==this)return getDefaultState().withProperty(FACING,world.getBlockState(other).getValue(FACING));return super.getStateForPlacement(world,pos,face,x,y,z,meta,placer);}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack){super.onBlockPlacedBy(world,pos,state,placer,stack);changed(world,pos);}
    private static void changed(World world,BlockPos pos){if(world.isRemote)return;TileEntity tile=world.getTileEntity(pos);if(tile instanceof GarageDoorTile)((GarageDoorTile)tile).scheduleRefresh();}
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,net.minecraft.block.Block block,BlockPos from){changed(world,pos);}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){super.breakBlock(world,pos,state);changed(world,pos.up());changed(world,pos.down());}
    public static boolean configure(EntityPlayer player,World world,BlockPos pos){if(!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos))return false;if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_GARAGE_DOOR,world,pos.getX(),pos.getY(),pos.getZ());return true;}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing face,float x,float y,float z){
        if(hand!=EnumHand.MAIN_HAND||player.getHeldItem(hand).getItem() instanceof net.minecraft.item.ItemBlock)return false;
        if(player.isSneaking()||player.getHeldItem(hand).getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER)return configure(player,world,pos);
        TileEntity tile=world.getTileEntity(pos);if(!world.isRemote&&tile instanceof GarageDoorTile&&world.isBlockModifiable(player,pos))try{((GarageDoorTile)tile).toggle(player);}catch(IllegalArgumentException denied){player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(denied.getMessage()),true);}return true;
    }
    private static AxisAlignedBB rotated(AxisAlignedBB b,IBlockState state){return state.getValue(FACING).getAxis()==EnumFacing.Axis.X?new AxisAlignedBB(b.minZ,b.minY,b.minX,b.maxZ,b.maxY,b.maxX):b;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){TileEntity raw=world.getTileEntity(pos);GarageDoorTile tile=raw instanceof GarageDoorTile?(GarageDoorTile)raw:null;return rotated(tile!=null&&!tile.closed()?(tile.isRoot()?new AxisAlignedBB(0,.875,.375,1,1,.625):new AxisAlignedBB(0,0,0,0,0,0)):CLOSED,state);}
    @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> out,net.minecraft.entity.Entity entity,boolean actual){TileEntity raw=world.getTileEntity(pos);GarageDoorTile tile=raw instanceof GarageDoorTile?(GarageDoorTile)raw:null;if(tile==null||tile.closed())addCollisionBoxToList(pos,query,out,rotated(CLOSED,state));else if(tile.isRoot())addCollisionBoxToList(pos,query,out,rotated(new AxisAlignedBB(0,.875,.375,1,1,.625),state));}
}
