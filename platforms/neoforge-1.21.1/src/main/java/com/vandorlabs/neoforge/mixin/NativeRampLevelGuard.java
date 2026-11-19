package com.vandorlabs.neoforge.mixin;
import com.vandorlabs.neoforge.ramp.NativeRampHeldOwners;
import com.vandorlabs.neoforge.turntable.NativeTurntableProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Reject destruction before loot, as well as block publication, on frozen mechanism cells or controllers retaining a captured assembly. */
@Mixin(Level.class)
public abstract class NativeRampLevelGuard {
 @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
 private void vandorlabs$rampSet(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> result){if(NativeRampHeldOwners.blocked((Level)(Object)this,pos)||com.vandorlabs.neoforge.gear.NativeGearHolds.replacementBlocked((Level)(Object)this,pos,state)||NativeTurntableProtection.retained((Level)(Object)this,pos))result.setReturnValue(false);}
 @Inject(method="destroyBlock",at=@At("HEAD"),cancellable=true)
 private void vandorlabs$rampDestroy(BlockPos pos,boolean drop,Entity actor,int recursion,CallbackInfoReturnable<Boolean> result){if(NativeRampHeldOwners.blocked((Level)(Object)this,pos)||com.vandorlabs.neoforge.gear.NativeGearHolds.retainedOwner((Level)(Object)this,pos)||NativeTurntableProtection.retained((Level)(Object)this,pos))result.setReturnValue(false);}
}
