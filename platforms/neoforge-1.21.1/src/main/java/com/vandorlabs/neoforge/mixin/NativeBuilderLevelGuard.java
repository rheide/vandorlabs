package com.vandorlabs.neoforge.mixin;
import com.vandorlabs.neoforge.builders.NativeBuilderProtection;import net.minecraft.core.BlockPos;import net.minecraft.world.entity.Entity;import net.minecraft.world.level.Level;import net.minecraft.world.level.block.state.BlockState;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Refuse terrain removal before vanilla loot. Player mining uses the builder's durable receipt. */
@Mixin(Level.class)
public abstract class NativeBuilderLevelGuard {
 @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
 private void vandorlabs$builderSet(BlockPos p,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> result){if(NativeBuilderProtection.replacementBlocked((Level)(Object)this,p,state))result.setReturnValue(false);}
 @Inject(method="destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",at=@At("HEAD"),cancellable=true)
 private void vandorlabs$builderDestroy(BlockPos p,boolean drop,Entity actor,int recursion,CallbackInfoReturnable<Boolean> result){if(NativeBuilderProtection.retained((Level)(Object)this,p))result.setReturnValue(false);}
}
