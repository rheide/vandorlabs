package com.vandorlabs.neoforge.mixin;
import com.vandorlabs.neoforge.ramp.NativeRampHeldOwners;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Reject destruction before loot, as well as block publication, on exact frozen Ramp cells. */
@Mixin(Level.class)
public abstract class NativeRampLevelGuard {
 @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
 private void vandorlabs$rampSet(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> result){if(NativeRampHeldOwners.blocked((Level)(Object)this,pos))result.setReturnValue(false);}
 @Inject(method="destroyBlock",at=@At("HEAD"),cancellable=true)
 private void vandorlabs$rampDestroy(BlockPos pos,boolean drop,Entity actor,int recursion,CallbackInfoReturnable<Boolean> result){if(NativeRampHeldOwners.blocked((Level)(Object)this,pos))result.setReturnValue(false);}
}
