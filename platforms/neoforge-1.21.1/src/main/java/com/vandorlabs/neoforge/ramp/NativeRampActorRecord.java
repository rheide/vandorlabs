package com.vandorlabs.neoforge.ramp;

import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/** Conservative persisted physical-state classification; never decodes an entity or its inventory. */
final class NativeRampActorRecord {
 enum State {ORIGINAL,TRANSFERRED,REFUSED}
 record Result(State state,String reason){}
 private static final Result ORIGINAL=new Result(State.ORIGINAL,""),TRANSFERRED=new Result(State.TRANSFERRED,"");
 static Result classify(CompoundTag row,NativeRampActorIntent.Participant actor){
  try{
   if(!row.hasUUID("UUID")||!row.getUUID("UUID").equals(actor.id())||!row.contains("id",Tag.TAG_STRING)||!row.getString("id").equals(actor.type()))return refused("Actor UUID/type differs from intent");
   var type=actor.type().equals("minecraft:pig")?EntityType.PIG:actor.type().equals("minecraft:item")?EntityType.ITEM:null;
   if(type==null||actor.noPhysics())return refused("Actor physical role is not proven for initial recovery");
   if(type==EntityType.PIG&&(!row.contains("Age",Tag.TAG_INT)||row.getInt("Age")<0))return refused("Only adult Pig physical dimensions are proven");
   for(String key:new String[]{"NeoForgeData","neoforge:attachments","ForgeCaps","ForgeData"})if(row.contains(key)&&(!row.contains(key,Tag.TAG_COMPOUND)||!row.getCompound(key).isEmpty()))return refused("Unproven actor platform envelope");
   if(row.contains("Passengers")&&(!row.contains("Passengers",Tag.TAG_LIST)||!((ListTag)row.get("Passengers")).isEmpty()))return refused("Actor passengers require their own recovery protocol");
   if(type==EntityType.PIG&&row.contains("Saddle")&&(!row.contains("Saddle",Tag.TAG_BYTE)||row.getByte("Saddle")!=0))return refused("Saddled actor mount requires player recovery support");
   if(type==EntityType.PIG&&!unscaled(row))return refused("Actor persisted scale is changed or unproven");
   var expected=type.getDimensions().makeBoundingBox(actor.from().x,actor.from().y,actor.from().z);if(!expected.equals(actor.body()))return refused("Actor dimensions differ from proven native role");
   var position=vector(row,"Pos");var motion=vector(row,"Motion");if(!motion.equals(actor.velocity())||Math.abs(motion.x)>10||Math.abs(motion.y)>10||Math.abs(motion.z)>10)return refused("Actor persisted velocity changed or is not codec-stable");
   if(!row.contains("FallDistance",Tag.TAG_FLOAT)||!Float.isFinite(row.getFloat("FallDistance"))||!row.contains("OnGround",Tag.TAG_BYTE)||(row.getByte("OnGround")!=0&&row.getByte("OnGround")!=1))return refused("Actor persisted physical flags are unavailable");
   float fall=row.getFloat("FallDistance");boolean ground=row.getBoolean("OnGround");boolean returned=fall==0&&ground;
   if(position.equals(actor.from())&&(returned||fall==actor.fall()&&ground==actor.ground()))return ORIGINAL;
   if(position.equals(actor.to())&&returned)return TRANSFERRED;
   return refused("Actor persisted position or physical flags changed");
  }catch(RuntimeException failure){return refused("Actor physical record is malformed");}
 }
 /** Only the position field changes, inside the same existing entity record; unknown data stays opaque. */
 static CompoundTag returned(CompoundTag row,NativeRampActorIntent.Participant actor){if(classify(row,actor).state()!=State.TRANSFERRED)throw new IllegalArgumentException("Actor record has no proven transferred position");if((net.minecraft.util.Mth.floor(actor.from().x)>>4)!=(net.minecraft.util.Mth.floor(actor.to().x)>>4)||(net.minecraft.util.Mth.floor(actor.from().z)>>4)!=(net.minecraft.util.Mth.floor(actor.to().z)>>4))throw new IllegalArgumentException("Cross-chunk actor record ownership requires a separate durable handoff");var copy=row.copy();var position=new ListTag();position.add(DoubleTag.valueOf(actor.from().x));position.add(DoubleTag.valueOf(actor.from().y));position.add(DoubleTag.valueOf(actor.from().z));copy.put("Pos",position);return copy;}
 private static boolean unscaled(CompoundTag row){if(!row.contains("attributes"))return true;if(!row.contains("attributes",Tag.TAG_LIST))return false;var attributes=row.getList("attributes",Tag.TAG_COMPOUND);if(attributes.size()!=((ListTag)row.get("attributes")).size()||attributes.size()>1024)return false;String scale=net.minecraft.world.entity.ai.attributes.Attributes.SCALE.unwrapKey().orElseThrow().location().toString();boolean found=false;for(var element:attributes){var attribute=(CompoundTag)element;if(!attribute.contains("id",Tag.TAG_STRING))return false;if(!attribute.getString("id").equals(scale))continue;if(found||!attribute.contains("base",Tag.TAG_DOUBLE)||attribute.getDouble("base")!=1)return false;found=true;if(attribute.contains("modifiers")&&(!attribute.contains("modifiers",Tag.TAG_LIST)||!((ListTag)attribute.get("modifiers")).isEmpty()))return false;}return true;}
 private static Vec3 vector(CompoundTag row,String key){if(!row.contains(key,Tag.TAG_LIST))throw new IllegalArgumentException("Missing actor vector");var values=row.getList(key,Tag.TAG_DOUBLE);if(values.size()!=3||((ListTag)row.get(key)).size()!=3)throw new IllegalArgumentException("Actor vector shape");for(int i=0;i<3;i++)if(!Double.isFinite(values.getDouble(i)))throw new IllegalArgumentException("Actor vector finite bounds");return new Vec3(values.getDouble(0),values.getDouble(1),values.getDouble(2));}
 private static Result refused(String reason){return new Result(State.REFUSED,reason);}
 private NativeRampActorRecord(){}
}
