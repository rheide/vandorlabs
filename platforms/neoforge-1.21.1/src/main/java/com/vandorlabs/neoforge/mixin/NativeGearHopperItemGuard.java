package com.vandorlabs.neoforge.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class NativeGearHopperItemGuard {
    @Inject(method="addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",at=@At("HEAD"),cancellable=true)
    private static void vandorlabs$heldDrop(Container target,ItemEntity item,CallbackInfoReturnable<Boolean> callback){if(com.vandorlabs.neoforge.gear.NativeGearDropEscrow.held(item))callback.setReturnValue(false);}
}
