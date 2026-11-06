package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.segmented.SegmentedDoorTile;
import com.vandorlabs.tiles.ScreenHousingTextures;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
/** Released material acceptance uses raw categories, including every authored Door and Frame variant. */
public final class SegmentedMaterialsLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();java.util.Set<Integer> choices=new java.util.TreeSet<>();for(int i=0;i<ScreenHousingTextures.IDS.length;i++){choices.add(i);choices.add(ScreenHousingTextures.choiceAt(i));}for(int i:new int[]{Integer.MIN_VALUE,-2,-1,0x1fffffff,0x20000000,0x3fffffff,0x40000000,Integer.MAX_VALUE})choices.add(i);
  JsonArray rows=new JsonArray();int accepted=0;for(int id:choices){JsonObject row=new JsonObject();row.addProperty("choice",id);boolean valid=SegmentedDoorTile.validTexture(id);row.addProperty("valid",valid);if(valid)accepted++;rows.add(row);}String text=rows.toString();Path output=Paths.get(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,text.getBytes(StandardCharsets.UTF_8));SegmentedReferenceGolden.check(output.toFile(),"6a47ec100785a0167996c920374eacae6ab17783e5770cc2f135e5a691cb4cd0");System.out.println("Actual Segmented material acceptance PASS choices="+rows.size()+" accepted="+accepted);
 }
}
