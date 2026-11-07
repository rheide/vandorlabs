package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.neoforge.shape.NativePanelLayout;
import com.vandorlabs.render.PanelCornerGeometry;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.core.Direction;

/** Actual clipped Source T-bar physical records, before the final GL color and world transform. */
public final class NativeFlatGlassBarChecks {
 public static void main(String[] args)throws Exception {
  Method material=NativeFlatGlassMeshChecks.class.getDeclaredMethod("material",String.class,int.class),compare=NativeFlatGlassMeshChecks.class.getDeclaredMethod("compare",JsonObject.class,List.class,boolean.class,boolean.class);material.setAccessible(true);compare.setAccessible(true);var glass=(NativePanelQuads.Material)material.invoke(null,"blocks/programmable_glass/low/soft_cyan",0);var rail=(NativePanelQuads.Material)material.invoke(null,"blocks/space_doors/medium/double_frame_metal",2);var metal=(NativePanelQuads.Material)material.invoke(null,"blocks/programmable_glass/metal_side",1);var custom=(NativePanelQuads.Material)material.invoke(null,"replacement",3);int profiles=0;
  try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();double near=row.get("near").getAsDouble(),bar=row.get("barNear").getAsDouble();boolean left=row.get("left").getAsBoolean(),replacement=row.get("material").getAsInt()>=0,selected=row.get("selected").getAsBoolean();var corner=new PanelCornerGeometry.FlatCorner(bar,bar,true,true,left,!left);var quads=NativePanelQuads.flatGlassBarPart(Direction.NORTH,near,corner,bar,row.get("role").getAsInt(),row.get("frame").getAsBoolean(),replacement?custom:glass,rail,selected?custom:metal,selected?custom:metal,1,NativePanelLayout.UNIT,1,.5,replacement);row.addProperty("kind","T-bar");try{compare.invoke(null,row,quads,false,false);}catch(InvocationTargetException error){throw new AssertionError("T profile="+profiles+" row="+row,error.getCause());}profiles++;}}
  finally{Field contents=NativeFlatGlassMeshChecks.class.getDeclaredField("CONTENTS");contents.setAccessible(true);for(var c:(List<SpriteContents>)contents.get(null))c.close();}if(profiles!=1152)throw new AssertionError("Source coverage");System.out.println("[vandorlabs][flat-glass-bar] PASS profiles="+profiles);
 }
}
