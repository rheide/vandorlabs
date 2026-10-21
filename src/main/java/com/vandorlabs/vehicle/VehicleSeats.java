package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityProgrammableChair;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Passenger chairs share placement and height rules with their stationary mounts. */
public final class VehicleSeats {
    private VehicleSeats(){}
    public static boolean supports(IBlockState state){
        return state.getBlock() instanceof BlockPilotSeat || state.getBlock() instanceof BlockBridgeChair
            && "programmable_chair".equals(state.getBlock().getRegistryName().getResourcePath());
    }
    public static BlockPos lower(IBlockState state,BlockPos pos){
        return supports(state)&&state.getValue(BlockBridgeChair.UPPER)?pos.down():pos;
    }
    public static double height(IBlockAccess world,BlockPos pos){
        IBlockState state=world.getBlockState(pos);
        if(state.getBlock() instanceof BlockPilotSeat)return BlockPilotSeat.seatHeight(world,pos);
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);
        TileEntityProgrammableChair tile=raw instanceof TileEntityProgrammableChair?(TileEntityProgrammableChair)raw:null;
        return BlockBridgeChair.Style.byIndex(tile==null?0:tile.getStyle()).seatY+(tile==null?0:tile.getHeightOffsetPixels()/16D);
    }
}
