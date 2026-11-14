package com.vandorlabs.neoforge.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Retain the actual native write future so a failed actor save cannot look like a completed checkpoint. */
@Mixin(EntityStorage.class)
public abstract class NativeGearEntityStoreCapture {
    @Redirect(method="storeEntities",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> vandorlabs$gearWrite(SimpleRegionStorage storage,ChunkPos chunk,CompoundTag tag){return com.vandorlabs.neoforge.gear.NativeGearActorCheckpoint.write(storage,chunk,tag);}
}
