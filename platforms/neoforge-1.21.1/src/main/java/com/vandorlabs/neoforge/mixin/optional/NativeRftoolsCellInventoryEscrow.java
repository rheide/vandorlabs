package com.vandorlabs.neoforge.mixin.optional;

import com.vandorlabs.neoforge.vehicle.NativeVehicleRftoolsCell;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mcjty.lib.container.GenericItemHandler", remap = false)
public abstract class NativeRftoolsCellInventoryEscrow {
    @Unique private BlockEntity vandorlabs$owner;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vandorlabs$rememberOwner(@Coerce Object tile, @Coerce Object factory, CallbackInfo result) {
        vandorlabs$owner = (BlockEntity)tile;
    }

    @Unique private boolean vandorlabs$held() {
        return vandorlabs$owner != null && NativeVehicleRftoolsCell.blocksMutation(vandorlabs$owner);
    }

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void vandorlabs$guardInsert(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> result) {
        if (vandorlabs$held()) result.setReturnValue(stack);
    }

    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true)
    private void vandorlabs$guardExtract(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> result) {
        if (vandorlabs$held()) result.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = {"setStackInSlot", "setInventorySlotContents"}, at = @At("HEAD"), cancellable = true)
    private void vandorlabs$guardSet(CallbackInfo result) {
        if (vandorlabs$held()) result.cancel();
    }
}
