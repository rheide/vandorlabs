package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.*;
import java.util.*;

/** Actual picker membership and half-display categories, without a window or texture upload. */
public final class ScreenPickerLegacyChecks {
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();
        for(java.lang.reflect.Field field:net.minecraft.client.resources.I18n.class.getDeclaredFields())if(field.getType()==net.minecraft.client.resources.Locale.class){field.setAccessible(true);if(field.get(null)==null)field.set(null,new net.minecraft.client.resources.Locale());}
        ModBlocks.BLOCKS.size();
        java.lang.reflect.Field delegate=ScreenTextureList.class.getDeclaredField("list"),options=HousingTextureList.class.getDeclaredField("options");delegate.setAccessible(true);options.setAccessible(true);
        JsonArray profiles=new JsonArray();int accepted=0;
        for(boolean input:new boolean[]{false,true}){
            String[] ids=input?TileEntityAnimatedScreenSelector.INPUT_PANELS:ModBlocks.DISPLAY_SCREEN_IDS.toArray(new String[0]);
            ScreenTextureList picker=new ScreenTextureList(0,0,200,8,ids[0],ids,null,input);
            Map<?,?> actual=(Map<?,?>)options.get(delegate.get(picker));JsonObject row=new JsonObject();row.addProperty("input",input);JsonArray choices=new JsonArray();int half=0;
            for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(actual.containsKey(i)){
                choices.add(i);accepted++;HousingTextureList.Option option=(HousingTextureList.Option)actual.get(i);
                com.google.gson.JsonObject entry=ScreenHousingTextures.entry(i);
                boolean halfDisplay=entry!=null&&"Screens".equals(ScreenHousingTextures.category(i))&&entry.has("menuGroup")&&entry.get("source").getAsString().startsWith("console_inputs/");
                if(halfDisplay!=option.category.startsWith("Half Displays/"))throw new AssertionError("Half display category membership: "+i);
                if(halfDisplay)half++;
            }
            if(half!=123)throw new AssertionError("All half displays must be available for input="+input+", got "+half);
            row.add("choices",choices);profiles.add(row);
        }
        java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
        JsonObject root=new JsonObject();root.addProperty("sha256",digest(hash,profiles.toString()));root.addProperty("materials",ScreenHousingTextures.IDS.length);root.add("profiles",profiles);
        Map<?,?> split=(Map<?,?>)options.get(HousingTextureList.forCategory("Screens",0,0,200,0));JsonArray splitChoices=new JsonArray();int splitHalf=0;
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(split.containsKey(i)){splitChoices.add(i);if(((HousingTextureList.Option)split.get(i)).category.startsWith("Half Displays/"))splitHalf++;}
        if(splitHalf!=123)throw new AssertionError("All half displays must be available in split artwork, got "+splitHalf);
        root.add("split",splitChoices);root.addProperty("splitSha256",digest(hash,splitChoices.toString()));
        java.nio.file.Path path=java.nio.file.Paths.get("build/test-artifacts/screen-picker-reference.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.write(path,root.toString().getBytes("UTF-8"));
        System.out.println("Actual Screen picker reference PASS profiles="+profiles.size()+" accepted="+accepted+" materials="+ScreenHousingTextures.IDS.length+" halfDisplays=123 split="+splitChoices.size());
    }
    private static String digest(java.security.MessageDigest hash,String text)throws Exception{
        StringBuilder result=new StringBuilder();for(byte b:hash.digest(text.getBytes("UTF-8")))result.append(String.format("%02x",b&255));return result.toString();
    }
}
