package com.vandorlabs.gear;

import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.gear.core.GearMotion;
import java.util.function.Function;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

final class ArmResourceTransfer {
    private final GearTileEntity input;
    private IEnergyStorage energy;
    private IFluidHandler fluid;
    private IItemHandler items;
    private GearMotion.Pose mappedPose;
    private ArmPayload mappedPayload;
    private ArmPayload.Endpoint emptyEndpoint;
    private ArmGridMapping payloadEndpoint;

    ArmResourceTransfer(GearTileEntity input) {
        this.input = input;
    }

    static ArmResource forCapability(Capability<?> capability) {
        if (capability == null) return ArmResource.DISABLED;
        if (capability == CapabilityEnergy.ENERGY) return ArmResource.POWER;
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return ArmResource.FLUID;
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return ArmResource.ITEM;
        return ArmResource.DISABLED;
    }

    static <T> T input(GearTileEntity tile, EnumFacing side, Capability<T> capability) {
        ArmResource type = forCapability(capability);
        if (type == ArmResource.DISABLED || side == null || side != tile.mountDirection().getOpposite()) return null;
        GearTileEntity arm = installedArm(tile, type);
        if (arm == null) return null;
        ArmResourceTransfer transfer = tile.resourceConnection();
        switch (type) {
            case POWER:
                if (transfer.energy == null) transfer.energy = transfer.new Power(side);
                return CapabilityEnergy.ENERGY.cast(transfer.energy);
            case FLUID:
                if (transfer.fluid == null) transfer.fluid = transfer.new Fluid(side);
                return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(transfer.fluid);
            case ITEM:
                if (transfer.items == null) transfer.items = transfer.new Items(side);
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(transfer.items);
            default:
                return null;
        }
    }

    private static GearTileEntity installedArm(GearTileEntity input, ArmResource type) {
        if (!(input.getWorld() instanceof WorldServer) || input.isInvalid()) return null;
        WorldServer world = (WorldServer) input.getWorld();
        BlockPos mounting = input.getPos();
        if (!world.getMinecraftServer().isCallingFromMinecraftThread() || !GearAssembly.loaded(world,mounting)
                || world.getTileEntity(mounting) != input) return null;
        GearTileEntity arm = input.loadedController();
        if (arm == null || arm.isInvalid() || arm.resourceTransfer() != type || !arm.canTransferResources()
                || arm.mountDirection() != input.mountDirection()
                || !arm.columns().contains(mounting.subtract(arm.getPos()))) return null;
        return arm;
    }

    private void map(GearTileEntity arm) {
        GearMotion.Pose pose = arm.pose(arm.progress());
        ArmPayload payload = arm.payload();
        if (mappedPose == pose && mappedPayload == payload) return;
        emptyEndpoint = payload == null ? ArmPayload.endpoint(arm,arm.progress()) : null;
        payloadEndpoint = payload != null ? payload.gridMapping(arm,arm.progress()) : null;
        mappedPose = pose;
        mappedPayload = payload;
    }

    private <T, R> R send(ArmResource type, EnumFacing side, Capability<T> capability, R unavailable, Function<T,R> action) {
        return ArmResource.forward(input,unavailable,() -> {
            if (side != input.mountDirection().getOpposite()) return unavailable;
            GearTileEntity arm = installedArm(input,type);
            if (arm == null || !arm.resourceEndpointAvailable()) return unavailable;
            WorldServer world = (WorldServer) input.getWorld();
            BlockPos mounting = input.getPos();
            BlockPos root = arm.getPos();
            if (ArmTransactions.lockedForResourceTransfer(world,root)
                    || !root.equals(mounting) && ArmTransactions.lockedForResourceTransfer(world,mounting)) return unavailable;
            BlockPos contact = mounting.offset(arm.mountDirection(),arm.assembly().columnHeight());
            EnumFacing face = side;
            if (!arm.isFixedMount()) {
                ArmResourceTransfer mapping = arm.resourceConnection();
                mapping.map(arm);
                if (mapping.payloadEndpoint != null) {
                    ArmGridMapping endpoint = mapping.payloadEndpoint;
                    contact = endpoint.cell(contact.subtract(root).subtract(arm.payload().offset));
                    face = endpoint.facing(side);
                } else if (mapping.emptyEndpoint != null) {
                    ArmPayload.Endpoint endpoint = mapping.emptyEndpoint;
                    contact = endpoint.placed(contact);
                    face = endpoint.mappedFacing(side);
                } else return unavailable;
            }
            if (!GearAssembly.loaded(world,contact) || ArmTransactions.lockedForResourceTransfer(world,contact)
                    || com.vandorlabs.copier.CopyTransactions.locked(world,contact)) return unavailable;
            TileEntity target = world.getTileEntity(contact);
            if (target == null || target.isInvalid() || target.getWorld() != world || !contact.equals(target.getPos())) return unavailable;
            T destination = target.getCapability(capability,face);
            return destination == null ? unavailable : action.apply(destination);
        });
    }

