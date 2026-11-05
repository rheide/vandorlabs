package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.tiles.CustomBlockMaterials;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Narrow actual legacy material path assessment, separate from the gameplay utility matrix. */
final class LegacyMaterialSpriteChecks {
    private static JsonArray sample(){
        JsonArray result=new JsonArray();TreeMap<String,Block> blocks=new TreeMap<>();
        for(Block block:Block.REGISTRY)if(block.getRegistryName()!=null&&"minecraft".equals(block.getRegistryName().getResourceDomain()))blocks.put(block.getRegistryName().toString(),block);
        for(Map.Entry<String,Block> entry:blocks.entrySet()){
            int choice=CustomBlockMaterials.identifier(entry.getValue(),0);JsonObject row=new JsonObject();row.addProperty("name",entry.getKey());row.addProperty("choice",choice);row.addProperty("state",entry.getValue().getStateFromMeta(0).toString());
            row.addProperty("lower",CustomBlockTextures.sprite(choice,false).getIconName());row.addProperty("upper",CustomBlockTextures.sprite(choice,true).getIconName());row.addProperty("door",CustomBlockTextures.isDoor(choice));result.add(row);
        }
        if(result.size()!=254)throw new AssertionError("Unexpected vanilla sample coverage "+result.size());
        return result;
    }
    static void run(Minecraft mc,File output)throws Exception{
        InventorySampleGridRuntimeChecks.run(mc);
        ItemStack door=new ItemStack(Items.OAK_DOOR,1,13);int choice=CustomBlockMaterials.choice(door);
        if(choice!=0x33011d3d||CustomBlockMaterials.block(door)!=Blocks.OAK_DOOR||!CustomBlockTextures.isDoor(choice))throw new AssertionError("Actual loaded-client door mapping/saved ID");
        if(CustomBlockTextures.sprite(choice,false)==CustomBlockTextures.sprite(choice,true))throw new AssertionError("Door upper/lower artwork collapsed");
        JsonArray before=sample();mc.refreshResources();JsonArray after=sample();
        if(!before.equals(after))throw new AssertionError("Legacy selected sprites changed after reload");
        JsonObject root=new JsonObject();root.addProperty("format",1);root.addProperty("reloadStable",true);root.add("rows",after);
        try(Writer writer=new OutputStreamWriter(new FileOutputStream(new File(output,"legacy-material-sprites.json")),StandardCharsets.UTF_8)){new GsonBuilder().setPrettyPrinting().create().toJson(root,writer);}
        System.out.println("[vandorlabs][material-sprites] legacy PASS vanilla=254 actual CustomBlockTextures, door item metadata normalization and both halves, particle fallback, reload stable");
    }
}
