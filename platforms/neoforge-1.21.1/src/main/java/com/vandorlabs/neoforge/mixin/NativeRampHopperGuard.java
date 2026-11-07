package com.vandorlabs.neoforge.mixin;
import com.vandorlabs.neoforge.ramp.NativeRampHeldOwners;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(HopperBlockEntity.class)
public abstract class NativeRampHopperGuard {
 @Inject(method="getContainerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/Container;",at=@At("HEAD"),cancellable=true)
 private static void vandorlabs$rampPosition(Level world,BlockPos pos,CallbackInfoReturnable<Container> result){if(NativeRampHeldOwners.blocked(world,pos))result.setReturnValue(null);}
 @Inject(method="getContainerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/Container;",at=@At("RETURN"),cancellable=true)
 private static void vandorlabs$rampCombined(Level world,BlockPos pos,CallbackInfoReturnable<Container> result){if(result.getReturnValue()!=null&&NativeRampHeldOwners.blocked(result.getReturnValue()))result.setReturnValue(null);}
}
