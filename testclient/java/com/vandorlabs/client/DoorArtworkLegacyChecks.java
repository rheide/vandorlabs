package com.vandorlabs.client;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
/** Actual Forge FaceBakery records from the current authored item graph, without a GL context. */
public final class DoorArtworkLegacyChecks {
 static final Map<String,ModelBlock> MODELS=new HashMap<>();
 static ModelBlock load(String id)throws Exception{
  ModelBlock found=MODELS.get(id);if(found!=null)return found;String[] name=id.split(":",2);String path=name[1];if(!path.startsWith("item/")&&!path.startsWith("block/"))path="block/"+path;InputStream input=DoorArtworkLegacyChecks.class.getResourceAsStream("/assets/"+name[0]+"/models/"+path+".json");if(input==null)throw new AssertionError("Missing actual source model "+id);ModelBlock model;try(Reader reader=new InputStreamReader(input,"UTF-8")){model=ModelBlock.deserialize(reader);}MODELS.put(id,model);if(model.getParentLocation()!=null)model.parent=load(model.getParentLocation().toString());return model;
 }
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();JsonObject manifest=new JsonParser().parse(new FileReader(args[0])).getAsJsonObject();FaceBakery bakery=new FaceBakery();TextureAtlasSprite sprite=new TextureAtlasSprite("source_reference"){};sprite.setIconWidth(128);sprite.setIconHeight(128);sprite.initSprite(512,512,128,128,false);Gson gson=new Gson();File target=new File(args[1]);target.getAbsoluteFile().getParentFile().mkdirs();int models=0,quads=0;
  try(PrintWriter writer=new PrintWriter(target,"UTF-8")){
   for(JsonElement name:manifest.getAsJsonArray("models")){String id=name.getAsString();ModelBlock model=load(id);JsonObject record=new JsonObject();record.addProperty("id",id);JsonArray emitted=new JsonArray();
    for(BlockPart part:model.getElements())for(Map.Entry<EnumFacing,BlockPartFace> face:part.mapFaces.entrySet()){
     BakedQuad q=bakery.makeBakedQuad(part.positionFrom,part.positionTo,face.getValue(),sprite,face.getKey(),ModelRotation.X0_Y0,part.partRotation,false,part.shade);JsonObject quad=new JsonObject();quad.addProperty("face",q.getFace().getIndex());quad.addProperty("shade",q.shouldApplyDiffuseLighting());quad.addProperty("texture",model.resolveTextureName(face.getValue().texture));JsonArray data=new JsonArray();int[] raw=q.getVertexData();int stride=raw.length/4;for(int v=0;v<4;v++){for(int a=0;a<3;a++)data.add(raw[v*stride+a]);data.add(raw[v*stride+6]&0xffffff);}quad.add("vertices",data);emitted.add(quad);quads++;
    }record.add("quads",emitted);writer.println(gson.toJson(record));models++;
   }
  }if(models!=1924||quads!=64968)throw new AssertionError("Actual authored coverage "+models+"/"+quads);System.out.println("Door artwork actual Forge FaceBakery PASS models="+models+" quads="+quads+" original float positions/directions/normal bytes/shade flags");
 }
}
