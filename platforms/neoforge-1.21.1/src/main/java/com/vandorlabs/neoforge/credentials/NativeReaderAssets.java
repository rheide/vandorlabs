package com.vandorlabs.neoforge.credentials;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Released reader frame/material identities, independent from placement or inventory ownership. */
public final class NativeReaderAssets {
 public record Texture(String id,String original,int size,String rgbaSha256){}
 private record Data(List<String> models,List<Texture> textures,Map<String,Integer> faces){}
 private static final Data DATA;
 static {var input=NativeReaderAssets.class.getResourceAsStream("/assets/vandorlabs/data/reader_artwork.json");if(input==null)throw new IllegalStateException("Missing reader resources");try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){var data=new Gson().fromJson(reader,Data.class);DATA=new Data(List.copyOf(data.models),List.copyOf(data.textures),Map.copyOf(data.faces));}catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}}
 private NativeReaderAssets(){}
 public static List<String> models(){return DATA.models;}
 public static List<Texture> textures(){return DATA.textures;}
 public static int faces(String model){return DATA.faces.getOrDefault(model,-1);}
 public static String model(boolean chip,String facing,int rotation){return "vandorlabs:block/token_slots/"+(chip?"data_chip":"access_card")+"_slot_"+facing+"_"+((facing.equals("up")||facing.equals("down"))?rotation&3:0);}
}
