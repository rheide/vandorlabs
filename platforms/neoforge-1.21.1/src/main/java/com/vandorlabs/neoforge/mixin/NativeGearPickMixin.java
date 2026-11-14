package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.gear.client.NativeGearMechanismSelection;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class NativeGearPickMixin {
    @Inject(method="pick(F)V",at=@At("TAIL"),require=1)
    private void vandorlabs$gearPick(float partial,CallbackInfo callback){NativeGearMechanismSelection.refreshPick(partial);com.vandorlabs.neoforge.gear.client.NativeGearPayloadSelection.refreshPick(partial);}
}
