package com.vandorlabs.neoforge.propulsion;
import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Generated authored identities; no world or gameplay objects. */
public final class NativePropulsionAssets {
 public record Model(String source,String kind,String sha256,java.util.List<com.vandorlabs.propulsion.PropulsionObjMesh.Face> geometry){public Model{geometry=geometry==null?java.util.List.of():java.util.List.copyOf(geometry);}}
 public record Texture(int width,int height,String sha256){}
 public record Variant(String model,int x,int y,boolean uvlock,String transform){}
 private record Data(Map<String,Map<String,Variant>> blocks,Map<String,Variant> items,Map<String,Model> models,Map<String,Texture> textures){}
 private static final Data DATA;
 static{var stream=NativePropulsionAssets.class.getResourceAsStream("/assets/vandorlabs/data/propulsion_artwork.json");if(stream==null)throw new IllegalStateException("Missing authored propulsion resources");try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){DATA=new Gson().fromJson(reader,Data.class);}catch(IOException failed){throw new ExceptionInInitializerError(failed);}if(DATA.blocks.size()!=27||DATA.models.size()!=2871||DATA.textures.size()!=24)throw new IllegalStateException("Authored engine coverage");}
 private NativePropulsionAssets(){}
 public static Set<String> models(){return Collections.unmodifiableSet(DATA.models.keySet());}public static Model model(String id){return DATA.models.get(id);}public static Map<String,Texture> textures(){return Collections.unmodifiableMap(DATA.textures);}
 public static Variant item(String block,int shape){shape=Math.max(0,Math.min(2,shape));var definition=com.vandorlabs.propulsion.PropulsionDefinition.of(block);String id=definition.family.isEmpty()?block:definition.family+(shape==1?"_hexagonal":shape==2?"_wedge":"");return DATA.items.get(id);}
 public static Variant variant(NativePropulsionEntity.Visual visual){var p=visual.part();String part=p.size()==1?"single":p.size()==2?p.y()==0?p.x()==0?"bottom_left":"bottom_right":p.x()==0?"top_left":"top_right":"s"+p.size()+"_x"+p.x()+"_y"+p.y();int mode=visual.powered()?visual.selected()?2:1:0;var variants=DATA.blocks.get(visual.definition().id);var selected=variants==null?null:variants.get(visual.facing().getName()+"/"+part+"/"+mode);if(selected==null)throw new IllegalArgumentException("Unsupported engine visual "+visual);return selected;}
}
