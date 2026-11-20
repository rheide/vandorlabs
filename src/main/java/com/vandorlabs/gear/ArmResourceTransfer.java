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
    /** A tip connection: transfers reach the block against the base's mounting face. */
    private final boolean reversed;
    private EnumFacing exposedSide;
    private IEnergyStorage energy;
    private IFluidHandler fluid;
    private IItemHandler items;
    private GearMotion.Pose mappedPose;
    private ArmPayload mappedPayload;
    private ArmPayload.Endpoint emptyEndpoint;
    private ArmGridMapping payloadEndpoint;

    ArmResourceTransfer(GearTileEntity input) {
        this(input,false);
    }
    private ArmResourceTransfer(GearTileEntity input,boolean reversed) {
        this.input = input;this.reversed=reversed;
    }
    /** Flow leaves through the exposed face: the base when Out is selected, the tip otherwise. */
    private boolean out(){return reversed!=input.resourceOut();}

    /**
     * The outward face of an empty arm tip exposes the selected resource, so ducts and pipes on
     * the tip connect to the arm instead of being captured as cargo.
     */
    static <T> T tipInput(GearTileEntity tile,EnumFacing side,Capability<T> capability){
        ArmResource type=forCapability(capability);
        if(type==ArmResource.DISABLED||side==null||!(tile.getWorld() instanceof WorldServer)||tile.isInvalid())return null;
        WorldServer world=(WorldServer)tile.getWorld();
        if(world.getMinecraftServer()==null||!world.getMinecraftServer().isCallingFromMinecraftThread())return null;
        GearTileEntity arm=tile.loadedController();
        if(arm==null||arm.isInvalid()||arm.resourceTransfer()!=type||arm.payload()!=null||!arm.resourceEndpointAvailable())return null;
        BlockPos contact=tile.getPos().offset(side);
        for(BlockPos column:arm.columns()){
            TileEntity base=world.getTileEntity(arm.getPos().add(column));
            if(!(base instanceof GearTileEntity)||installedArm((GearTileEntity)base,type)!=arm)continue;
            ArmResourceTransfer forward=((GearTileEntity)base).resourceConnection();
            if(!contact.equals(forward.endpointContact(arm)))continue;
            ArmResourceTransfer tip=tile.tipConnection((GearTileEntity)base);
            if(tip.exposedSide!=side){tip.exposedSide=side;tip.energy=null;tip.fluid=null;tip.items=null;}
            return tip.handler(type,side,capability);
        }
        return null;
    }
    static ArmResourceTransfer tip(GearTileEntity base){return new ArmResourceTransfer(base,true);}
    boolean tipFor(GearTileEntity base){return reversed&&input==base;}
    /** The world cell the base forwards to while the arm is empty and at rest, or null. */
    private BlockPos endpointContact(GearTileEntity arm){
        BlockPos contact=input.getPos().offset(arm.mountDirection(),arm.assembly().columnHeight());
        if(arm.isFixedMount())return contact;
        map(arm);
        return emptyEndpoint==null?null:emptyEndpoint.placed(contact);
    }
    private <T> T handler(ArmResource type,EnumFacing side,Capability<T> capability){
        switch(type){
            case POWER:if(energy==null)energy=new Power(side);return CapabilityEnergy.ENERGY.cast(energy);
            case FLUID:if(fluid==null)fluid=new Fluid(side);return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluid);
            case ITEM:if(items==null)items=new Items(side);return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(items);
            default:return null;
        }
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
        if (transfer.exposedSide != side) {
            transfer.exposedSide = side;
            transfer.energy = null;
            transfer.fluid = null;
            transfer.items = null;
        }
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
        if (world.getMinecraftServer() == null || !world.getMinecraftServer().isCallingFromMinecraftThread() || !GearAssembly.loaded(world,mounting)
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

    @SuppressWarnings("unchecked")
    private <T, R> R send(ArmResource type, EnumFacing side, Capability<T> capability, R unavailable, Function<T,R> action) {
        if(reversed)return ArmResource.forward(input,unavailable,() -> {
            GearTileEntity arm = installedArm(input,type);
            if (arm == null || arm.payload() != null || !arm.resourceEndpointAvailable()) return unavailable;
            WorldServer world = (WorldServer) input.getWorld();
            BlockPos mounting = input.getPos();
            if (ArmTransactions.lockedForResourceTransfer(world,arm.getPos())
                    || !arm.getPos().equals(mounting) && ArmTransactions.lockedForResourceTransfer(world,mounting)) return unavailable;
            BlockPos source = mounting.offset(input.mountDirection().getOpposite());
            if (!GearAssembly.loaded(world,source) || ArmTransactions.lockedForResourceTransfer(world,source)
                    || com.vandorlabs.copier.CopyTransactions.locked(world,source)) return unavailable;
            TileEntity target = world.getTileEntity(source);
            if (target == null || target.isInvalid() || target.getWorld() != world || !source.equals(target.getPos())) return unavailable;
            EnumFacing face = input.mountDirection();
            T destination = target.getCapability(capability,face);
            if(destination==null&&capability==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)destination=(T)com.vandorlabs.ports.ResourcePortConnections.itemHandler(target,face,mounting);
            return destination == null ? unavailable : action.apply(destination);
        });
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
            ArmPayload carried=arm.payload();
            if(carried!=null&&!carried.materialized())return carried.transferCapturedTank(arm,
                    contact.subtract(root).subtract(carried.offset),side,capability,unavailable,action);
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
            if(destination==null&&capability==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)destination=(T)com.vandorlabs.ports.ResourcePortConnections.itemHandler(target,face,contact.offset(face));
            return destination == null ? unavailable : action.apply(destination);
        });
    }

    private final class Power implements IEnergyStorage {
        private final EnumFacing side;
        Power(EnumFacing side) { this.side = side; }
        @Override public int receiveEnergy(int amount, boolean simulate) {
            if (out()||amount <= 0) return 0;
            int accepted=send(ArmResource.POWER,side,CapabilityEnergy.ENERGY,0,target ->
                    Math.min(amount,Math.max(0,target.receiveEnergy(amount,simulate))));
            if(!simulate&&accepted>0)input.resourceTransferred(ArmResource.POWER);
            return accepted;
        }
        @Override public boolean canReceive() {return !out()&&send(ArmResource.POWER,side,CapabilityEnergy.ENERGY,false,IEnergyStorage::canReceive);}
        @Override public int extractEnergy(int amount, boolean simulate) {
            if(!out()||amount<=0)return 0;
            int extracted=send(ArmResource.POWER,side,CapabilityEnergy.ENERGY,0,target->Math.min(amount,Math.max(0,target.extractEnergy(amount,simulate))));
            if(!simulate&&extracted>0)input.resourceTransferred(ArmResource.POWER);return extracted;
        }
        @Override public int getEnergyStored() {return send(ArmResource.POWER,side,CapabilityEnergy.ENERGY,0,IEnergyStorage::getEnergyStored);}
        @Override public int getMaxEnergyStored() {return send(ArmResource.POWER,side,CapabilityEnergy.ENERGY,0,IEnergyStorage::getMaxEnergyStored);}
        @Override public boolean canExtract() {return out()&&send(ArmResource.POWER,side,CapabilityEnergy.ENERGY,false,IEnergyStorage::canExtract);}
    }

    private final class Fluid implements IFluidHandler {
        private final EnumFacing side;
        private final IFluidTankProperties[] unavailable = new IFluidTankProperties[0];
        Fluid(EnumFacing side) { this.side = side; }
        @Override public int fill(FluidStack resource, boolean commit) {
            if (out()||resource == null || resource.amount <= 0) return 0;
            int amount = resource.amount;
            int accepted=send(ArmResource.FLUID,side,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,0,target ->
                    Math.min(amount,Math.max(0,target.fill(resource.copy(),commit))));
            if(commit&&accepted>0)input.resourceTransferred(ArmResource.FLUID);
            return accepted;
        }
        @Override public IFluidTankProperties[] getTankProperties() {
            IFluidTankProperties[] result=properties();
            // Pipes only connect to a neighbour that reports a tank; keep a tip connection stable.
            return reversed&&result.length==0?new IFluidTankProperties[]{new net.minecraftforge.fluids.capability.FluidTankProperties(null,0,out(),!out())}:result;
        }
        private IFluidTankProperties[] properties() {
            return send(ArmResource.FLUID,side,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,unavailable,target -> {
                IFluidTankProperties[] properties = target.getTankProperties();
                if (properties == null || properties.length == 0) return unavailable;
                IFluidTankProperties[] result = new IFluidTankProperties[properties.length];
                for (int index=0; index<result.length; index++) result[index] = new InputTank(index);
                return result;
            });
        }
        @Override public FluidStack drain(FluidStack resource, boolean commit) {
            if(!out()||resource==null||resource.amount<=0)return null;
            FluidStack result=send(ArmResource.FLUID,side,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,null,target->target.drain(resource.copy(),commit));
            if(commit&&result!=null&&result.amount>0)input.resourceTransferred(ArmResource.FLUID);return result==null?null:result.copy();
        }
        @Override public FluidStack drain(int amount, boolean commit) {
            if(!out()||amount<=0)return null;
            FluidStack result=send(ArmResource.FLUID,side,CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,null,target->target.drain(amount,commit));
            if(commit&&result!=null&&result.amount>0)input.resourceTransferred(ArmResource.FLUID);return result==null?null:result.copy();
        }

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
            @Override public boolean canFill() {return !out()&&property(false,IFluidTankProperties::canFill);}
            @Override public boolean canDrain() {return out()&&property(false,IFluidTankProperties::canDrain);}
            @Override public boolean canFillFluidType(FluidStack resource) {
                return !out()&&resource!=null && property(false,tank -> tank.canFillFluidType(resource));
            }
            @Override public boolean canDrainFluidType(FluidStack resource) {return out()&&resource!=null&&property(false,tank->tank.canDrainFluidType(resource));}
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
            if (out()||stack.isEmpty()) return stack;
            int offered=stack.getCount();
            ItemStack remainder=send(ArmResource.ITEM,side,CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,stack,
                    target -> validSlot(target,slot) ? target.insertItem(slot,stack,simulate) : stack);
            if(!simulate&&remainder.getCount()<offered)input.resourceTransferred(ArmResource.ITEM);
            return remainder;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if(!out()||amount<=0)return ItemStack.EMPTY;
            ItemStack result=send(ArmResource.ITEM,side,CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,ItemStack.EMPTY,target->validSlot(target,slot)?target.extractItem(slot,amount,simulate):ItemStack.EMPTY);
            if(!simulate&&!result.isEmpty())input.resourceTransferred(ArmResource.ITEM);return result.copy();
        }
        @Override public int getSlotLimit(int slot) {
            return send(ArmResource.ITEM,side,CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,0,
                    target -> validSlot(target,slot) ? target.getSlotLimit(slot) : 0);
        }
        private boolean validSlot(IItemHandler target,int slot) {return slot >= 0 && slot < target.getSlots();}
    }
}
