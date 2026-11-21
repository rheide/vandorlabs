package com.vandorlabs.neoforge.gear;

import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.gear.core.GearMotion;
import com.vandorlabs.neoforge.transfer.NativeTransferHolds;
import com.vandorlabs.neoforge.ramp.NativeRampHeldOwners;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

final class NativeGearResourceTransfer {
    private final NativeGearEntity input;
    private Direction exposedSide;
    private IEnergyStorage energy;
    private IFluidHandler fluid;
    private IItemHandler items;
    private GearMotion.Pose mappedPose;
    private Direction mappedMount;
    private NativeGearPayload.Transform frame;

    NativeGearResourceTransfer(NativeGearEntity input) {
        this.input = input;
    }

    static IEnergyStorage energy(NativeGearEntity tile, Direction side) {
        if (installedArm(tile,side,ArmResource.POWER) == null) return null;
        var connection = tile.resourceConnection().expose(side);
        if (connection.energy == null) connection.energy = connection.new Power(side);
        return connection.energy;
    }

    static IFluidHandler fluid(NativeGearEntity tile, Direction side) {
        if (installedArm(tile,side,ArmResource.FLUID) == null) return null;
        var connection = tile.resourceConnection().expose(side);
        if (connection.fluid == null) connection.fluid = connection.new Fluids(side);
        return connection.fluid;
    }

    static IItemHandler items(NativeGearEntity tile, Direction side) {
        if (installedArm(tile,side,ArmResource.ITEM) == null) return null;
        var connection = tile.resourceConnection().expose(side);
        if (connection.items == null) connection.items = connection.new Items(side);
        return connection.items;
    }

    private boolean output(){var owner=input.controllerEntity();return owner!=null&&owner.settings().resourceOut();}
    private NativeGearResourceTransfer expose(Direction side) {
        if (exposedSide != side) {
            exposedSide = side;
            energy = null;
            fluid = null;
            items = null;
        }
        return this;
    }

    private static NativeGearEntity installedArm(NativeGearEntity input, Direction side, ArmResource type) {
        if (side == null || side != input.mount().getOpposite() || !input.authority()) return null;
        var arm = input.controllerEntity();
        if (arm == null || !arm.canTransferResources() || arm.mount() != input.mount()
                || arm.settings().resourceTransfer() != type
                || !arm.columns().contains(input.getBlockPos().subtract(arm.getBlockPos()))) return null;
        return arm;
    }

    private NativeGearPayload.Transform frame(NativeGearEntity arm) {
        var pose = arm.pose(arm.progress());
        if (mappedPose != pose || mappedMount != arm.mount()) {
            frame = NativeGearPayload.frame(arm,arm.progress());
            mappedPose = pose;
            mappedMount = arm.mount();
        }
        return frame;
    }

    private static Direction face(NativeGearEntity arm, NativeGearPayload.Transform frame, Direction side) {
        var local = NativeGearOrientation.inverse(arm.mount(),new BlockPos(side.getStepX(),side.getStepY(),side.getStepZ()));
        var vector = frame.vector(local.getX(),local.getY(),local.getZ());
        var oriented = Direction.getNearest(vector.x,vector.y,vector.z);
        return vector.distanceToSqr(Vec3.atLowerCornerOf(oriented.getNormal())) < 1e-12 ? oriented : null;
    }

    private static boolean reserved(ServerLevel level, BlockPos pos) {
        return NativeRampHeldOwners.foreignToCellReservation(level,pos) || NativeTransferHolds.reserved(level,pos);
    }

    private <T, R> R send(ArmResource type, Direction side, BlockCapability<T,Direction> capability, R unavailable, Function<T,R> action) {
        return ArmResource.forward(input,unavailable,() -> {
            var arm = installedArm(input,side,type);
            if (arm == null || !arm.resourceEndpointAvailable()) return unavailable;
            var level = (ServerLevel) input.getLevel();
            var mounting = input.getBlockPos();
            var root = arm.getBlockPos();
            if (NativeGearJournal.escrowAdmission(level) != NativeGearJournal.Status.READY
                    || !arm.resourceEndpointAvailable() || reserved(level,root) || reserved(level,mounting)) return unavailable;
            var contact = mounting.relative(arm.mount(),arm.assembly().columnHeight());
            Direction receiving = side;
            if (arm.mode() != GearMotion.Mode.STATIC) {
                var frame = arm.resourceConnection().frame(arm);
                // An integral position is insufficient if the attachment's axes are tilted.
                if (face(arm,frame,Direction.UP) == null || face(arm,frame,Direction.EAST) == null
                        || face(arm,frame,Direction.NORTH) == null) return unavailable;
                receiving = face(arm,frame,side);
                try {
                    contact = NativeGearPayloadService.nativeCell(arm,contact,frame);
                } catch (IllegalArgumentException fractional) {
                    return unavailable;
                }
            }
            if (!NativeGearFormation.loaded(level,contact) || reserved(level,contact)) return unavailable;
            var destination = level.getCapability(capability,contact,receiving);
            return destination == null ? unavailable : action.apply(destination);
        });
    }

