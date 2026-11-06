package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.tiles.ScreenHousingTextures;
import java.io.*;

/** Actual released lit/unlit material aliases, including legacy atlas entries. */
public final class LightArtworkLegacyOracle {
 public static void main(String[] args)throws Exception {
  net.minecraft.init.Bootstrap.register();JsonArray aliases=new JsonArray();
  for(int i=0;i<ScreenHousingTextures.BUILTIN_COUNT;i++){
   int choice=ScreenHousingTextures.choiceAt(i);String lit=ScreenHousingTextures.texture(choice,true),off=ScreenHousingTextures.texture(choice,false);if(lit.equals(off))continue;int found=-1;
   for(int j=0;j<ScreenHousingTextures.BUILTIN_COUNT;j++)if(ScreenHousingTextures.texture(ScreenHousingTextures.choiceAt(j),true).equals(off)){found=j;break;}
   if(found<0)throw new AssertionError("Native catalog cannot represent released Off alias "+choice+" "+off);
   JsonObject row=new JsonObject();row.addProperty("on",ScreenHousingTextures.IDS[i]);row.addProperty("off",ScreenHousingTextures.IDS[found]);row.addProperty("choice",choice);row.addProperty("offChoice",ScreenHousingTextures.choiceAt(found));aliases.add(row);
  }
  File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();try(Writer out=new OutputStreamWriter(new FileOutputStream(file),"UTF-8")){out.write(new Gson().toJson(aliases));out.write('\n');}
  SegmentedReferenceGolden.check(file,"a295eb060a9611aedc81092a26ca9e165dfc4e5698b352d4bdeea7f2d51da429");
  System.out.println("Actual Light lit/unlit aliases PASS rows="+aliases.size());
 }
}
