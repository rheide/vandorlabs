package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityWirelessRedstone;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** The port marks the only wired input/output; the opposite face marks wireless type. */
public final class BlockWirelessRedstone extends BlockVandor {
    public static final PropertyDirection FACING=PropertyDirection.create("facing");
    public final boolean transmitter;
    public BlockWirelessRedstone(boolean transmitter){super("redstone_"+(transmitter?"transmitter_block":"receiver"));this.transmitter=transmitter;setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH));}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING);}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.getFront(Math.floorMod(meta,6)));}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getIndex();}
    @Override public IBlockState withRotation(IBlockState state,Rotation rotation){return state.withProperty(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public IBlockState withMirror(IBlockState state,Mirror mirror){return state.withProperty(FACING,mirror.mirror(state.getValue(FACING)));}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing face,float x,float y,float z,int meta,EntityLivingBase placer){return getDefaultState().withProperty(FACING,EnumFacing.getDirectionFromEntityLiving(pos,placer));}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityWirelessRedstone();}
    @Override public boolean isSideSolid(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing side){return true;}
    @Override public boolean canProvidePower(IBlockState state){return true;}
    @Override public boolean canConnectRedstone(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing side){return side==state.getValue(FACING).getOpposite();}
    @Override public boolean shouldCheckWeakPower(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing side){return false;}
    @Override public int getWeakPower(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing side){TileEntity tile=world.getTileEntity(pos);if(!(tile instanceof TileEntityWirelessRedstone))return 0;TileEntityWirelessRedstone endpoint=(TileEntityWirelessRedstone)tile;if(transmitter)return com.vandorlabs.redstone.TransmitterSlots.localRedstone(tile)&&side==state.getValue(FACING)?endpoint.localSignalLevel(0):0;return side==state.getValue(FACING).getOpposite()?endpoint.getOutputLevel():0;}
    @Override public int getStrongPower(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing side){return getWeakPower(state,world,pos,side);}
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,net.minecraft.block.Block block,BlockPos from){if(!world.isRemote&&world.getTileEntity(pos) instanceof TileEntityWirelessRedstone)((TileEntityWirelessRedstone)world.getTileEntity(pos)).sampleInput();}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){if(hand!=EnumHand.MAIN_HAND)return false;if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_WIRELESS_REDSTONE,world,pos.getX(),pos.getY(),pos.getZ());return true;}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){super.breakBlock(world,pos,state);world.notifyNeighborsOfStateChange(pos,this,false);world.notifyNeighborsOfStateChange(pos.offset(state.getValue(FACING)),this,false);}
}
