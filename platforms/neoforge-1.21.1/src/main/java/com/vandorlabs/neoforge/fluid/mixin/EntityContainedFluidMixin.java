package com.vandorlabs.neoforge.fluid.mixin;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supply actual partial-fluid contacts at the same point as the native fluid scan. */
@Mixin(Entity.class)
public abstract class EntityContainedFluidMixin implements net.neoforged.neoforge.common.extensions.IEntityExtension {
    @Override public boolean canStartSwimming(){Entity entity=(Entity)(Object)this;var feet=NativeFluidWorld.atPoint(entity.level(),new Vec3(entity.getX(),entity.getBoundingBox().minY+.001,entity.getZ()));if(feet.isEmpty())return net.neoforged.neoforge.common.extensions.IEntityExtension.super.canStartSwimming();return !entity.getEyeInFluidType().isAir()&&entity.canSwimInFluidType(entity.getEyeInFluidType())&&entity.canSwimInFluidType(feet.getFluidType());}
    @Shadow protected Object2DoubleMap<FluidType> forgeFluidTypeHeight;
    @Shadow private FluidType forgeFluidTypeOnEyes;
    @Inject(method="updateFluidHeightAndDoFluidPushing()V",at=@At("TAIL"),require=1)
    private void vandorlabs$containedBody(CallbackInfo callback){
        Entity entity=(Entity)(Object)this;
        if(entity.touchingUnloadedChunk())return;
        NativeFluidWorld.immersion(entity.level(),entity.getBoundingBox().deflate(.001),(type,height)->forgeFluidTypeHeight.put(type,Math.max(forgeFluidTypeHeight.getDouble(type),height)));
    }
    @Inject(method="updateFluidOnEyes()V",at=@At("TAIL"),require=1)
    private void vandorlabs$containedEyes(CallbackInfo callback){
        Entity entity=(Entity)(Object)this;if(entity.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat boat&&!boat.isUnderWater()&&boat.getBoundingBox().minY<=entity.getEyeY()&&boat.getBoundingBox().maxY>=entity.getEyeY())return;var fluid=NativeFluidWorld.atPoint(entity.level(),new Vec3(entity.getX(),entity.getEyeY(),entity.getZ()));
        if(!fluid.isEmpty())forgeFluidTypeOnEyes=fluid.getFluidType();
    }
}
