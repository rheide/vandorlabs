package com.vandorlabs.neoforge.glass;

import com.google.gson.*;
import com.vandorlabs.render.FlatGlassPolicy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable authored default-frame/item identities, independent of world, tiles and renderer state. */
public final class NativeFlatGlassAssets {
 public record Model(String source,String sha256){}
 public record Variant(String model,int y){}
 private record Rule(JsonObject when,String model,int y){}
 private record Data(Map<String,Model> models,Map<String,String> textures,List<Rule> rules,List<String> items){}
 private static final Data DATA;private static final List<Variant>[] PARTS;
 static {try(var stream=NativeFlatGlassAssets.class.getResourceAsStream("/assets/vandorlabs/data/flat_glass_artwork.json")){if(stream==null)throw new IllegalStateException("Missing authored flat glass resources");try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){DATA=new Gson().fromJson(reader,Data.class);}}catch(IOException failed){throw new ExceptionInInitializerError(failed);}if(DATA.models.size()!=352||DATA.items.size()!=304||DATA.rules.size()!=144)throw new IllegalStateException("Released flat glass artwork coverage");PARTS=compile();}
 private NativeFlatGlassAssets(){}
 @SuppressWarnings("unchecked")private static List<Variant>[] compile(){List<Variant>[] result=new List[3*3*2*256];for(int size=0;size<3;size++)for(int depth=0;depth<3;depth++)for(int rotated=0;rotated<2;rotated++)for(int mask=0;mask<256;mask++){var selected=new ArrayList<Variant>();for(var rule:DATA.rules)if(matches(rule.when,size,depth,rotated!=0,mask))selected.add(new Variant(rule.model,rule.y));result[key(size,depth,rotated!=0,mask)]=List.copyOf(selected);}return result;}
 private static boolean matches(JsonObject when,int size,int depth,boolean rotated,int mask){for(var entry:when.entrySet()){var value=entry.getValue();if(entry.getKey().equals("AND")){for(var child:value.getAsJsonArray())if(!matches(child.getAsJsonObject(),size,depth,rotated,mask))return false;}else if(entry.getKey().equals("OR")){boolean any=false;for(var child:value.getAsJsonArray())any|=matches(child.getAsJsonObject(),size,depth,rotated,mask);if(!any)return false;}else{String actual=switch(entry.getKey()){case "size"->Integer.toString(size);case "depth"->Integer.toString(depth);case "rotated"->Boolean.toString(rotated);case "left"->Boolean.toString((mask&1)!=0);case "right"->Boolean.toString((mask&2)!=0);case "bottom"->Boolean.toString((mask&4)!=0);case "top"->Boolean.toString((mask&8)!=0);case "inner_bl"->Boolean.toString((mask&16)!=0);case "inner_br"->Boolean.toString((mask&32)!=0);case "inner_tl"->Boolean.toString((mask&64)!=0);case "inner_tr"->Boolean.toString((mask&128)!=0);default->throw new IllegalArgumentException("Unknown released glass multipart field "+entry.getKey());};if(!actual.equals(value.getAsString()))return false;}}return true;}
 private static int key(int size,int depth,boolean rotated,int mask){return (((size*3+depth)*2+(rotated?1:0))<<8)|mask;}
 public static List<Variant> frame(FlatGlassPolicy.Pose pose,int mask){if((mask&~255)!=0)throw new IllegalArgumentException("Glass frame mask");return PARTS[key(pose.size,pose.depth,pose.rotated,mask)];}
 public static Set<String> models(){return Collections.unmodifiableSet(DATA.models.keySet());}
 public static Model model(String id){return DATA.models.get(id);}
 public static List<String> items(){return Collections.unmodifiableList(DATA.items);}
 public static String texture(String source){return DATA.textures.get(source);}
 public static String item(int size,int shade,int tint,boolean framed){int index=(((Math.max(0,Math.min(1,size))*com.vandorlabs.tiles.GlassTextures.IDS.length+com.vandorlabs.tiles.GlassTextures.clamp(shade))*4+com.vandorlabs.tiles.GlassTextures.clampTint(tint))*2)+(framed?1:0);return DATA.items.get(index);}
}
