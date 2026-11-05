package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.neoforge.fluid.client.NativeFluidClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Network state updates can skip neighbor callbacks. Refresh only near indexed wet hosts. */
@Mixin(ClientLevel.class)
public abstract class ClientFluidNeighborMixin {
    @Inject(method="setBlocksDirty", at=@At("TAIL"), require=1)
    private void vandorlabs$fluidNeighborChanged(BlockPos pos, BlockState before,
                                                BlockState after, CallbackInfo callback) {
        if (before == after || !NativeFluidClient.potentialContact(pos)) return;
        NativeFluidWorld.blockChanged((ClientLevel)(Object)this, pos);
    }
}
