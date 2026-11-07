package com.vandorlabs.neoforge.ramp;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.PistonEvent;

/** Refuse the complete vanilla move before it publishes targets for held source cells. */
public final class NativeRampHeldPhysics {
 public static boolean blocked(PistonEvent.Pre event){
  if(!(event.getLevel() instanceof Level level)||!NativeRampHeldOwners.active(level))return false;
  var base=event.getPos();var facing=event.getDirection();
  // The base and head are mutated even by an extension with no pushed blocks.
  if(NativeRampHeldOwners.blocked(level,base)||NativeRampHeldOwners.blocked(level,base.relative(facing)))return true;
  var resolver=event.getStructureHelper();
  if(resolver==null||!resolver.resolve())return false;
  var movement=event.getPistonMoveType().isExtend?facing:facing.getOpposite();
  for(var pos:resolver.getToPush())if(NativeRampHeldOwners.blocked(level,pos)||NativeRampHeldOwners.blocked(level,pos.relative(movement)))return true;
  for(var pos:resolver.getToDestroy())if(NativeRampHeldOwners.blocked(level,pos))return true;
  return false;
 }
 private NativeRampHeldPhysics(){}
}
