package com.vandorlabs.neoforge.door;

import com.google.gson.Gson;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored model/material identities and content UVs; no world or per-host state. */
public final class NativeDoorAssets {
    public record Texture(String id,String original,int width,int height,int size,float usedU,float usedV,boolean alphaMips){}
    private record Data(List<String> models,List<Texture> textures,Map<String,String> previews,Map<String,Integer> faces){}
    private static final Data DATA;
    private static final Map<String,Texture> TEXTURES;
    private static final List<String> MODELS;
    private static final ModernFinishCatalog.Entry[] PREVIEWS;
    static{
        var input=NativeDoorAssets.class.getResourceAsStream("/assets/vandorlabs/data/door_artwork.json");if(input==null)throw new IllegalStateException("Missing authored door resources");
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){DATA=new Gson().fromJson(reader,Data.class);}catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}
        var textures=new HashMap<String,Texture>();for(var texture:DATA.textures){if(texture.size<1||texture.width<1||texture.height<1||texture.width>texture.size||texture.height>texture.size||textures.put(texture.id,texture)!=null)throw new IllegalStateException("Invalid door sprite descriptor");}TEXTURES=Map.copyOf(textures);MODELS=List.copyOf(DATA.models);
        PREVIEWS=new ModernFinishCatalog.Entry[ModernFinishCatalog.ENTRIES.size()];for(int i=0;i<PREVIEWS.length;i++){var entry=ModernFinishCatalog.ENTRIES.get(i);String id=DATA.previews.get(entry.id());if(id!=null){var texture=TEXTURES.get(id);PREVIEWS[i]=new ModernFinishCatalog.Entry(entry.id(),entry.label(),entry.category(),entry.visible(),entry.general(),entry.canonical(),id,texture.usedU,texture.usedV,entry.displaySelectable(),entry.frame(),entry.playback(),entry.door());}}
    }
    private NativeDoorAssets(){}
    public static List<String> models(){return MODELS;}
    public static Collection<Texture> textures(){return TEXTURES.values();}
    public static Texture texture(String id){return TEXTURES.get(id);}
    public static int faces(String id){return DATA.faces.getOrDefault(id,-1);}
    /** Door previews retain complete1:2 artwork instead of the cube catalog's lower face crop. */
    /** Catalog category paths include company submenus while artwork-detail filtering stays unchanged. */
    public static boolean selectable(ModernFinishCatalog.Entry entry,boolean large,int detail){
        String category=entry.category();boolean door=category.equals("Doors")||category.startsWith("Doors/")||large&&(category.equals("Double Doors")||category.startsWith("Double Doors/"));
        return entry.general()||door&&(entry.door()==null||entry.door().detail()==detail);
    }
    public static ModernFinishCatalog.Entry preview(int choice){
        if(choice>=0&&choice<PREVIEWS.length){var entry=PREVIEWS[choice];if(entry!=null)return entry;}return ModernFinishCatalog.option(choice);
    }
}
