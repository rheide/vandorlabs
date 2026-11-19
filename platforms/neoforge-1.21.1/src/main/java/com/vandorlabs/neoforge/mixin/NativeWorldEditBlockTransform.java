package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.compat.worldedit.NativeWorldEditRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.sk89q.worldedit.extent.transform.BlockTransformExtent", remap = false)
public abstract class NativeWorldEditBlockTransform {
    @Inject(method = "transform", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void vandorlabs$rotateWedge(@Coerce Object block, @Coerce Object transform,
                                              CallbackInfoReturnable<Object> result) {
        var rotated = NativeWorldEditRotation.transform(block, transform);
        if (rotated != null) result.setReturnValue(rotated);
    }
}
