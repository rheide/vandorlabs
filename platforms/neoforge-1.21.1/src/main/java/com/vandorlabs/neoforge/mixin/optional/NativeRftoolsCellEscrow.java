package com.vandorlabs.neoforge.mixin.optional;

import com.vandorlabs.neoforge.vehicle.NativeVehicleRftoolsCell;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mcjty.rftoolspower.modules.dimensionalcell.blocks.DimensionalCellTileEntity", remap = false)
public abstract class NativeRftoolsCellEscrow {
    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void vandorlabs$pauseCell(CallbackInfo result) {
        if (NativeVehicleRftoolsCell.blocksMutation((BlockEntity)(Object)this)) result.cancel();
    }

    @Inject(method = "receiveEnergyFacing", at = @At("HEAD"), cancellable = true)
    private void vandorlabs$guardCellInput(Direction side, int amount, boolean simulate, CallbackInfoReturnable<Integer> result) {
        if (NativeVehicleRftoolsCell.blocksMutation((BlockEntity)(Object)this)) result.setReturnValue(0);
    }
}
