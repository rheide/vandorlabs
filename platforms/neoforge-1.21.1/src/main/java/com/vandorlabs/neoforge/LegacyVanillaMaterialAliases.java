package com.vandorlabs.neoforge;

import com.mojang.serialization.Dynamic;
import com.vandorlabs.tiles.CustomMaterialIdentity;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.util.datafix.fixes.References;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Saved-ID aliases only. Does not discover materials or convert worlds, items or tiles. */
public final class LegacyVanillaMaterialAliases {
    private record Source(int block,String name,int metadata){}
    private static final String RESOURCE="/assets/vandorlabs/data/legacy_vanilla_blocks.tsv";
    private final Map<Integer,String> names;
    private final Map<Integer,Source> choices;
    private final int[][] normalized=new int[256][];
    // Exactly one fixed-size slot per possible legacy vanilla state; no caller-controlled growth.
    private final CompoundTag[] upgraded=new CompoundTag[4096];

    public LegacyVanillaMaterialAliases(){
        var stream=LegacyVanillaMaterialAliases.class.getResourceAsStream(RESOURCE);
        if(stream==null)throw new IllegalStateException("Missing legacy vanilla block manifest");
        var parsed=new HashMap<Integer,String>();var aliases=new HashMap<Integer,Source>();
        try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
            for(String line;(line=reader.readLine())!=null;){
                if(line.isBlank()||line.startsWith("#"))continue;
                String[] fields=line.split("\\t",-1);
                if(fields.length!=3||!fields[1].matches("minecraft:[a-z0-9_]+"))throw new IllegalStateException("Invalid legacy vanilla manifest row");
                int block=Integer.parseInt(fields[0]);
                if(block<0||block>255||parsed.putIfAbsent(block,fields[1])!=null||Collections.frequency(parsed.values(),fields[1])!=1)throw new IllegalStateException("Duplicate/out-of-range legacy vanilla registry entry");
                String[] slots=fields[2].split(",",-1);
                if(slots.length!=16)throw new IllegalStateException("Missing legacy metadata normalization slots");
                normalized[block]=new int[16];
                for(int metadata=0;metadata<16;metadata++){
                    int canonical=Integer.parseInt(slots[metadata]);
                    if(canonical< -1||canonical>15)throw new IllegalStateException("Invalid canonical legacy metadata");
                    normalized[block][metadata]=canonical;
                    if(canonical<0)continue;
                    int choice=CustomMaterialIdentity.identifier(fields[1],metadata);
                    if(aliases.putIfAbsent(choice,new Source(block,fields[1],metadata))!=null)throw new IllegalStateException("Legacy saved material CRC collision");
                }
            }
        }catch(IOException|NumberFormatException failure){throw new IllegalStateException("Unreadable legacy vanilla block manifest",failure);}
        if(parsed.size()!=254||!"minecraft:air".equals(parsed.get(0)))throw new IllegalStateException("Incomplete legacy vanilla block manifest");
        names=Map.copyOf(parsed);choices=Map.copyOf(aliases);
    }
    public int size(){return choices.size();}
    public boolean contains(int savedChoice){return choices.containsKey(savedChoice);}
    /** Unknown IDs return null; callers retain their original saved integer and show a fallback. */
    public CompoundTag state(int savedChoice){
        Source source=choices.get(savedChoice);
        return source==null?null:state(source.block,source.metadata);
    }
    /** Strict range/manifest guards prevent BlockStateData's silent unknown-to-AIR fallback. */
    public synchronized CompoundTag state(int oldNumericBlockId,int metadata){
        if(metadata<0||metadata>15||!names.containsKey(oldNumericBlockId))return null;
        int canonical=normalized[oldNumericBlockId][metadata];
        if(canonical<0)return null;
        int packed=(oldNumericBlockId<<4)|canonical;
        if(upgraded[packed]==null){
            Dynamic<?> flattened=BlockStateData.getTag(packed);
            String flattenedName=flattened.get("Name").asString("");
            if(flattenedName.isEmpty()||("minecraft:air".equals(flattenedName)&&oldNumericBlockId!=0))throw new IllegalStateException("Missing flattening state for known legacy block "+oldNumericBlockId);
            Dynamic<?> current=DataFixers.getDataFixer().update(References.BLOCK_STATE,flattened,1451,SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            var tag=current.convert(NbtOps.INSTANCE).getValue();
            if(!(tag instanceof CompoundTag compound)||!compound.contains("Name",8)||("minecraft:air".equals(compound.getString("Name"))&&oldNumericBlockId!=0))throw new IllegalStateException("Invalid flattened legacy block state");
            upgraded[packed]=compound.copy();
        }
        return upgraded[packed].copy();
    }
}
