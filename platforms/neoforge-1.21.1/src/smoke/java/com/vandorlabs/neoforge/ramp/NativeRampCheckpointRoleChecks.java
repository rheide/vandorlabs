package com.vandorlabs.neoforge.ramp;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** The live initial-actor admission must be no broader than its cold native-record classifier. */
public final class NativeRampCheckpointRoleChecks {
 private static NativeRampActorIntent.Participant participant(Entity entity){return new NativeRampActorIntent.Participant(entity.getUUID(),net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),entity.position(),entity.position().add(0,-.5,0),entity.getBoundingBox(),entity.getDeltaMovement(),entity.fallDistance,entity.onGround(),entity.noPhysics,entity.hasImpulse,entity.hurtMarked);}
 public static void run(ServerLevel level){var pig=new Pig(EntityType.PIG,level);pig.setPos(8,120,8);pig.setOnGround(true);pig.setDeltaMovement(Vec3.ZERO);NativeRampRiderPlan.requireInitialRecord(pig,participant(pig));var item=new ItemEntity(level,8,120,8,new ItemStack(Items.EMERALD,5));item.setDeltaMovement(Vec3.ZERO);NativeRampRiderPlan.requireInitialRecord(item,participant(item));var cow=new Cow(EntityType.COW,level);cow.setPos(8,120,8);cow.setDeltaMovement(Vec3.ZERO);refused(cow);pig.getPersistentData().putString("UnprovenOpaqueOwner","retained");var before=pig.getPersistentData().copy();refused(pig);if(!pig.getPersistentData().equals(before))throw new AssertionError("Ramp refused actor data changed");System.out.println("[vandorlabs][ramp-checkpoint-roles] PASS validPigItem/unprovenCowOpaqueRefusedBeforeIntent/originalDataRetained");}
 private static void refused(Entity entity){try{NativeRampRiderPlan.requireInitialRecord(entity,participant(entity));}catch(IllegalArgumentException expected){return;}throw new AssertionError("Unsupported Ramp initial actor admitted: "+entity.getType());}
 private NativeRampCheckpointRoleChecks(){}
}
