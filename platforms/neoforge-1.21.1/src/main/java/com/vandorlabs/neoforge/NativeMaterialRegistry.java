package com.vandorlabs.neoforge;

import com.vandorlabs.tiles.CustomMaterialIdentity;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Registry-only sampled material schema; safe on a native dedicated server. */
public final class NativeMaterialRegistry {
    public record Snapshot(Map<Integer,BlockState> states,Map<Block,Integer> samples,Set<Integer> ambiguous,Set<Integer> reservedLegacyIds,Map<BlockState,Integer> stateSamples,Set<BlockState> ambiguousStates){}
    private static Snapshot registry;
    private static volatile boolean finalized;
    public static void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event){event.enqueueWork(()->finalized=true); }
    public static synchronized Snapshot snapshot(){
        if(!finalized)throw new IllegalStateException("Sampled material snapshot requested before block/item registration finalized");
        if(registry!=null)return registry;
        if(!BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.parse("vandorlabs:programmable_block"))||!BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.parse("vandorlabs:programmable_block")))throw new IllegalStateException("Native programmable block/item absent at finalized registry boundary");
        var aliases=new LegacyVanillaMaterialAliases();var states=new HashMap<Integer,BlockState>();
        for(int choice:aliases.choices()){
            var tag=aliases.state(choice);var name=ResourceLocation.tryParse(tag.getString("Name"));
            if(name==null||!BuiltInRegistries.BLOCK.containsKey(name))continue;
            states.put(choice,NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),tag));
        }
        var samples=new HashMap<Block,Integer>();var owners=new HashMap<Integer,Block>();var ambiguous=new HashSet<Integer>();
        for(Block block:BuiltInRegistries.BLOCK){
            String name=BuiltInRegistries.BLOCK.getKey(block).toString();int choice=CustomMaterialIdentity.flattenedBlockIdentifier(name);
            if(aliases.reservedChoices().contains(choice)){ambiguous.add(choice);continue;}
            Block previous=owners.putIfAbsent(choice,block);
            if(previous!=null){ambiguous.add(choice);samples.remove(previous);states.remove(choice);continue;}
            if(ambiguous.contains(choice))continue;
            samples.put(block,choice);states.put(choice,block.defaultBlockState());
        }
        for(int key:ambiguous){Block owner=owners.get(key);if(owner!=null)samples.remove(owner);if(!aliases.reservedChoices().contains(key))states.remove(key);}
        if(!ambiguous.isEmpty())org.slf4j.LoggerFactory.getLogger(NativeMaterialRegistry.class).warn("Refusing {} ambiguous new sampled material IDs; existing saved aliases remain unchanged",ambiguous.size());
        var explicit=new ExplicitStates(states,samples,aliases.reservedChoices(),ambiguous);
        for(Block block:BuiltInRegistries.BLOCK){
            String name=BuiltInRegistries.BLOCK.getKey(block).toString();if(!name.startsWith("minecraft:"))continue;
            for(BlockState state:block.getStateDefinition().getPossibleStates()){
                if(state==block.defaultBlockState())continue;var properties=new TreeMap<String,String>();
                for(var property:state.getProperties())properties.put(property.getName(),propertyValue(state,property));
                explicit.add(state,CustomMaterialIdentity.flattenedStateIdentifier(name,properties));
            }
        }
        if(!explicit.ambiguous.isEmpty())org.slf4j.LoggerFactory.getLogger(NativeMaterialRegistry.class).warn("Refusing {} ambiguous explicit-state material samples; prior saved identities remain unchanged",explicit.ambiguous.size());
        return registry=new Snapshot(Map.copyOf(states),Map.copyOf(samples),Set.copyOf(ambiguous),aliases.reservedChoices(),Map.copyOf(explicit.samples),Set.copyOf(explicit.ambiguous));
    }
    /** One bounded registry construction; rejects conflicts without removing any prior saved identity. */
    static final class ExplicitStates {
        final Map<Integer,BlockState> states;final Map<BlockState,Integer> samples=new HashMap<>();final Set<BlockState> ambiguous=new HashSet<>();
        private final Set<Integer> protectedIds=new HashSet<>(),collisions=new HashSet<>();private final Map<Integer,BlockState> owners=new HashMap<>();
        ExplicitStates(Map<Integer,BlockState> states,Map<Block,Integer> defaults,Set<Integer> reserved,Set<Integer> priorAmbiguous){this.states=states;protectedIds.addAll(states.keySet());protectedIds.addAll(reserved);protectedIds.addAll(priorAmbiguous);defaults.forEach((block,choice)->samples.put(block.defaultBlockState(),choice));}
        void add(BlockState state,int choice){
            if(protectedIds.contains(choice)||collisions.contains(choice)){ambiguous.add(state);return;}
            var previous=owners.putIfAbsent(choice,state);
            if(previous!=null&&previous!=state){collisions.add(choice);ambiguous.add(previous);ambiguous.add(state);samples.remove(previous);states.remove(choice);return;}
            samples.put(state,choice);states.put(choice,state);
        }
    }
    private static <T extends Comparable<T>> String propertyValue(BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property){return property.getName(state.getValue(property));}
    private NativeMaterialRegistry(){}
}
