package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.ramp.NativeRampRiders;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An armed Ramp's own native sources are not foreign obstacles to its scoped rider preflight. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class NativeRampSourceCollisionMixin {
 @Inject(method="getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",at=@At("HEAD"),cancellable=true,require=1)
 private void vandorlabs$heldOriginal(BlockGetter world,BlockPos pos,CollisionContext context,CallbackInfoReturnable<VoxelShape> result){if(NativeRampRiders.suppressOriginal(world,pos,(BlockState)(Object)this))result.setReturnValue(Shapes.empty());}
}
