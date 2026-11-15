package com.vandorlabs.client;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;

/** Actual Source vanilla property schema, exported without a world or modded numeric registry assignments. */
public final class LegacyConstructionStateSchemaChecks {
    private static <T extends Comparable<T>> void property(JsonObject schema,IProperty<T> property){List<String> names=new ArrayList<String>();for(T value:property.getAllowedValues())names.add(property.getName(value));Collections.sort(names);JsonArray values=new JsonArray();for(String value:names)values.add(value);schema.add(property.getName(),values);}
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();TreeMap<String,JsonObject> entries=new TreeMap<String,JsonObject>();int properties=0,values=0;
        for(Block block:Block.REGISTRY){net.minecraft.util.ResourceLocation id=Block.REGISTRY.getNameForObject(block);if(id==null||!id.getResourceDomain().equals("minecraft"))continue;IBlockState state=block.getDefaultState();TreeMap<String,IProperty<?>> ordered=new TreeMap<String,IProperty<?>>();for(IProperty<?> property:state.getPropertyKeys())ordered.put(property.getName(),property);JsonObject schema=new JsonObject();for(IProperty<?> property:ordered.values()){property(schema,property);properties++;values+=property.getAllowedValues().size();}if(entries.put(id.toString(),schema)!=null)throw new AssertionError("Duplicate vanilla state identity");}
        if(entries.size()!=254||!entries.containsKey("minecraft:air")||!entries.get("minecraft:chest").has("facing")||!entries.get("minecraft:stone").has("variant"))throw new AssertionError("Source vanilla state schema boundary");
        JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("sourceDataVersion",1343);JsonObject blocks=new JsonObject();for(Map.Entry<String,JsonObject> entry:entries.entrySet())blocks.add(entry.getKey(),entry.getValue());root.add("blocks",blocks);byte[] bytes=(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root)+"\n").getBytes(StandardCharsets.UTF_8);Path path=Paths.get(args[0]);if(args.length==2&&args[1].equals("--write")){Files.createDirectories(path.getParent());Files.write(path,bytes);}else if(!Arrays.equals(bytes,Files.readAllBytes(path)))throw new AssertionError("Source vanilla construction state schema drift");
        System.out.println("Source construction state schema PASS blocks="+entries.size()+" properties="+properties+" values="+values+" bytes="+bytes.length);
    }
    private LegacyConstructionStateSchemaChecks(){}
}
