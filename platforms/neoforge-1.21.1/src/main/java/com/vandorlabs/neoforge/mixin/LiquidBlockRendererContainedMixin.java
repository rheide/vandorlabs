package com.vandorlabs.neoforge.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vandorlabs.neoforge.fluid.client.NativeFluidContacts;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the native liquid renderer and adapt only contacts with published contained-fluid cells. */
@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererContainedMixin {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="tesselate")
    private void vandorlabs$render(BlockAndTintGetter access,BlockPos pos,VertexConsumer consumer,BlockState block,FluidState fluid,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original){VertexConsumer wrapped=NativeFluidContacts.begin(access,pos,consumer,fluid);boolean complete=false;try{original.call(access,pos,wrapped,block,fluid);complete=true;}finally{NativeFluidContacts.end(wrapped,complete);}}
    @Inject(method="getHeight(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)F",at=@At("HEAD"),cancellable=true,require=1)
    private void vandorlabs$height(BlockAndTintGetter access,Fluid fluid,BlockPos pos,BlockState state,FluidState original,CallbackInfoReturnable<Float> result){Float value=NativeFluidContacts.height(access,fluid,pos,original);if(value!=null)result.setReturnValue(value);}
    @Inject(method="calculateAverageHeight",at=@At("HEAD"),cancellable=true,require=1)
    private void vandorlabs$corner(BlockAndTintGetter access,Fluid fluid,float own,float first,float second,BlockPos diagonal,CallbackInfoReturnable<Float> result){Float value=NativeFluidContacts.corner(access,fluid,diagonal);if(value!=null)result.setReturnValue(value);}
}
