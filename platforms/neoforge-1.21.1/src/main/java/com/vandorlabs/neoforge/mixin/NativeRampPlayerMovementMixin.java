package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.ramp.NativeRampPlayerHold;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only the bounded physical publication fence intercepts movement. Teleport ACK, keepalive,
 * connection ticks and disconnect handling keep their vanilla paths. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class NativeRampPlayerMovementMixin {
 @Shadow public ServerPlayer player;
 @Inject(method="handleMovePlayer",at=@At("HEAD"),cancellable=true)
 private void vandorRampCommitMovement(ServerboundMovePlayerPacket packet,CallbackInfo callback){
  NativeRampPlayerHold.observedMovementHook(player);if(NativeRampPlayerHold.blocked(player))callback.cancel();
 }
}
