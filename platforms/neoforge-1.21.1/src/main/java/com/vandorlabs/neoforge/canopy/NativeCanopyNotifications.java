package com.vandorlabs.neoforge.canopy;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Cooperative mechanisms may queue a retry when a loaded owner changes; never mutate the owner here. */
public final class NativeCanopyNotifications {
    private static volatile BiConsumer<ServerLevel,BlockPos> listener;
    private NativeCanopyNotifications(){}
    public static synchronized BiConsumer<ServerLevel,BlockPos> bindChanged(BiConsumer<ServerLevel,BlockPos> next){var previous=listener;listener=next;return previous;}
    static void changed(NativeCanopyEntity host){var callback=listener;if(callback!=null&&host.getLevel() instanceof ServerLevel world&&world.getServer().isSameThread())callback.accept(world,NativeCanopyLifecycle.owner(host).getBlockPos());}
}
