package com.vandorlabs.neoforge.mixin;
import com.vandorlabs.neoforge.programmer.NativeProgrammerBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Released SOLID decorative connections do not grant ordinary Programmer side support. */
@Mixin({FenceBlock.class,WallBlock.class,IronBarsBlock.class})
abstract class ProgrammerConnectionMixin {
 // Each selector exists only on its matching targets; the actual 384-case fixture proves both paths.
 @Inject(method="connectsTo(Lnet/minecraft/world/level/block/state/BlockState;ZLnet/minecraft/core/Direction;)Z",at=@At("HEAD"),cancellable=true,require=0)
 private void vandorProgrammerConnection(BlockState neighbor,boolean sturdy,Direction face,CallbackInfoReturnable<Boolean> result){if(neighbor.getBlock() instanceof NativeProgrammerBlock)result.setReturnValue(true);}
 @Inject(method="attachsTo(Lnet/minecraft/world/level/block/state/BlockState;Z)Z",at=@At("HEAD"),cancellable=true,require=0)
 private void vandorProgrammerPane(BlockState neighbor,boolean sturdy,CallbackInfoReturnable<Boolean> result){if(neighbor.getBlock() instanceof NativeProgrammerBlock)result.setReturnValue(true);}
}
