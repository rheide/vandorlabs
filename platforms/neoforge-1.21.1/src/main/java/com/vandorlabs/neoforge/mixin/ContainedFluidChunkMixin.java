package com.vandorlabs.neoforge.mixin;
import com.vandorlabs.neoforge.fluid.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LevelChunk.class)
public abstract class ContainedFluidChunkMixin {
 @Inject(method="setBlockState",at=@At("RETURN"))
 private void vandorlabs$fluidChanged(BlockPos pos,BlockState after,boolean moving,CallbackInfoReturnable<BlockState> result){var before=result.getReturnValue();if(before!=null&&((LevelChunk)(Object)this).getLevel() instanceof ServerLevel level)NativeFluidSponge.observe(level,pos,before,after);}
}
