package com.vandorlabs.neoforge.mixin.optional;

import com.vandorlabs.neoforge.vehicle.NativeVehicleRftoolsCell;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mcjty.lib.api.infusable.DefaultInfusable", remap = false)
public abstract class NativeRftoolsCellInfusionEscrow {
    @Shadow @Final private BlockEntity owner;

    @Inject(method = "setInfused", at = @At("HEAD"), cancellable = true)
    private void vandorlabs$guardInfusion(int value, CallbackInfo result) {
        if (NativeVehicleRftoolsCell.blocksMutation(owner)) result.cancel();
    }
}
