package com.vandorlabs.neoforge.mixin;

import java.util.Optional;
import com.vandorlabs.neoforge.ramp.NativeRampPlayerAdoption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerDataStorage.class)
public abstract class NativeRampPlayerLoadMixin {
 @Inject(method="load(Lnet/minecraft/world/entity/player/Player;)Ljava/util/Optional;",at=@At("RETURN"),cancellable=true)
 private void vandorlabs$rampLogin(Player player,CallbackInfoReturnable<Optional<CompoundTag>> callback){if(player instanceof ServerPlayer server)callback.setReturnValue(NativeRampPlayerAdoption.loaded(server,callback.getReturnValue()));}
}
