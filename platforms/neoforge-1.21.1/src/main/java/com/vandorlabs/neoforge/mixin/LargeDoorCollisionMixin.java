package com.vandorlabs.neoforge.mixin;

import com.vandorlabs.neoforge.door.NativeLargeDoorTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/** Released moved-leaf collision discovery, including panels beyond vanilla's cell scan. */
@Mixin(CollisionGetter.class)
public interface LargeDoorCollisionMixin {
    @ModifyReturnValue(method="getBlockCollisions",at=@At("RETURN"),require=1)
    private Iterable<VoxelShape> vandorlabs$largePanels(Iterable<VoxelShape> vanilla,Entity entity,AABB query){
        return (Object)this instanceof Level level?NativeLargeDoorTargets.blockCollisions(level,query,vanilla):vanilla;
    }
}
