package com.vandorlabs.neoforge;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;

/** Bounded fallback to this family's authored geometry when an external model cannot be frozen. */
final class NativeDoorCanonicalBake {
 private NativeDoorCanonicalBake(){}
 private static JsonObject resolved(String id,int depth){if(depth>16)throw new IllegalArgumentException("Cyclic canonical Door parent "+id);String[] name=id.split(":",2);String path="/assets/"+name[0]+"/models/"+name[1]+".json";var input=NativeDoorCanonicalBake.class.getResourceAsStream(path);if(input==null)throw new IllegalArgumentException("Missing canonical Door "+id);try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){var value=JsonParser.parseReader(reader).getAsJsonObject();JsonObject result=value.has("parent")?resolved(value.get("parent").getAsString(),depth+1):new JsonObject();JsonObject textures=result.has("textures")?result.getAsJsonObject("textures").deepCopy():new JsonObject();if(value.has("textures"))for(var entry:value.getAsJsonObject("textures").entrySet())textures.add(entry.getKey(),entry.getValue());for(var entry:value.entrySet())if(!entry.getKey().equals("parent")&&!entry.getKey().equals("textures"))result.add(entry.getKey(),entry.getValue());result.add("textures",textures);return result;}catch(java.io.IOException failure){throw new IllegalStateException("Canonical Door "+id,failure);}}
 static BakedModel model(String id,Function<Material,TextureAtlasSprite> textures){var parsed=BlockModel.fromString(resolved(id,0).toString());var builder=new SimpleBakedModel.Builder(false,true,true,ItemTransforms.NO_TRANSFORMS,ItemOverrides.EMPTY).particle(textures.apply(parsed.getMaterial("particle")));var bakery=new FaceBakery();for(var element:parsed.getElements())for(var face:element.faces.entrySet())builder.addUnculledFace(bakery.bakeQuad(element.from,element.to,face.getValue(),textures.apply(parsed.getMaterial(face.getValue().texture())),face.getKey(),BlockModelRotation.X0_Y0,element.rotation,element.shade));return builder.build();}
}
