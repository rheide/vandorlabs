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
    private static void retiredFuelPicker(Minecraft mc)throws Exception{
        for(String id:new String[]{"imported_fuel_fuel_port_dual","imported_fuel_fuel_port_round"}){
            int index=java.util.Arrays.asList(com.vandorlabs.tiles.ScreenHousingTextures.IDS).indexOf(id);
            if(index<0||com.vandorlabs.tiles.ScreenHousingTextures.visible(index))throw new AssertionError("Retired Fuel Port lost its saved slot or remains selectable: "+id);
            if(!com.vandorlabs.tiles.ScreenHousingTextures.texture(index).equals(com.vandorlabs.tiles.ScreenHousingTextures.texture(0)))throw new AssertionError("Retired Fuel Port has no compatibility fallback: "+id);
        }
        HousingTextureList picker=new HousingTextureList(20,40,220,-1,8);
        picker.searchClick(25,47,0);for(char c:"Fuel Port".toCharArray())picker.searchKey(c,0);
        java.lang.reflect.Field rows=HousingTextureList.class.getDeclaredField("rows");rows.setAccessible(true);
        if(!((java.util.List<?>)rows.get(picker)).isEmpty())throw new AssertionError("Retired Fuel Ports remain in the real picker search");
        picker.draw(mc.fontRenderer,-1,-1);
    }
    static void run(Minecraft mc,File output)throws Exception{
        retiredFuelPicker(mc);
        InventorySampleGridRuntimeChecks.run(mc);
        ItemStack door=new ItemStack(Items.OAK_DOOR,1,13);int choice=CustomBlockMaterials.choice(door);
        if(choice!=0x33011d3d||CustomBlockMaterials.block(door)!=Blocks.OAK_DOOR||!CustomBlockTextures.isDoor(choice))throw new AssertionError("Actual loaded-client door mapping/saved ID");
        if(CustomBlockTextures.sprite(choice,false)==CustomBlockTextures.sprite(choice,true))throw new AssertionError("Door upper/lower artwork collapsed");
        JsonArray before=sample();mc.refreshResources();JsonArray after=sample();retiredFuelPicker(mc);
        if(!before.equals(after))throw new AssertionError("Legacy selected sprites changed after reload");
        JsonObject root=new JsonObject();root.addProperty("format",1);root.addProperty("reloadStable",true);root.add("rows",after);
        try(Writer writer=new OutputStreamWriter(new FileOutputStream(new File(output,"legacy-material-sprites.json")),StandardCharsets.UTF_8)){new GsonBuilder().setPrettyPrinting().create().toJson(root,writer);}
        System.out.println("[vandorlabs][material-sprites] retired-fuel-ports PASS savedSlots/pickerSearch/fallback/reload");
        System.out.println("[vandorlabs][material-sprites] legacy PASS vanilla=254 actual CustomBlockTextures, door item metadata normalization and both halves, particle fallback, reload stable");
    }
}
