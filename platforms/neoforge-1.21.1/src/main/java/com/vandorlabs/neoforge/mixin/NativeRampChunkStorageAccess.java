package com.vandorlabs.neoforge.mixin;
import net.minecraft.world.level.chunk.storage.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Asynchronous region durability; callers never wait for disk on the server thread. */
@Mixin(ChunkStorage.class)
public interface NativeRampChunkStorageAccess {
 @Accessor("worker") IOWorker vandorlabs$rampWorker();
}
