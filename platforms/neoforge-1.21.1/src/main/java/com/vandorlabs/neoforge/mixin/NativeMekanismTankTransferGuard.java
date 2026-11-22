package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.vehicle.NativeVehicleMekanismEscrow;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mekanism can retain its actual tank behind both standard and native fluid handlers. */
@Pseudo
@Mixin(targets = "mekanism.common.capabilities.fluid.FluidTankFluidTank", remap = false)
public abstract class NativeMekanismTankTransferGuard {
    @Unique private BlockEntity vandorlabs$tankOwner;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vandorlabs$rememberOwner(@Coerce Object owner, @Coerce Object listener, CallbackInfo callback) {
        vandorlabs$tankOwner = (BlockEntity)owner;
    }

    @Unique private boolean vandorlabs$held() {
        var owner = vandorlabs$tankOwner;
        return owner != null && NativeVehicleMekanismEscrow.blocksSided(owner);
    }

    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private void vandorlabs$guardFill(FluidStack stack, @Coerce Object action, @Coerce Object automation,
                                     CallbackInfoReturnable<FluidStack> result) {
        if (vandorlabs$held()) result.setReturnValue(stack);
    }

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void vandorlabs$guardDrain(int amount, @Coerce Object action, @Coerce Object automation,
                                      CallbackInfoReturnable<FluidStack> result) {
        if (vandorlabs$held()) result.setReturnValue(FluidStack.EMPTY);
    }
}
