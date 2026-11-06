package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.client.NativeDoorSelection;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publish exact moved-leaf picking before vanilla captures its continuous-mining target. */
@Mixin(GameRenderer.class)
public abstract class DoorPickMixin {
    @Inject(method="pick(F)V",at=@At("TAIL"),require=1)
    private void vandorlabs$movedLeafPick(float partialTick,CallbackInfo callback){NativeDoorSelection.refreshPick(partialTick);}
}
