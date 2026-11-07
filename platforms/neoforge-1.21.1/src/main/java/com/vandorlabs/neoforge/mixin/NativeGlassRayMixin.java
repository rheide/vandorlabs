package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.glass.NativeGlassBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Glass keeps its enclosing outline while ray traversal uses the prepared released component boxes. */
@Mixin(BlockGetter.class)
public interface NativeGlassRayMixin {
 @Inject(method="clipWithInteractionOverride",at=@At("HEAD"),cancellable=true)
 private void vandorlabs$glass(Vec3 start,Vec3 end,BlockPos pos,VoxelShape shape,BlockState state,CallbackInfoReturnable<BlockHitResult> result){
  if(!(state.getBlock() instanceof NativeGlassBlock)||shape.isEmpty())return;
  var geometry=NativeGlassBlock.geometry(state,(BlockGetter)(Object)this,pos);
  var hit=geometry.scalar().trace(start.x-pos.getX(),start.y-pos.getY(),start.z-pos.getZ(),end.x-pos.getX(),end.y-pos.getY(),end.z-pos.getZ());
  result.setReturnValue(hit==null?null:new BlockHitResult(new Vec3(hit.x+pos.getX(),hit.y+pos.getY(),hit.z+pos.getZ()),Direction.from3DDataValue(hit.face),pos,false));
 }
}
