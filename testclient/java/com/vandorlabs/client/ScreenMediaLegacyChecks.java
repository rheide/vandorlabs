package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.tiles.*;
import java.lang.reflect.Method;
import java.nio.file.*;

/** Actual source category/crop predicates and builtin-input bindings, before native material adaptation. */
public final class ScreenMediaLegacyChecks {
 public static void main(String[] args)throws Exception {
  net.minecraft.init.Bootstrap.register();Method engineering=TEAnimatedScreenSelector.class.getDeclaredMethod("engineeringChoice",int.class),unpaired=TEAnimatedScreenSelector.class.getDeclaredMethod("unpairedInputChoice",int.class);engineering.setAccessible(true);unpaired.setAccessible(true);Method builtinCrop=TEAnimatedScreenSelector.class.getDeclaredMethod("unpairedInput",String.class);builtinCrop.setAccessible(true);JsonObject result=new JsonObject();JsonArray materials=new JsonArray(),inputs=new JsonArray();int screens=0,engineeringCount=0,unpairedCount=0;
  for(int choice=0;choice<ScreenHousingTextures.IDS.length;choice++){boolean screen="Screens".equals(ScreenHousingTextures.category(choice)),eng=(boolean)engineering.invoke(null,choice),input=(boolean)unpaired.invoke(null,choice);int flags=(screen?1:0)|(eng?2:0)|(input?4:0);JsonObject row=new JsonObject();row.addProperty("choice",choice);row.addProperty("flags",flags);materials.add(row);if(screen)screens++;if(eng)engineeringCount++;if(input)unpairedCount++;}
  for(String id:TileEntityAnimatedScreenSelector.INPUT_PANELS){JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("canonical",ScreenHousingTextures.canonicalInput(id));row.addProperty("frames",TileEntityAnimatedScreenSelector.getInputFrameCount(id));row.addProperty("crop",(boolean)builtinCrop.invoke(null,ScreenHousingTextures.canonicalInput(id))?1D/32:0);row.addProperty("stationary",RenderIdentifierCache.input(id,false).toString());row.addProperty("off",RenderIdentifierCache.input(id,true).toString());inputs.add(row);}
  result.add("materials",materials);result.add("inputs",inputs);Path file=Paths.get(args[0]);Files.createDirectories(file.toAbsolutePath().getParent());byte[] bytes=result.toString().getBytes("UTF-8");Files.write(file,bytes);StringBuilder hash=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",value&255));if(materials.size()!=893||screens!=389||engineeringCount!=0||unpairedCount!=4||inputs.size()!=190||!hash.toString().equals("ae2a739bf966012667f2252c48327944608dfb39a00435179c347cf8dfade0a7"))throw new AssertionError("Source Screen media predicates changed "+hash);System.out.println("Actual Screen media predicates PASS materials="+materials.size()+" screens="+screens+" engineering="+engineeringCount+" unpaired="+unpairedCount+" inputs="+inputs.size()+" sha256="+hash);
 }
}
