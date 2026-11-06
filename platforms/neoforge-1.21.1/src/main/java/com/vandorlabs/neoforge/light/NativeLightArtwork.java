package com.vandorlabs.neoforge.light;

import com.google.gson.Gson;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.persistence.LightData;
import com.vandorlabs.tiles.ProgrammableLightTextures;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Released lit/unlit aliases select existing catalog surfaces; explicit Off choices remain literal. */
public final class NativeLightArtwork {
    private record Alias(String on,String off,int choice,int offChoice){}
    private static final Map<Integer,Integer> OFF;
    private static final int[] DEFAULTS=new int[ProgrammableLightTextures.IDS.length];
    static {
        var ids=new HashMap<String,Integer>();for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)ids.put(ModernFinishCatalog.ENTRIES.get(i).id(),i);
        for(int i=0;i<DEFAULTS.length;i++)DEFAULTS[i]=required(ids,"light_"+ProgrammableLightTextures.IDS[i]);
        var stream=NativeLightArtwork.class.getResourceAsStream("/assets/vandorlabs/data/light_artwork.json");if(stream==null)throw new IllegalStateException("Missing Light artwork aliases");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){var mapped=new HashMap<Integer,Integer>();for(var alias:new Gson().fromJson(reader,Alias[].class)){int on=required(ids,alias.on),off=required(ids,alias.off);if(on!=alias.choice||off!=alias.offChoice)throw new IllegalStateException("Light catalog alias moved: "+alias.on);if(mapped.put(on,off)!=null)throw new IllegalStateException("Duplicate Light alias");}OFF=Map.copyOf(mapped);}catch(java.io.IOException failed){throw new ExceptionInInitializerError(failed);}
    }
    private NativeLightArtwork(){}
    private static int required(Map<String,Integer> ids,String name){var value=ids.get(name);if(value==null)throw new IllegalStateException("Missing Light artwork "+name);return value;}
    public static int defaultFace(int style){return DEFAULTS[ProgrammableLightTextures.clamp(style)];}
    public static int unlit(int face){return OFF.getOrDefault(face,face);}
    public static int face(LightData data,boolean lit){if(!lit&&data.offFaceTexture>=0)return data.offFaceTexture;int face=data.faceTexture>=0?data.faceTexture:defaultFace(data.texture);return lit?face:unlit(face);}
}
