package com.vandorlabs.neoforge.programmer;
import com.google.gson.Gson;
import com.vandorlabs.programmer.ProgrammerStatus;
import com.vandorlabs.propulsion.PropulsionObjMesh;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Immutable authored resource descriptors; no world, inventory or registration. */
public final class NativeProgrammerAssets {
 public record Model(String source,String sha256,List<PropulsionObjMesh.Face> geometry){public Model{geometry=List.copyOf(geometry);}}
 public record Texture(int width,int height,String sha256){}
 private record Data(Map<String,Map<String,String>> states,String item,Map<String,Model> models,Map<String,Texture> textures){}
 private static final Data DATA;
 static{var stream=NativeProgrammerAssets.class.getResourceAsStream("/assets/vandorlabs/data/programmer_artwork.json");if(stream==null)throw new IllegalStateException("Missing authored Programmer resources");try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){DATA=new Gson().fromJson(reader,Data.class);}catch(IOException failure){throw new ExceptionInInitializerError(failure);}if(DATA.states.size()!=4||DATA.models.size()!=21)throw new IllegalStateException("Programmer authored coverage");}
 private NativeProgrammerAssets(){}
 public static Set<String> models(){return Collections.unmodifiableSet(DATA.models.keySet());}
 public static Model model(String id){return DATA.models.get(id);}
 public static Map<String,Texture> textures(){return Collections.unmodifiableMap(DATA.textures);}
 public static String part(ProgrammerStatus status,String part){return DATA.states.get(status.resourceName).get(part);}
 public static String item(){return DATA.item;}
}