    private final class Power implements IEnergyStorage {
        private final EnumFacing side;
        Power(EnumFacing side) { this.side = side; }
        @Override public int receiveEnergy(int amount, boolean simulate) {
            if (amount <= 0) return 0;
            return send(ArmResource.POWER,side,CapabilityEnergy.ENERGY,0,target ->
                    Math.min(amount,Math.max(0,target.receiveEnergy(amount,simulate))));
        }
        @Override public boolean canReceive() {return send(ArmResource.POWER,side,CapabilityEnergy.ENERGY,false,IEnergyStorage::canReceive);}
        @Override public int extractEnergy(int amount, boolean simulate) {return 0;}
        @Override public int getEnergyStored() {return 0;}
        @Override public int getMaxEnergyStored() {return 0;}
        @Override public boolean canExtract() {return false;}
    }

    private final class Fluid implements IFluidHandler {
        private final EnumFacing side;
        private final IFluidTankProperties[] unavailable = new IFluidTankProperties[0];
        Fluid(EnumFacing side) { this.side = side; }
        @Override public int fill(FluidStack resource, boolean commit) {
            if (resource == null || resource.amount <= 0) return 0;
            int amount = resource.amount;
            return send(ArmResource.FLUID,side,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,0,target ->
                    Math.min(amount,Math.max(0,target.fill(resource.copy(),commit))));
        }
        @Override public IFluidTankProperties[] getTankProperties() {
            return send(ArmResource.FLUID,side,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,unavailable,target -> {
                IFluidTankProperties[] properties = target.getTankProperties();
                if (properties == null || properties.length == 0) return unavailable;
                IFluidTankProperties[] result = new IFluidTankProperties[properties.length];
                for (int index=0; index<result.length; index++) result[index] = new InputTank(index);
                return result;
            });
        }
        @Override public FluidStack drain(FluidStack resource, boolean commit) {return null;}
        @Override public FluidStack drain(int amount, boolean commit) {return null;}

        private final class InputTank implements IFluidTankProperties {
            private final int index;
            InputTank(int index) {this.index=index;}
            private <R> R property(R unavailable, Function<IFluidTankProperties,R> read) {
                return send(ArmResource.FLUID,side,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,unavailable,target -> {
                    IFluidTankProperties[] properties=target.getTankProperties();
                    return properties!=null && index<properties.length && properties[index]!=null
                            ? read.apply(properties[index]) : unavailable;
                });
            }
            @Override public FluidStack getContents() {
                return property(null,tank -> {FluidStack fluid=tank.getContents();return fluid==null?null:fluid.copy();});
            }
            @Override public int getCapacity() {return property(0,IFluidTankProperties::getCapacity);}
            @Override public boolean canFill() {return property(false,IFluidTankProperties::canFill);}
            @Override public boolean canDrain() {return false;}
            @Override public boolean canFillFluidType(FluidStack resource) {
                return resource!=null && property(false,tank -> tank.canFillFluidType(resource));
            }
            @Override public boolean canDrainFluidType(FluidStack resource) {return false;}
        }
    }

    private final class Items implements IItemHandler {
        private final EnumFacing side;
        Items(EnumFacing side) { this.side = side; }
        @Override public int getSlots() {
            return send(ArmResource.ITEM,side,CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,0,IItemHandler::getSlots);
        }
        @Override public ItemStack getStackInSlot(int slot) {
            return send(ArmResource.ITEM,side,CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,ItemStack.EMPTY,
                    target -> validSlot(target,slot) ? target.getStackInSlot(slot).copy() : ItemStack.EMPTY);
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;
            return send(ArmResource.ITEM,side,CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,stack,
                    target -> validSlot(target,slot) ? target.insertItem(slot,stack,simulate) : stack);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {return ItemStack.EMPTY;}
        @Override public int getSlotLimit(int slot) {
            return send(ArmResource.ITEM,side,CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,0,
                    target -> validSlot(target,slot) ? target.getSlotLimit(slot) : 0);
        }
        private boolean validSlot(IItemHandler target,int slot) {return slot >= 0 && slot < target.getSlots();}
    }
}