    private final class Power implements IEnergyStorage {
        private final Direction side;
        Power(Direction side) {this.side=side;}
        public int receiveEnergy(int amount, boolean simulate) {
            if (output()||amount <= 0) return 0;
            int accepted=send(ArmResource.POWER,side,Capabilities.EnergyStorage.BLOCK,0,target ->
                    Math.min(amount,Math.max(0,target.receiveEnergy(amount,simulate))));
            if(!simulate&&accepted>0)input.resourceTransferred(ArmResource.POWER);
            return accepted;
        }
        /*
         * Configuration only, never whether the far end is reachable right now: conduits may read
         * canReceive once when they connect and treat a false answer as a source until they
         * reconnect. receiveEnergy and extractEnergy return 0 while the arm cannot transfer.
         */
        public boolean canReceive() {return !output();}
        public int extractEnergy(int amount, boolean simulate) {
            if(!output()||amount<=0)return 0;
            int extracted=send(ArmResource.POWER,side,Capabilities.EnergyStorage.BLOCK,0,target->Math.min(amount,Math.max(0,target.extractEnergy(amount,simulate))));
            if(!simulate&&extracted>0)input.resourceTransferred(ArmResource.POWER);return extracted;
        }
        public int getEnergyStored() {return send(ArmResource.POWER,side,Capabilities.EnergyStorage.BLOCK,0,IEnergyStorage::getEnergyStored);}
        public int getMaxEnergyStored() {return send(ArmResource.POWER,side,Capabilities.EnergyStorage.BLOCK,0,IEnergyStorage::getMaxEnergyStored);}
        public boolean canExtract() {return output();}
    }

    private final class Fluids implements IFluidHandler {
        private final Direction side;
        Fluids(Direction side) {this.side=side;}
        public int getTanks() {return send(ArmResource.FLUID,side,Capabilities.FluidHandler.BLOCK,0,IFluidHandler::getTanks);}
        public FluidStack getFluidInTank(int tank) {
            return send(ArmResource.FLUID,side,Capabilities.FluidHandler.BLOCK,FluidStack.EMPTY,
                    target -> validTank(target,tank) ? target.getFluidInTank(tank).copy() : FluidStack.EMPTY);
        }
        public int getTankCapacity(int tank) {
            return send(ArmResource.FLUID,side,Capabilities.FluidHandler.BLOCK,0,
                    target -> validTank(target,tank) ? target.getTankCapacity(tank) : 0);
        }
        public boolean isFluidValid(int tank, FluidStack fluid) {
            return send(ArmResource.FLUID,side,Capabilities.FluidHandler.BLOCK,false,
                    target -> validTank(target,tank) && target.isFluidValid(tank,fluid));
        }
        public int fill(FluidStack fluid, FluidAction action) {
            if (output()||fluid.isEmpty()) return 0;
            int accepted=send(ArmResource.FLUID,side,Capabilities.FluidHandler.BLOCK,0,target ->
                    Math.min(fluid.getAmount(),Math.max(0,target.fill(fluid.copy(),action))));
            if(action.execute()&&accepted>0)input.resourceTransferred(ArmResource.FLUID);
            return accepted;
        }
        public FluidStack drain(FluidStack fluid, FluidAction action) {
            if(!output()||fluid.isEmpty())return FluidStack.EMPTY;
            FluidStack result=send(ArmResource.FLUID,side,Capabilities.FluidHandler.BLOCK,FluidStack.EMPTY,target->target.drain(fluid.copy(),action));
            if(action.execute()&&!result.isEmpty())input.resourceTransferred(ArmResource.FLUID);return result.copy();
        }
        public FluidStack drain(int amount, FluidAction action) {
            if(!output()||amount<=0)return FluidStack.EMPTY;
            FluidStack result=send(ArmResource.FLUID,side,Capabilities.FluidHandler.BLOCK,FluidStack.EMPTY,target->target.drain(amount,action));
            if(action.execute()&&!result.isEmpty())input.resourceTransferred(ArmResource.FLUID);return result.copy();
        }
        private boolean validTank(IFluidHandler target, int tank) {return tank >= 0 && tank < target.getTanks();}
    }

    private final class Items implements IItemHandler {
        private final Direction side;
        Items(Direction side) {this.side=side;}
        public int getSlots() {return send(ArmResource.ITEM,side,Capabilities.ItemHandler.BLOCK,0,IItemHandler::getSlots);}
        public ItemStack getStackInSlot(int slot) {
            return send(ArmResource.ITEM,side,Capabilities.ItemHandler.BLOCK,ItemStack.EMPTY,
                    target -> validSlot(target,slot) ? target.getStackInSlot(slot).copy() : ItemStack.EMPTY);
        }
        public int getSlotLimit(int slot) {
            return send(ArmResource.ITEM,side,Capabilities.ItemHandler.BLOCK,0,
                    target -> validSlot(target,slot) ? target.getSlotLimit(slot) : 0);
        }
        public boolean isItemValid(int slot, ItemStack stack) {
            return send(ArmResource.ITEM,side,Capabilities.ItemHandler.BLOCK,false,
                    target -> validSlot(target,slot) && target.isItemValid(slot,stack));
        }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (output()||stack.isEmpty()) return stack;
            int offered=stack.getCount();
            ItemStack remainder=send(ArmResource.ITEM,side,Capabilities.ItemHandler.BLOCK,stack,
                    // Forward a copy: an inventory may keep the stack it is given in an empty slot,
                    // and a caller may change the stack it passed afterwards (a Thermal Retriever
                    // sets its stuffed stack to the remainder, zero), emptying that slot.
                    target -> validSlot(target,slot) ? target.insertItem(slot,stack.copy(),simulate) : stack);
            if(!simulate&&remainder.getCount()<offered)input.resourceTransferred(ArmResource.ITEM);
            return remainder;
        }
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if(!output()||amount<=0)return ItemStack.EMPTY;
            ItemStack result=send(ArmResource.ITEM,side,Capabilities.ItemHandler.BLOCK,ItemStack.EMPTY,target->validSlot(target,slot)?target.extractItem(slot,amount,simulate):ItemStack.EMPTY);
            if(!simulate&&!result.isEmpty())input.resourceTransferred(ArmResource.ITEM);return result.copy();
        }
        private boolean validSlot(IItemHandler target,int slot) {return slot >= 0 && slot < target.getSlots();}
    }
}
