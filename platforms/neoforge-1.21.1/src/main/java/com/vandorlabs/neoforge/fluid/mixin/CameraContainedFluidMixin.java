package com.vandorlabs.neoforge.fluid.mixin;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import net.minecraft.client.Camera;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Select vanilla water/lava fog from the actual partial-volume camera point. */
@Mixin(Camera.class)
public abstract class CameraContainedFluidMixin {
    @Inject(method="getFluidInCamera()Lnet/minecraft/world/level/material/FogType;",at=@At("HEAD"),cancellable=true,require=1)
    private void vandorlabs$containedCamera(CallbackInfoReturnable<FogType> result){Camera camera=(Camera)(Object)this;if(!camera.isInitialized()||camera.getEntity()==null)return;var fluid=NativeFluidWorld.atPoint(camera.getEntity().level(),camera.getPosition());if(fluid.is(FluidTags.WATER))result.setReturnValue(FogType.WATER);else if(fluid.is(FluidTags.LAVA))result.setReturnValue(FogType.LAVA);}
}
