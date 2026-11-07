package com.vandorlabs.neoforge.mixin;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.chunk.storage.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(EntityStorage.class)
public interface NativeRampEntityStorageAccess {
 @Accessor("simpleRegionStorage") SimpleRegionStorage vandorlabs$rampActorRegions();
 @Accessor("emptyChunks") LongSet vandorlabs$rampActorEmptyChunks();
}
