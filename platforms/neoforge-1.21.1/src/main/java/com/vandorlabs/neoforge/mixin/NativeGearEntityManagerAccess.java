package com.vandorlabs.neoforge.mixin;

import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Use the normal loaded-actor serializer, never a raw replacement of an active entity region. */
@Mixin(PersistentEntitySectionManager.class)
public interface NativeGearEntityManagerAccess {
    @Invoker("storeChunkSections") boolean vandorlabs$gearStore(long chunk,Consumer<Entity> stored);
    @Invoker("requestChunkLoad") void vandorlabs$gearLoad(long chunk);
    @Invoker("processPendingLoads") void vandorlabs$gearInbox();
}
