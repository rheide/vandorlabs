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
    public record Snapshot(Map<Integer,BlockState> states,Map<Block,Integer> samples,Set<Integer> ambiguous,Set<Integer> reservedLegacyIds){}
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
        return registry=new Snapshot(Map.copyOf(states),Map.copyOf(samples),Set.copyOf(ambiguous),aliases.reservedChoices());
    }
    private NativeMaterialRegistry(){}
}
