package com.vandorlabs.neoforge.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent vanilla from repeatedly digging the protected root used as a captured-cell hit address. */
@Mixin(Minecraft.class)
public abstract class NativeGearAttackMixin {
    @Inject(method="continueAttack(Z)V",at=@At("HEAD"),cancellable=true,require=1)
    private void vandorlabs$capturedDig(boolean held,CallbackInfo callback){if(com.vandorlabs.neoforge.gear.client.NativeGearPayloadSelection.attacking(held))callback.cancel();}
}
