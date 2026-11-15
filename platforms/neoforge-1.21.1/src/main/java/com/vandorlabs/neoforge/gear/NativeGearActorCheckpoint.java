package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.transfer.NativeEntityCheckpoint;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;

/** Arm-owned admission remains at its callers; entity file durability uses the shared native checkpoint. */
public final class NativeGearActorCheckpoint {
    private final NativeEntityCheckpoint checkpoint;
    NativeGearActorCheckpoint(ServerLevel level,Collection<ChunkPos> chunks,Runnable requireAuthority){checkpoint=new NativeEntityCheckpoint(level,chunks,requireAuthority);}
    NativeGearActorCheckpoint(ServerLevel level,Collection<ChunkPos> chunks,Runnable requireAuthority,java.util.function.BiConsumer<ChunkPos,CompoundTag> validator){checkpoint=new NativeEntityCheckpoint(level,chunks,requireAuthority,16,validator);}
    public static CompletableFuture<Void> write(SimpleRegionStorage storage,ChunkPos chunk,CompoundTag tag){return NativeEntityCheckpoint.write(storage,chunk,tag);}
    boolean loaded(){return checkpoint.loaded();}
    Entity actor(UUID identity){return checkpoint.actor(identity);}
    boolean poll(){return checkpoint.poll();}
}
