package com.vandorlabs.neoforge.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Client render scope only; bypasses physical bounds and cached block/chunk positions. */
@Mixin(Entity.class)
public interface NativeRampRenderPositionAccess {
    @Accessor("position") void vandorlabs$renderPosition(Vec3 position);
}
