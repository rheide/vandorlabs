package com.vandorlabs.blocks;

import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Distinct navigation computer and route-marker housings. */
public final class BlockAutopilot extends BlockVandorDirectional {
    private final boolean waypoint;
    public BlockAutopilot(boolean waypoint){super(waypoint?"waypoint":"autopilot");this.waypoint=waypoint;}
    public boolean hasTileEntity(IBlockState state){return true;}
    public TileEntity createTileEntity(World world,IBlockState state){return waypoint?new TileEntityWaypoint():new TileEntityAutopilot();}
    public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(hand!=EnumHand.MAIN_HAND||!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos))return false;
        if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_AUTOPILOT,world,pos.getX(),pos.getY(),pos.getZ());return true;
    }
    public void neighborChanged(IBlockState state,World world,BlockPos pos,Block block,BlockPos from){TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileEntityAutopilot && !(tile instanceof TileEntityWaypoint))((TileEntityAutopilot)tile).refreshPower();}
    public boolean canProvidePower(IBlockState state){return waypoint;}
    public int getWeakPower(IBlockState state,net.minecraft.world.IBlockAccess world,BlockPos pos,EnumFacing side){TileEntity raw=world.getTileEntity(pos);return raw instanceof TileEntityWaypoint&&((TileEntityWaypoint)raw).hasLocalRedstoneSignal()?15:0;}
    public void updateTick(World world,BlockPos pos,IBlockState state,java.util.Random random){TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntityWaypoint)((TileEntityWaypoint)raw).endPulse();else if(raw instanceof TileEntityAutopilot)com.vandorlabs.vehicle.VehicleAutopilot.depart((TileEntityAutopilot)raw);}
}
