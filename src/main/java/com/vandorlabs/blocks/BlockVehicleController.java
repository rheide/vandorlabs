package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Dedicated assembly and parking console; Pilot Seats keep their own settings. */
public final class BlockVehicleController extends BlockVandorDirectional {
    public BlockVehicleController(){super("vehicle_controller");setHardness(5);setResistance(10);setHarvestLevel("pickaxe",1);}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new com.vandorlabs.tiles.TileEntityVehicleController();}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing face,float x,float y,float z){
        if(hand!=EnumHand.MAIN_HAND || player.isSpectator())return false;
        if(!world.isRemote)com.vandorlabs.vehicle.VehicleService.previewController((net.minecraft.entity.player.EntityPlayerMP)player,pos);return true;
    }
}
