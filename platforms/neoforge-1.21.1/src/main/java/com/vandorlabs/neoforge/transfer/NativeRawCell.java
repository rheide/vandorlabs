package com.vandorlabs.neoforge.transfer;

import com.vandorlabs.neoforge.NativePlatformComponents;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable exact before image and pure staging mechanics; no ownership, publication or journal authority. */
public final class NativeRawCell {
    public final BlockPos pos;
    public final BlockState state;
    private final CompoundTag raw;
    public NativeRawCell(BlockPos pos,BlockState state,CompoundTag raw,int maximumTileBytes){
        if(maximumTileBytes<1||maximumTileBytes>8*1024*1024)throw new IllegalArgumentException("Tile snapshot byte policy");
        this.pos=Objects.requireNonNull(pos).immutable();this.state=Objects.requireNonNull(state);
        if(state.hasBlockEntity()!=(raw!=null))throw new IllegalArgumentException("Tile/state snapshot mismatch");
        if(raw!=null&&raw.sizeInBytes()>maximumTileBytes)throw new IllegalArgumentException("Tile snapshot byte bound");
        this.raw=raw==null?null:raw.copy();
    }
    public CompoundTag tile(){return raw==null?null:raw.copy();}
    public boolean hasTile(){return raw!=null;}
    /** Callers select missing legacy properties explicitly for a recognized block; supplied values are never changed. */
    public static BlockState decodePalette(HolderLookup.Provider registries,CompoundTag input,Map<String,String> missingProperties){
        if(!input.contains("Name",Tag.TAG_STRING))throw new IllegalArgumentException("Missing palette block identity");
        var id=ResourceLocation.tryParse(input.getString("Name"));if(id==null||!BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalArgumentException("Unknown palette block");
        if(input.contains("Properties")&&!input.contains("Properties",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Malformed palette properties");
        var expanded=input.copy();var properties=expanded.getCompound("Properties");for(var key:properties.getAllKeys())if(!properties.contains(key,Tag.TAG_STRING))throw new IllegalArgumentException("Malformed palette property value");
        for(var entry:missingProperties.entrySet())if(!properties.contains(entry.getKey()))properties.putString(entry.getKey(),entry.getValue());
        if(!properties.isEmpty())expanded.put("Properties",properties);
        var result=NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK),expanded);
        if(!NbtUtils.writeBlockState(result).equals(expanded))throw new IllegalArgumentException("Unknown palette properties");return result;
    }
    /** Build an unpublished tile with real physical context before any third-party deserialization callback. */
    public BlockEntity stage(ServerLevel level,Predicate<BlockEntity> supported,BiPredicate<CompoundTag,CompoundTag> equivalent){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Tile staging requires the server thread");
        if(raw==null)return null;
        if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)throw new IllegalArgumentException("Load the tile target before staging");
        if(!(state.getBlock() instanceof EntityBlock factory))throw new IllegalArgumentException("Missing tile factory");
        var staged=factory.newBlockEntity(pos,state);
        if(staged==null||!supported.test(staged))throw new IllegalArgumentException("Unsupported transfer tile");
        var identity=ResourceLocation.tryParse(raw.getString("id"));var expected=BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(staged.getType());
        if(identity==null||!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(identity)||!identity.equals(expected)||BuiltInRegistries.BLOCK_ENTITY_TYPE.get(identity)!=staged.getType())throw new IllegalArgumentException("Tile factory identity mismatch");
        var registered=staged.getType().create(pos,state);if(registered==null||registered.getClass()!=staged.getClass())throw new IllegalArgumentException("Tile factory class mismatch");
        for(var axis:List.of("x","y","z"))if(!raw.contains(axis,Tag.TAG_INT))throw new IllegalArgumentException("Tile snapshot coordinate type");
        if(raw.getInt("x")!=pos.getX()||raw.getInt("y")!=pos.getY()||raw.getInt("z")!=pos.getZ())throw new IllegalArgumentException("Tile snapshot position mismatch");
        staged.setLevel(level);NativePlatformComponents.requireSupported(raw,level.registryAccess());
        try(var guard=NativePlatformComponents.guardLoad(staged)){staged.loadWithComponents(raw.copy(),level.registryAccess());guard.requireSuccessful();}
        if(staged.getLevel()!=level||!staged.getBlockPos().equals(pos)||staged.getBlockState()!=state||!supported.test(staged)||!equivalent.test(raw.copy(),staged.saveWithFullMetadata(level.registryAccess())))throw new IllegalArgumentException("Lossy tile factory roundtrip");
        return staged;
    }
}
