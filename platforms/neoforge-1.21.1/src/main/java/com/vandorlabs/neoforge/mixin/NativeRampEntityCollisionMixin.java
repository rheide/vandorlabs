package com.vandorlabs.neoforge.mixin;

import java.util.List;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.vandorlabs.neoforge.ramp.NativeRampRiders;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Released recent-floor correction also covers proven entity support hulls. */
@Mixin(EntityGetter.class)
public interface NativeRampEntityCollisionMixin {
    @ModifyReturnValue(method="getEntityCollisions",at=@At("RETURN"),require=1)
    private List<VoxelShape> vandorlabs$recentSupport(List<VoxelShape> original,Entity entity,AABB query){
        return (Object)this instanceof Level level?NativeRampRiders.recentEntityCollisions(level,entity,original):original;
    }
}
