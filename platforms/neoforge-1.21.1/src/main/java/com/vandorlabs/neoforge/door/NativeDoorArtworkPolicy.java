package com.vandorlabs.neoforge.door;

import com.google.gson.*;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored source decisions, kept independent from labels and grouped menu categories. */
public final class NativeDoorArtworkPolicy {
 public record Artwork(boolean mirrored,boolean reversed,boolean light){}
 private static final Artwork DEFAULT=new Artwork(false,false,false);
 private static final Map<String,Artwork> ARTWORK;
 static{var input=NativeDoorArtworkPolicy.class.getResourceAsStream("/assets/vandorlabs/data/door_material_policy.json");if(input==null)throw new IllegalStateException("Missing Door artwork policy");try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){Map<String,Artwork> values=new HashMap<>();var gson=new Gson();for(var entry:JsonParser.parseReader(reader).getAsJsonObject().entrySet())values.put(entry.getKey(),gson.fromJson(entry.getValue(),Artwork.class));ARTWORK=Map.copyOf(values);}catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}}
 private NativeDoorArtworkPolicy(){}
 public static Artwork artwork(int choice){return ARTWORK.getOrDefault(ModernFinishCatalog.option(choice).id(),DEFAULT);}
 public static boolean mirror(int choice,boolean right,boolean custom){var policy=artwork(choice);return policy.reversed()^(right&&(custom||ModernFinishCatalog.option(choice).texture().startsWith("vandorlabs:filesystem/")||policy.mirrored()));}
}
