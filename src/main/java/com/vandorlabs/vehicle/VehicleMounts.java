package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockTokenSlot;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Only readers physically mounted onto this controller qualify. Unloaded readers fail closed. */
public final class VehicleMounts {
    private VehicleMounts(){}
    public static TileEntityTokenSlot find(World world,BlockPos controller,boolean chip){
        for(EnumFacing side:EnumFacing.values()){
            BlockPos at=controller.offset(side);if(!world.isBlockLoaded(at))continue;IBlockState state=world.getBlockState(at);
            if(!(state.getBlock() instanceof BlockTokenSlot)||((BlockTokenSlot)state.getBlock()).chip!=chip||state.getValue(BlockTokenSlot.FACING)!=side)continue;
            TileEntity raw=world.getTileEntity(at);if(raw instanceof TileEntityTokenSlot)return (TileEntityTokenSlot)raw;
        }
        return null;
    }
    public static boolean allowed(World world,BlockPos at){
        TileEntity raw=world.getTileEntity(at);if(!(raw instanceof TileEntityVehicleController))return true;
        TileEntityVehicleController controller=(TileEntityVehicleController)raw;if(!controller.security)return true;
        TileEntityTokenSlot reader=find(world,at,false);if(reader==null||reader.returning()||reader.behavior!=0)return false;
        net.minecraft.item.ItemStack card=reader.getStackInSlot(0);
        return !card.isEmpty()&&card.getItem()==com.vandorlabs.items.ModItems.ACCESS_CARD&&card.getMetadata()==controller.card;
    }
    public static void requireAllowed(World world,BlockPos at){if(!allowed(world,at))throw new IllegalArgumentException("Insert the accepted Access Card and leave it in the Vehicle Controller mount");}
}
