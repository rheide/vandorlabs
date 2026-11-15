package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.ramp.NativeRampSupportStamps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class NativeRampActorSaveMixin {
 @Inject(method="saveWithoutId",at=@At("RETURN"))
 private void vandorlabs$rampSupport(CompoundTag target,CallbackInfoReturnable<CompoundTag> callback){NativeRampSupportStamps.saving((Entity)(Object)this,callback.getReturnValue());}
}
