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
            world.setBlockToAir(pos);world.setBlockToAir(pos.south());
            for(int meta=0;meta<4;meta++)require(battery.getStateFromMeta(meta).getValue(com.vandorlabs.blocks.BlockBattery.FACING)==EnumFacing.getHorizontal(meta),"old battery orientation metadata");
            for(EnumFacing face:EnumFacing.values()){
                net.minecraft.block.state.IBlockState placed=battery.getStateForPlacement(world,pos,face,.5F,.5F,.5F,0,null);
                require(placed.getValue(com.vandorlabs.blocks.BlockBattery.FACING)==face&&battery.getStateFromMeta(battery.getMetaFromState(placed))==placed,"battery clicked-face/meta "+face);
                world.setBlockState(pos,placed);TileEntityBattery cell=(TileEntityBattery)world.getTileEntity(pos);
                require(cell.isBlockShape()&&!cell.hasCustomHousingTexture(),"old/default battery stays block shaped with legacy casing");
                cell.setHousingTexture(ScreenHousingTextures.DEFAULT_TRAPDOOR);
                TileEntityBattery savedCasing=new TileEntityBattery();savedCasing.readFromNBT(cell.writeToNBT(new NBTTagCompound()));
                require(savedCasing.hasCustomHousingTexture()&&savedCasing.getHousingTexture()==cell.getHousingTexture(),"battery casing persists on "+face);
                net.minecraft.item.ItemStack casingPick=battery.getPickBlock(placed,null,world,pos,null);
                require(casingPick.getSubCompound("BlockEntityTag").getInteger("HousingTexture")==cell.getHousingTexture(),"picked battery preserves casing on "+face);
                cell.getCapability(CapabilityEnergy.ENERGY,face).receiveEnergy(700,false);
                for(boolean block:new boolean[]{false,true}){
                    cell.setBlockShape(block);TileEntityBattery saved=new TileEntityBattery();saved.readFromNBT(cell.writeToNBT(new NBTTagCompound()));
                    require(saved.isBlockShape()==block&&saved.getCapability(CapabilityEnergy.ENERGY,null).getEnergyStored()==700,"battery shape/charge persistence "+face);
                    require(Math.abs(saved.chargeFraction()-.007)<1e-8,"live charge fraction");
                    for(EnumFacing side:EnumFacing.values())require(cell.hasCapability(CapabilityEnergy.ENERGY,side)==(side.getAxis()==face.getAxis()),"battery axial ports "+face);
                    net.minecraft.item.ItemStack picked=battery.getPickBlock(placed,null,world,pos,null);
                    require(picked.getSubCompound("BlockEntityTag").getBoolean("BlockShape")==block&&picked.getSubCompound("BlockEntityTag").getInteger("Energy")==700,"battery pick retains shape and charge");
                }
                world.setBlockToAir(pos);
            }
            world.setBlockState(pos,tank.getDefaultState().withProperty(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING,EnumFacing.UP));world.setBlockState(pos.up(),tank.getDefaultState().withProperty(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING,EnumFacing.UP));
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
            world.setBlockToAir(pos.up());
            for(EnumFacing face:EnumFacing.values()){
                net.minecraft.block.state.IBlockState placed=tank.getStateForPlacement(world,pos,face,.5F,.5F,.5F,0,null);
                require(placed.getValue(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING)==face,"tank clicked-face orientation "+face);
                world.setBlockState(pos,placed);world.setBlockState(pos.offset(face),placed);
                TileEntityHexTank one=(TileEntityHexTank)world.getTileEntity(pos);
                require(one.fill(new FluidStack(FluidRegistry.WATER,20000),true)==20000&&one.getTankProperties()[0].getCapacity()==32000,"connected tank capacity on "+face);
                require(one.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,face)&&!one.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,face.getAxis()==EnumFacing.Axis.Y?EnumFacing.NORTH:EnumFacing.UP),"axial ports "+face);
                for(boolean block:new boolean[]{true,false}){
                    one.setBlockShape(block);require(one.isBlockShape()==block&&one.getTankProperties()[0].getContents().amount==20000,"shape change retains connected fluid "+face);
                    TileEntityHexTank saved=new TileEntityHexTank();saved.readFromNBT(one.writeToNBT(new NBTTagCompound()));require(saved.isBlockShape()==block&&saved.tank.getFluidAmount()==one.tank.getFluidAmount(),"shape and fluid survive NBT "+face);
                    for(int layout:new int[]{1,2}){one.setMainTextureLayout(layout);saved.readFromNBT(one.writeToNBT(new NBTTagCompound()));require(saved.getMainTextureLayout()==layout&&saved.isBlockShape()==block&&one.getTankProperties()[0].getContents().amount==20000,"tank layout persists without changing storage or shape");}
                    require(one.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,face),"shape retains axial port "+face);
                }
                com.vandorlabs.container.ContainerStorageStatus meter=new com.vandorlabs.container.ContainerStorageStatus(one);require(meter.values[0]==20000&&meter.values[1]==32000,"live combined fluid meter");
                world.setBlockToAir(pos);world.setBlockToAir(pos.offset(face));
            }
            System.out.println("[vandorlabs][reprolab] utility-storage PASS sided energy simulation transfer persistence pooled-fluid split no-mixing");
        }finally{world.setBlockToAir(pos);world.setBlockToAir(pos.up());world.setBlockToAir(pos.south());}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
