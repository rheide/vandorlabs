package com.vandorlabs.neoforge.glass;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.nbt.*;

/** Actual ItemBlock generic merge + dedicated outer-settings callback replay. */
public final class GlassItemPolicyChecks {
 public static void main(String[] args)throws Exception {int profiles=0,rejected=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();String raw=row.get("outer").getAsString();CompoundTag outer=raw.isEmpty()?null:TagParser.parseTag(raw),base=TagParser.parseTag(row.get("base").getAsString());boolean fails=!row.get("failure").getAsString().isEmpty();try{var result=NativeGlassPlacementPolicy.place(NativeGlassSettings.read(base),outer,row.get("size").getAsInt(),row.get("depth").getAsInt(),(row.get("facing").getAsInt()+2)%4);if(fails)throw new AssertionError("Expected invalid layout refusal");if(result.stateSize()!=row.get("placedSize").getAsInt())throw new AssertionError("State size "+profiles);CompoundTag actual=new CompoundTag(),expected=new CompoundTag();result.settings().write(actual);NativeGlassSettings.read(TagParser.parseTag(row.get("saved").getAsString())).write(expected);if(!actual.equals(expected))throw new AssertionError("Item settings "+profiles+" actual="+actual+" expected="+expected);}catch(IllegalArgumentException invalid){if(!fails)throw invalid;rejected++;}profiles++;}}
  if(profiles!=3744||rejected!=144)throw new AssertionError("Source item coverage");System.out.println("[vandorlabs][flat-glass-item-policy] PASS profiles="+profiles+" safe-invalid-layout-refusals="+rejected);
 }
}
