package com.vandorlabs.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Use vanilla's current player serialization for participating Ramp actors only.
 * Recovery never installs a player/inventory snapshot. */
@Mixin(PlayerList.class)
public interface NativeRampPlayerSaveAccess {
 @Invoker("save") void vandorlabs$rampSaveParticipant(ServerPlayer player);
}
