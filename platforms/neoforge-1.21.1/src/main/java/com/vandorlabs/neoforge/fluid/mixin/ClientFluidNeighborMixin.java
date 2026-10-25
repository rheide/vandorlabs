package com.vandorlabs.neoforge.fluid.mixin;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Network block updates skip client neighbour callbacks; defer partial-fluid mask publication. */
@Mixin(ClientLevel.class)
public abstract class ClientFluidNeighborMixin {
    @Inject(method="setBlocksDirty",at=@At("TAIL"))
    private void vandor$fluidNeighborChanged(BlockPos pos,BlockState before,BlockState after,CallbackInfo callback){
        if(before!=after)NativeFluidWorld.blockChanged((ClientLevel)(Object)this,pos);
    }
}
