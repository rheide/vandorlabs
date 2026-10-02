package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import java.lang.reflect.*;

/** Vanilla calls updateController after its voxel raycast and before processing held input. */
final class OffsetTrapdoorController extends PlayerControllerMP {
    OffsetTrapdoorController(Minecraft mc,PlayerControllerMP previous) {
        super(mc,mc.getConnection());
        try {
            // Preserve game mode, selected slot, and any breaking state on installation.
            for(Field field:PlayerControllerMP.class.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                field.setAccessible(true);field.set(this,field.get(previous));
            }
        } catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot preserve client controller state",e);}
    }
    @Override public void updateController() {
        super.updateController();OffsetTrapdoorSelection.updateTarget(1);
    }
}
