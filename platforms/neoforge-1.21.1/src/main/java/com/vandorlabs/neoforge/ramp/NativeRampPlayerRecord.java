package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;

/** Authenticated player position repair. Inventory, platform envelopes and every unrelated field
 * remain opaque: a repair copies the existing record and changes only its position list. */
final class NativeRampPlayerRecord {
 enum State {UNCHANGED,ORIGINAL,TRANSFERRED,REFUSED}
 record Result(State state,String reason){}
 static Result classify(Optional<CompoundTag> current,Optional<CompoundTag> before,
                        NativeRampActorIntent.Participant actor,String dimension){
  if(!actor.type().equals("minecraft:player"))return refused("Player receipt has another actor role");
  if(current.isEmpty())return before.isEmpty()?new Result(State.UNCHANGED,""):refused("Player file disappeared");
  var row=current.get();
  try{
   identity(row,actor.id(),dimension);
   if(!row.contains("playerGameType",Tag.TAG_INT)||row.getInt("playerGameType")<0||row.getInt("playerGameType")>2
       ||!row.contains("abilities",Tag.TAG_COMPOUND)||!row.getCompound("abilities").contains("flying",Tag.TAG_BYTE)
       ||row.getCompound("abilities").getBoolean("flying")||row.contains("RootVehicle")||row.contains("SleepingX")
       ||row.contains("SleepingY")||row.contains("SleepingZ")||!NativeRampActorRecord.unscaled(row))return refused("Player physical role changed");
   var pos=vector(row,"Pos");var velocity=vector(row,"Motion");
   if(!row.contains("FallDistance",Tag.TAG_FLOAT)||!Float.isFinite(row.getFloat("FallDistance"))
       ||!row.contains("OnGround",Tag.TAG_BYTE)||(row.getByte("OnGround")!=0&&row.getByte("OnGround")!=1))return refused("Player physical flags unavailable");
   if(before.isPresent()&&row.equals(before.get()))return new Result(State.UNCHANGED,"");
   if(!velocity.equals(actor.velocity())&&!velocity.equals(new Vec3(actor.velocity().x,0,actor.velocity().z)))return refused("Player velocity changed");
   boolean returned=row.getFloat("FallDistance")==0&&row.getBoolean("OnGround");
   if(pos.equals(actor.from())&&(returned||row.getFloat("FallDistance")==actor.fall()&&row.getBoolean("OnGround")==actor.ground()))return new Result(State.ORIGINAL,"");
   if(pos.equals(actor.to())&&returned)return new Result(State.TRANSFERRED,"");
   return refused("Player position is outside its operation receipt");
  }catch(RuntimeException failure){return refused("Player physical record is malformed");}
 }
 static CompoundTag returned(CompoundTag row,Optional<CompoundTag> before,NativeRampActorIntent.Participant actor,String dimension){
  if(classify(Optional.of(row),before,actor,dimension).state()!=State.TRANSFERRED)throw new IllegalArgumentException("Player has no authenticated transferred position");
  var result=row.copy();var position=new ListTag();position.add(DoubleTag.valueOf(actor.from().x));position.add(DoubleTag.valueOf(actor.from().y));position.add(DoubleTag.valueOf(actor.from().z));result.put("Pos",position);return result;
 }
 static void identity(CompoundTag row,UUID id,String dimension){if(!row.hasUUID("UUID")||!row.getUUID("UUID").equals(id)||!row.contains("Dimension",Tag.TAG_STRING)||!row.getString("Dimension").equals(dimension))throw new IllegalArgumentException("Player UUID or dimension differs from operation");}
 private static Vec3 vector(CompoundTag row,String key){if(!row.contains(key,Tag.TAG_LIST))throw new IllegalArgumentException("Player physical vector missing");var values=row.getList(key,Tag.TAG_DOUBLE);if(values.size()!=3||((ListTag)row.get(key)).size()!=3)throw new IllegalArgumentException("Player physical vector shape");for(int i=0;i<3;i++)if(!Double.isFinite(values.getDouble(i))||Math.abs(values.getDouble(i))>30000128)throw new IllegalArgumentException("Player physical vector bounds");return new Vec3(values.getDouble(0),values.getDouble(1),values.getDouble(2));}
 private static Result refused(String reason){return new Result(State.REFUSED,reason);}
 private NativeRampPlayerRecord(){}
}
