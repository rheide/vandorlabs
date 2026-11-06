package com.vandorlabs.neoforge.light;

import com.google.gson.*;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.persistence.LightData;
import java.nio.file.*;
import java.util.*;

/** Source-runtime aliases, including the legacy Blue Hex and explicit Off override precedence. */
public final class NativeLightArtworkChecks {
 public static void main(String[] args)throws Exception {
  var rows=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();var off=new HashMap<Integer,Integer>();int checks=0;
  try(var stream=NativeLightArtwork.class.getResourceAsStream("/assets/vandorlabs/data/light_artwork.json")){var installed=JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));if(!installed.equals(rows))throw new AssertionError("Installed Light aliases differ from actual Source");}
  for(var element:rows){var row=element.getAsJsonObject();int from=row.get("choice").getAsInt(),to=row.get("offChoice").getAsInt();if(!ModernFinishCatalog.ENTRIES.get(from).id().equals(row.get("on").getAsString())||!ModernFinishCatalog.ENTRIES.get(to).id().equals(row.get("off").getAsString()))throw new AssertionError("Source/native catalog identity");off.put(from,to);checks++;}
  for(int style=0;style<6;style++)for(int face=-1;face<ModernFinishCatalog.ENTRIES.size();face++)for(int explicit:new int[]{-1,0,78,256})for(boolean lit:new boolean[]{false,true}){var data=new LightData(style,face,explicit,15,false,0,0,true,false,0,false);int selected=face<0?78+style:face,expected=!lit&&explicit>=0?explicit:lit?selected:off.getOrDefault(selected,selected);if(NativeLightArtwork.face(data,lit)!=expected)throw new AssertionError("Source Light Off selection precedence");checks++;}
  if(rows.size()!=25)throw new AssertionError("Actual alias coverage");System.out.println("[vandorlabs][light-artwork] CPU PASS checks="+checks+" actual25aliases/allcatalogchoices/default6styles/explicitOff/preference");
 }
}
