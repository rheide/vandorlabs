package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.NativeAuthoredBlockRay;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Authored blocks keep their physical voxel fast paths while ordinary DDA uses exact released rays. */
@Mixin(BlockGetter.class)
public interface NativeGlassRayMixin {
 @Inject(method="clipWithInteractionOverride",at=@At("HEAD"),cancellable=true)
 private void vandorlabs$authored(Vec3 start,Vec3 end,BlockPos pos,VoxelShape shape,BlockState state,CallbackInfoReturnable<BlockHitResult> result){
  if(!(state.getBlock() instanceof NativeAuthoredBlockRay authored)||shape.isEmpty())return;
  result.setReturnValue(authored.traceAuthored(state,(BlockGetter)(Object)this,pos,start,end));
 }
}
