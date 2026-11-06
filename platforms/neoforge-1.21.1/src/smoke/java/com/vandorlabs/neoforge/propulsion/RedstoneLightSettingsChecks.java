package com.vandorlabs.neoforge.propulsion;

import com.google.gson.*;
import com.vandorlabs.persistence.RedstoneLightData;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Actual source snapshots before/after local initialization and the released authoritative callback. */
public final class RedstoneLightSettingsChecks {
 private static int checks;
 private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 private static void snapshot(NativeRedstoneLightSettings settings,JsonObject expected,int physical,boolean settled,String label)throws Exception {
  var d=settings.data();need(d.channel==expected.get("channel").getAsInt(),label+" primary");int[] channels=new int[expected.getAsJsonArray("channels").size()];for(int i=0;i<channels.length;i++)channels[i]=expected.getAsJsonArray("channels").get(i).getAsInt();need(Arrays.equals(settings.channels().toArray(),channels),label+" channels");need(d.channelLevel==expected.get("channelLevel").getAsInt()&&d.channelSignal==expected.get("channelSignal").getAsBoolean(),label+" independent saved level/signal");need(d.inverted==expected.get("inverted").getAsBoolean()&&d.analog==expected.get("analog").getAsBoolean(),label+" analog flags");need(d.activation(physical)==expected.get("activation").getAsInt()&&d.brightness(physical)==expected.get("brightness").getAsInt(),label+" activation/brightness");need(d.particleLevel==expected.get("particleLevel").getAsInt()&&d.particleThreshold==expected.get("threshold").getAsInt()&&d.particles(physical)==expected.get("particles").getAsBoolean(),label+" intensity/threshold/selection");need(d.manualMode()==expected.get("manualMode").getAsInt()&&d.join==expected.get("join").getAsBoolean()&&d.sideTexture==expected.get("side").getAsInt(),label+" manual/join/housing");boolean powered=expected.get("powered").getAsBoolean();need((powered&&d.particles(physical))==expected.get("renderParticles").getAsBoolean()&&(powered?d.brightness(physical):0)==expected.get("emitted").getAsInt(),label+" render and emission gating");if(settled)need(d.powered(physical)==powered,label+" actual power callback");
  var actual=new CompoundTag();settings.write(actual);var saved=TagParser.parseTag(expected.get("saved").getAsString());for(String key:new String[]{"id","x","y","z"})saved.remove(key);need(actual.equals(saved),label+" typed saved tree expected="+saved+" actual="+actual);
 }
 public static void main(String[] args)throws Exception {
  int cases=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();var source=TagParser.parseTag(row.get("input").getAsString());var settings=NativeRedstoneLightSettings.read(source);int physical=row.get("physical").getAsInt();String label="case="+cases;snapshot(settings,row.getAsJsonObject("read"),physical,false,label+" read");var loaded=settings.capture(row.get("statePowered").getAsBoolean());need(row.get("loadStateReads").getAsInt()==(settings.data().initialized?0:1),label+" local-only initial capture");snapshot(loaded,row.getAsJsonObject("loaded"),physical,false,label+" loaded");snapshot(loaded,row.getAsJsonObject("settled"),physical,true,label+" settled");need(loaded.capture(!row.get("statePowered").getAsBoolean())==loaded,label+" repeat load retains captured state");cases++;}}
  var original=new CompoundTag();var opaque=new CompoundTag();opaque.putString("UnknownOwner","retain");original.put("ForgeCaps",opaque);original.putString("FutureData","opaque");var saved=new NativeRedstoneLightSettings.Saved(original);original.getCompound("ForgeCaps").putString("UnknownOwner","mutated");need(saved.unchanged().getCompound("ForgeCaps").getString("UnknownOwner").equals("retain"),"retained scalar wrapper owns defensive original");var edited=saved.edited(saved.settings.capture(true));need(edited.getCompound("ForgeCaps").getString("UnknownOwner").equals("retain")&&edited.getString("FutureData").equals("opaque"),"known edits keep opaque namespaces");
  need(cases==16384,"actual source corpus coverage");System.out.println("[vandorlabs][redstone-light-settings] CPU PASS cases="+cases+" snapshots="+(cases*3)+" checks="+checks+" exact Source fields/defaults/local capture/power/particles/emission/retained opaque edits; inactive adapter");
 }
}
