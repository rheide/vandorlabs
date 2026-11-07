package com.vandorlabs.client;
import com.google.gson.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.util.EnumFacing;
/** Invokes the released renderer's private GL matrix builder without drawing or extracting it first. */
public final class ProgrammerJournalMatrixLegacyChecks {
 public static void main(String[] args)throws Exception{
  java.lang.reflect.Method initialize=Class.forName("com.vandorlabs.client.PropulsionArtworkLegacyOracle").getDeclaredMethod("initialize");initialize.setAccessible(true);initialize.invoke(null);
  java.lang.reflect.Method method=Class.forName("com.vandorlabs.client.ProgrammerJournalRenderer").getDeclaredMethod("matrix",EnumFacing.class);method.setAccessible(true);JsonArray cases=new JsonArray();
  for(EnumFacing facing:EnumFacing.HORIZONTALS){FloatBuffer matrix=(FloatBuffer)method.invoke(null,facing);JsonObject row=new JsonObject();row.addProperty("facing",facing.name());JsonArray values=new JsonArray();for(int i=0;i<16;i++)values.add(Float.floatToRawIntBits(matrix.get(i)));row.add("matrix",values);cases.add(row);}
  String payload=new Gson().toJson(cases);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",b&255));if(cases.size()!=4||!hash.toString().equals("c20e4807514ddb65c844b44ccdaacddc0d60c83c00f30c9e87883f658292ec7b"))throw new AssertionError("Released Programmer journal matrix changed: "+hash);JsonObject result=new JsonObject();result.addProperty("golden",hash.toString());result.add("cases",cases);Path path=Paths.get(System.getProperty("vandorlabs.programmerJournalMatrixReference","build/test-artifacts/programmer-journal-matrix-forge.json"));Files.createDirectories(path.toAbsolutePath().getParent());Files.write(path,new Gson().toJson(result).getBytes(StandardCharsets.UTF_8));System.out.println("Actual Programmer journal matrices PASS cases="+cases.size()+" golden="+hash);
 }
}
