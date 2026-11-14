package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.gear.NativeGearHolds;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frozen native inventory/settings tiles cannot mutate their snapshots during an armed disk checkpoint. */
@Mixin(targets="net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class NativeGearTickGuard {
    @Shadow @Final private BlockEntity blockEntity;
    @Inject(method="tick",at=@At("HEAD"),cancellable=true) private void vandorlabs$gearHold(CallbackInfo callback){if(NativeGearHolds.blocked(blockEntity.getLevel(),blockEntity.getBlockPos()))callback.cancel();}
}
