package com.vandorlabs.neoforge.mixin.optional;

import com.vandorlabs.neoforge.vehicle.NativeVehicleMekanismEscrow;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hold known finite tanks and cubes through the terrain checkpoint, including old sided views. */
@Pseudo
@Mixin(targets = "mekanism.common.tile.base.TileEntityMekanism", remap = false)
public abstract class NativeMekanismVehicleEscrow {
    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true, remap = false)
    private static void vandorlabs$freezeTile(Level level, BlockPos pos, BlockState state, @Coerce BlockEntity tile, CallbackInfo result) {
        if (NativeVehicleMekanismEscrow.tickHeld(tile)) result.cancel();
    }

    @Inject(method = {"getEnergyContainers", "getInventorySlots"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void vandorlabs$freezeSided(Direction side, CallbackInfoReturnable<List<?>> result) {
        if (side != null && NativeVehicleMekanismEscrow.blocksSided((BlockEntity)(Object)this)) {
            result.setReturnValue(List.of());
        }
    }
}
