package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.ramp.NativeRampPlayerHold;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ServerPlayer physics uses doTick rather than the ordinary cancellable EntityTick event. */
@Mixin(ServerPlayer.class)
public abstract class NativeRampPlayerPhysicsMixin {
 @Inject(method="doTick",at=@At("HEAD"),cancellable=true)
 private void vandorRampCommitPhysics(CallbackInfo callback){var player=(ServerPlayer)(Object)this;NativeRampPlayerHold.observedPhysicsHook(player);if(NativeRampPlayerHold.blocked(player))callback.cancel();}
}
