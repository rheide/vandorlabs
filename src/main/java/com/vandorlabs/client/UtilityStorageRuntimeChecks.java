package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.BlockVandorDirectional;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.*;

final class UtilityStorageRuntimeChecks {
    static void run(World world){
        BlockPos pos=new BlockPos(145,230,145);
        Block battery=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:battery_block")),tank=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:hexagonal_fuel_tank"));
        try{
            world.setBlockState(pos,battery.getDefaultState());world.setBlockState(pos.south(),battery.getDefaultState());
            TileEntityBattery first=(TileEntityBattery)world.getTileEntity(pos),second=(TileEntityBattery)world.getTileEntity(pos.south());
            IEnergyStorage in=first.getCapability(CapabilityEnergy.ENERGY,EnumFacing.NORTH),out=first.getCapability(CapabilityEnergy.ENERGY,EnumFacing.SOUTH);
            require(in.receiveEnergy(2000,true)==1000 && in.getEnergyStored()==0,"simulated energy receive");
            require(in.receiveEnergy(2000,false)==1000 && in.extractEnergy(1000,false)==0 && out.receiveEnergy(1000,false)==0,"directional ports");
            require(!first.hasCapability(CapabilityEnergy.ENERGY,EnumFacing.UP),"side insulation");first.update();
            require(in.getEnergyStored()==0 && second.getCapability(CapabilityEnergy.ENERGY,null).getEnergyStored()==1000,"neighbor energy transfer conserves charge");
            TileEntityBattery restored=new TileEntityBattery();restored.readFromNBT(second.writeToNBT(new NBTTagCompound()));require(restored.getCapability(CapabilityEnergy.ENERGY,null).getEnergyStored()==1000,"energy persists");
            world.setBlockToAir(pos);world.setBlockToAir(pos.south());world.setBlockState(pos,tank.getDefaultState());world.setBlockState(pos.up(),tank.getDefaultState());
            TileEntityHexTank bottom=(TileEntityHexTank)world.getTileEntity(pos),top=(TileEntityHexTank)world.getTileEntity(pos.up());
            require(top.fill(new FluidStack(FluidRegistry.WATER,24000),false)==24000 && bottom.tank.getFluidAmount()==0,"simulated stack fill");
            require(top.fill(new FluidStack(FluidRegistry.WATER,24000),true)==24000 && bottom.tank.getFluidAmount()==16000 && top.tank.getFluidAmount()==8000,"pooled stack fill bottom first");
            require(top.getTankProperties()[0].getCapacity()==32000 && top.getTankProperties()[0].getContents().amount==24000,"shared capacity and volume");
            require(top.fill(new FluidStack(FluidRegistry.LAVA,1000),true)==0,"reject mixed liquids");
            require(top.drain(20000,false).amount==20000 && bottom.tank.getFluidAmount()==16000,"simulated stack drain");
            TileEntityHexTank copy=new TileEntityHexTank();copy.readFromNBT(top.writeToNBT(new NBTTagCompound()));require(copy.tank.getFluidAmount()==8000,"fluid persists per cell");
            require(bottom.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,EnumFacing.UP) && !bottom.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,EnumFacing.NORTH),"top and bottom ports only");
            world.setBlockToAir(pos);require(top.getTankProperties()[0].getCapacity()==16000 && top.tank.getFluidAmount()==8000,"stack split preserves remaining fluid");
            require(top.drain(new FluidStack(FluidRegistry.WATER,9000),true).amount==8000 && top.tank.getFluidAmount()==0,"drain clips to stored amount");
            require(top.fill(new FluidStack(FluidRegistry.LAVA,1000),true)==1000,"empty tank accepts another liquid");
            System.out.println("[vandorlabs][reprolab] utility-storage PASS sided energy simulation transfer persistence pooled-fluid split no-mixing");
        }finally{world.setBlockToAir(pos);world.setBlockToAir(pos.up());world.setBlockToAir(pos.south());}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
