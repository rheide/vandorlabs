package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.appliances.NativeApplianceBlock;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.maps.MapId;
import java.util.function.Consumer;

/** Only map identities escape private arm snapshots, when a viewer begins tracking the root chunk. */
public final class NativeAppliancePayloadMaps {
 public static void visit(NativeGearEntity arm,Consumer<MapId> visitor){
  if(arm.inert()||!arm.controller())return;var payload=arm.payload();if(payload==null||payload.materialized())return;
  for(var cell:payload.cells()){
   if(!(cell.state.getBlock() instanceof NativeApplianceBlock appliance)||!appliance.scanner())continue;
   var raw=cell.tile();if(raw==null||!raw.contains("ApplianceFrame",Tag.TAG_COMPOUND))continue;
   var frame=raw.getCompound("ApplianceFrame");if(!frame.contains("id",Tag.TAG_STRING)||!frame.getString("id").equals("minecraft:filled_map")||!frame.contains("components",Tag.TAG_COMPOUND))continue;
   var components=frame.getCompound("components");if(components.contains("minecraft:map_id",Tag.TAG_INT)){int id=components.getInt("minecraft:map_id");if(id>=0)visitor.accept(new MapId(id));}
  }
 }
 private NativeAppliancePayloadMaps(){}
}
