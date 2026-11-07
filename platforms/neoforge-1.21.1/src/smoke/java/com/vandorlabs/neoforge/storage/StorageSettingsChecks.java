package com.vandorlabs.neoforge.storage;

import com.google.gson.*;
import com.vandorlabs.persistence.HousingSettings;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;

/** Actual Source typed defaults and real ItemBlock merge results; no host or inventory installation. */
public final class StorageSettingsChecks {
 private static int checks;private static final String[] KEYS={HousingSettings.MAIN,HousingSettings.VERSION,HousingSettings.SIDE,HousingSettings.LAYOUT,HousingSettings.TILE_SIDES,HousingSettings.FACES_ENABLED,HousingSettings.FACES};
 private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Native Storage settings: "+why);}
 private static void compare(NativeStorageSettings actual,CompoundTag expected,String label){var tag=new CompoundTag();actual.write(tag);for(String key:KEYS)need(Objects.equals(tag.get(key),expected.get(key)),label+" key="+key+" expected="+expected.get(key)+" actual="+tag.get(key));}
 public static void main(String[] args)throws Exception{JsonObject source;try(var stream=new GZIPInputStream(Files.newInputStream(Path.of(args[0])));var reader=new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)){source=JsonParser.parseReader(reader).getAsJsonObject();}compare(NativeStorageSettings.fresh(),TagParser.parseTag(source.get("factory").getAsString()),"factory");int snapshots=0,placements=0;
  for(var value:source.getAsJsonArray("snapshots")){var row=value.getAsJsonObject();var raw=TagParser.parseTag(row.get("input").getAsString());var before=raw.copy();var saved=new NativeStorageSettings.Saved(raw);compare(saved.settings,TagParser.parseTag(row.get("saved").getAsString()),"snapshot "+snapshots);need(raw.equals(before)&&saved.unchanged().equals(before),"defensive original retention");var privateRoot=before.copy();privateRoot.putString("FutureStorage","retain");privateRoot.put("neoforge:opaque",new CompoundTag());var original=new NativeStorageSettings.Saved(privateRoot);var edited=original.edited(NativeStorageSettings.fresh());for(String key:privateRoot.getAllKeys())if(!List.of(KEYS).contains(key))need(Objects.equals(privateRoot.get(key),edited.get(key)),"appearance edit preserves private field "+key);var leaked=original.unchanged();leaked.remove("FutureStorage");need(original.unchanged().equals(privateRoot),"no mutable retained tag escape");snapshots++;}
  for(var value:source.getAsJsonArray("placement")){var row=value.getAsJsonObject();compare(NativeStorageSettings.item(TagParser.parseTag(row.get("input").getAsString())),TagParser.parseTag(row.get("saved").getAsString()),"placement "+placements);placements++;}
  need(snapshots==39&&placements==192,"released coverage");need(NativeStorageSettings.fresh().housing().mainTexture==295&&NativeStorageSettings.read(new CompoundTag()).housing().mainTexture==294,"fresh and legacy empty decode remain distinct");System.out.println("Native Storage settings PASS snapshots="+snapshots+" placements="+placements+" checks="+checks+" typed defaults/real item merge/private inventory preservation");
 }
}
