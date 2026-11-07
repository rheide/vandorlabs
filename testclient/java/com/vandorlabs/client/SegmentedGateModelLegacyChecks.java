package com.vandorlabs.client;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
/** Actual Forge model parsing/FaceBakery, before either Segmented gate renderer adapter. */
public final class SegmentedGateModelLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();FaceBakery bakery=new FaceBakery();TextureAtlasSprite sprite=new TextureAtlasSprite("segmented_gate_reference"){};sprite.setIconWidth(128);sprite.setIconHeight(128);sprite.initSprite(512,512,128,128,false);
  ModelRotation[] rotations={ModelRotation.X0_Y0,ModelRotation.X0_Y90,ModelRotation.X0_Y180,ModelRotation.X0_Y270};String[] styles={"picket","industrial","lattice"},parts={"single","left","middle","right"};Gson gson=new Gson();List<String> lines=new ArrayList<>();int quads=0,vertices=0;
  for(String style:styles)for(String part:parts){String id="vandorlabs:block/segmented_gates/"+style+"_"+part;ModelBlock model=DoorArtworkLegacyChecks.load(id);for(int rotation=0;rotation<4;rotation++){
   JsonObject record=new JsonObject();record.addProperty("id",id);record.addProperty("yaw",rotation*90);record.addProperty("ambientOcclusion",model.isAmbientOcclusion());JsonArray faces=new JsonArray();int elementIndex=0;
   for(BlockPart element:model.getElements()){for(EnumFacing facing:EnumFacing.values()){BlockPartFace authored=element.mapFaces.get(facing);if(authored==null)continue;BakedQuad quad=bakery.makeBakedQuad(element.positionFrom,element.positionTo,authored,sprite,facing,rotations[rotation],element.partRotation,false,element.shade);JsonObject face=new JsonObject();face.addProperty("element",elementIndex);face.addProperty("authoredFace",facing.getIndex());face.addProperty("face",quad.getFace().getIndex());face.addProperty("cull",authored.cullFace==null?-1:authored.cullFace.getIndex());face.addProperty("shade",quad.shouldApplyDiffuseLighting());face.addProperty("texture",model.resolveTextureName(authored.texture));JsonArray raw=new JsonArray();for(int value:quad.getVertexData())raw.add(value);face.add("vertices",raw);faces.add(face);quads++;vertices+=4;}elementIndex++;}
   record.add("quads",faces);lines.add(gson.toJson(record));
  }}
  String joined=String.join("\n",lines)+"\n";MessageDigest digest=MessageDigest.getInstance("SHA-256");StringBuilder hash=new StringBuilder();for(byte value:digest.digest(joined.getBytes("UTF-8")))hash.append(String.format("%02x",value&255));
  if(lines.size()!=48||quads!=3040||vertices!=12160)throw new AssertionError("Authored gate coverage "+lines.size()+"/"+quads+"/"+vertices);
  if(!hash.toString().equals("2c2a5246aeb2894ea2237f75f287f0a162047fd615ba2f60b8a54325d2e6d605"))throw new AssertionError("Authored Forge gate records changed "+hash);
  Path target=Paths.get(System.getProperty("vandorlabs.segmentedGateReference","build/test-artifacts/segmented-gate-forge.jsonl"));Files.createDirectories(target.toAbsolutePath().getParent());Files.write(target,joined.getBytes("UTF-8"));System.out.println("Actual Segmented gate models frozen PASS models="+lines.size()+" quads="+quads+" vertices="+vertices+" sha256="+hash);
 }
}
