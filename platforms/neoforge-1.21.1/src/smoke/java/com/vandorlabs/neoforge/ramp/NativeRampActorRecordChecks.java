package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Actual native entity writers, independent of region-store publication. */
final class NativeRampActorRecordChecks {
 static void run(ServerLevel level){int checked=0;for(Entity entity:List.of(new Pig(EntityType.PIG,level),new ItemEntity(level,0,0,0,new ItemStack(Items.STONE,3)))){
  entity.setUUID(new UUID(83,checked));entity.setPos(12.5,100,13.5);entity.setDeltaMovement(.125,0,-.125);entity.fallDistance=3.5F;entity.setOnGround(false);var from=entity.position();var actor=new NativeRampActorIntent.Participant(entity.getUUID(),net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),from,from.add(0,-1,0),entity.getBoundingBox(),entity.getDeltaMovement(),entity.fallDistance,entity.onGround(),entity.noPhysics,entity.hasImpulse,entity.hurtMarked);var before=new CompoundTag();need(entity.save(before),"actual native original writer");need(NativeRampActorRecord.classify(before,actor).state()==NativeRampActorRecord.State.ORIGINAL,"actual original physical metadata");NativeRampRiders.move(entity,0,-1,0,null);var after=new CompoundTag();need(entity.save(after),"actual native transferred writer");var opaque=new CompoundTag();opaque.putUUID("CredentialOwner",new UUID(8,9));opaque.putByteArray("OpaqueInventory",new byte[65536]);after.put("UnrelatedOpaquePayload",opaque);need(NativeRampActorRecord.classify(after,actor).state()==NativeRampActorRecord.State.TRANSFERRED,"actual transferred physical metadata");var returned=NativeRampActorRecord.returned(after,actor);need(NativeRampActorRecord.classify(returned,actor).state()==NativeRampActorRecord.State.ORIGINAL,"returned record is idempotently original");var unchanged=after.copy();unchanged.remove("Pos");var other=returned.copy();other.remove("Pos");need(other.equals(unchanged),"only position changes; actual item and all opaque fields preserved");
  for(int variant=0;variant<7;variant++){var broken=after.copy();switch(variant){case 0->broken.putUUID("UUID",new UUID(9,9));case 1->broken.putString("id","minecraft:player");case 2->broken.getList("Pos",Tag.TAG_DOUBLE).set(0,DoubleTag.valueOf(12.625));case 3->broken.getList("Motion",Tag.TAG_DOUBLE).set(0,DoubleTag.valueOf(.25));case 4->broken.putDouble("FallDistance",0);case 5->broken.putByte("OnGround",(byte)2);default->{var wrong=new ListTag();wrong.add(DoubleTag.valueOf(Double.NaN));broken.put("Pos",wrong);}}need(NativeRampActorRecord.classify(broken,actor).state()==NativeRampActorRecord.State.REFUSED,"changed/malformed record refuses "+variant);}
  entity.setPos(15.75,100,13.5);entity.setDeltaMovement(.125,0,-.125);entity.fallDistance=3.5F;entity.setOnGround(false);var crossing=new NativeRampActorIntent.Participant(entity.getUUID(),actor.type(),entity.position(),entity.position().add(.5,0,0),entity.getBoundingBox(),entity.getDeltaMovement(),entity.fallDistance,entity.onGround(),entity.noPhysics,entity.hasImpulse,entity.hurtMarked);NativeRampRiders.move(entity,.5,0,0,null);var crossed=new CompoundTag();need(entity.save(crossed)&&NativeRampActorRecord.classify(crossed,crossing).state()==NativeRampActorRecord.State.TRANSFERRED,"actual cross-chunk physical record can be classified");boolean crossRefused=false;try{NativeRampActorRecord.returned(crossed,crossing);}catch(IllegalArgumentException expected){crossRefused=true;}need(crossRefused,"cross-chunk ownership cannot use same-record position rewrite");
  if(entity instanceof Pig){var baby=after.copy();baby.putInt("Age",-1);need(NativeRampActorRecord.classify(baby,actor).state()==NativeRampActorRecord.State.REFUSED,"unproven changed dimensions refuse");}checked++;
 }need(checked==2,"both actual native record types");System.out.println("[vandorlabs][ramp-actor-record] actual writers PASS");}
 private static void need(boolean value,String message){if(!value)throw new AssertionError("Ramp actor record: "+message);}
}
