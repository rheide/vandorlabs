package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.neoforge.shape.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/** Actual Source filled caps compared with the prepared native topology and emitted model vertices. */
public final class NativeGlassFilledContactChecks {
 public static void run(NativeDiagonalWallBlock diagonal,com.vandorlabs.neoforge.glass.NativeGlassBlock glass,Path reference)throws Exception{String frozen=Files.readString(reference,java.nio.charset.StandardCharsets.UTF_8).replace("\r\n","\n");String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(frozen.getBytes(java.nio.charset.StandardCharsets.UTF_8)));if(!hash.equals("6e32a6754dba086883f4433ec84a443486028ba6c8fb1f8663cfaf894c196d0b"))throw new AssertionError("Frozen actual Source filled cap identity "+hash);var body=new SpriteContents(ResourceLocation.parse("vandorlabs:filled-glass-body"),new FrameSize(32,32),new NativeImage(32,32,false),ResourceMetadata.EMPTY);var metal=new SpriteContents(ResourceLocation.parse("vandorlabs:filled-glass-metal"),new FrameSize(64,64),new NativeImage(64,64,false),ResourceMetadata.EMPTY);JsonArray a=new JsonArray();for(float f:new float[]{0,.125F,0,.25F})a.add(f);JsonArray b=new JsonArray();for(float f:new float[]{.75F,1,.5F,1})b.add(f);var wall=new ProgrammableModel.MaterialSnapshot(new NativeDiagonalModelChecks.Sprite(body,a,1,1,-1),1,1);var inner=new NativeDiagonalModelChecks.Sprite(metal,b,1,1,4);var bake=new NativeFlatWallModel.Bake(Map.of(0,wall),ignored->inner);int profiles=0;long capVertices=0;
  try(var reader=Files.newBufferedReader(reference)){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();NativeDiagonalModelChecks.key="filled Glass profile="+profiles+" facing="+row.get("facing")+" inverted="+row.get("inverted")+" mode="+row.get("mode")+" fill="+row.get("fill")+" contacts="+row.get("profile");var geometry=GlassFilledContactPoseChecks.geometry(row,diagonal,glass);var quads=bake.body(geometry,0,-1,0);NativeDiagonalModelChecks.compare(row.getAsJsonArray("vertices"),quads,geometry.pose().facing(),false);int caps=0;for(int i=0;i<quads.size();i+=2)for(int vertex=0;vertex<4;vertex++)if(Math.abs((byte)(quads.get(i).getVertices()[vertex*8+7]>>>8))>114)caps++;if(caps!=row.get("capVertices").getAsInt()||caps==0)throw new AssertionError("Filled Glass cap vertices");capVertices+=caps;profiles++;}}
  finally{body.close();metal.close();}if(profiles!=216)throw new AssertionError("Filled Glass Source coverage");System.out.println("[vandorlabs][glass-filled-contact] PASS SourceProfiles="+profiles+" nativeFields="+NativeDiagonalModelChecks.checks+" pairedQuads="+NativeDiagonalModelChecks.quads+" retainedCapVertices="+capVertices+" allFacings/inverted/InsideOutsideBoth/contactedEnds");
 }
 private NativeGlassFilledContactChecks(){}
}
