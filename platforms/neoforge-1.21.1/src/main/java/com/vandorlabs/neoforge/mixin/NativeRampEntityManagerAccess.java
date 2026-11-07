package com.vandorlabs.neoforge.mixin;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Queue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(PersistentEntitySectionManager.class)
public interface NativeRampEntityManagerAccess {
 @Accessor("permanentStorage") EntityPersistentStorage<Entity> vandorlabs$rampActorStorage();
 @Accessor("chunkLoadStatuses") Long2ObjectMap<?> vandorlabs$rampActorLoads();
 @Accessor("loadingInbox") Queue<ChunkEntities<Entity>> vandorlabs$rampActorInbox();
 @Accessor("sectionStorage") EntitySectionStorage<Entity> vandorlabs$rampActorSections();
}
