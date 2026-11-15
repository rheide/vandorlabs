package com.vandorlabs.neoforge.ramp;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;

/** Short exact-operation fence, acquired only immediately before physical ownership publication.
 * Preparation never installs a hold. Connection tick, teleport acknowledgement and logout remain live. */
public final class NativeRampPlayerHold {
 private record Hold(UUID operation,WeakReference<ServerPlayer> player,WeakReference<ServerLevel> level,Vec3 position){}
 private static final Map<UUID,Hold> HELD=new HashMap<>();
 private static final Map<ServerPlayer,Integer> HOOKS=new WeakHashMap<>();
 private static long movementBlocks,physicsBlocks;
 public static long[] diagnostics(){return new long[]{movementBlocks,physicsBlocks,HELD.size()};}
 public static void observedMovementHook(ServerPlayer player){observe(player,1);if(blocked(player))movementBlocks++;}
 public static void observedPhysicsHook(ServerPlayer player){observe(player,2);if(blocked(player))physicsBlocks++;}
 private static void observe(ServerPlayer player,int bit){if(player!=null&&player.getServer()!=null&&player.getServer().isSameThread()){int previous=HOOKS.getOrDefault(player,0);if((previous&bit)==0)HOOKS.put(player,previous|bit);}}
 static boolean available(ServerPlayer player){thread(player);return HOOKS.getOrDefault(player,0)==3&&player.connection!=null;}
 static void acquire(UUID operation,Collection<ServerPlayer> players){
  for(var player:players){thread(player);if(!available(player))throw new IllegalStateException("Player movement/physics handoff is not installed");var before=HELD.get(player.getUUID());if(before!=null&&(!before.operation.equals(operation)||before.player.get()!=player))throw new IllegalStateException("Player belongs to another Ramp commit");}
  for(var player:players)HELD.put(player.getUUID(),new Hold(operation,new WeakReference<>(player),new WeakReference<>(player.serverLevel()),player.position()));
 }
 /** Server-thread query; an injected network-thread HEAD must fall through to vanilla's enqueue. */
 public static boolean blocked(ServerPlayer player){
  if(HELD.isEmpty()||player==null||player.getServer()==null||!player.getServer().isSameThread())return false;
  var held=HELD.get(player.getUUID());return held!=null&&held.player.get()==player&&held.level.get()==player.serverLevel();
 }
 static void release(UUID operation){var iterator=HELD.entrySet().iterator();while(iterator.hasNext()){var hold=iterator.next().getValue();if(!hold.operation.equals(operation))continue;iterator.remove();var player=hold.player.get();if(player!=null&&player.connection!=null&&!player.isRemoved()&&hold.level.get()==player.serverLevel())try{player.connection.teleport(player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());}catch(RuntimeException failure){com.mojang.logging.LogUtils.getLogger().warn("Ramp player endpoint correction could not be delivered to {}",player.getUUID(),failure);}}}
 static void unload(ServerLevel level){HELD.entrySet().removeIf(entry->entry.getValue().level.get()==null||entry.getValue().level.get()==level);HOOKS.keySet().removeIf(player->player.level()==level);}
 private static void thread(ServerPlayer player){if(player.getServer()==null||!player.getServer().isSameThread())throw new IllegalStateException("Ramp player fence requires server thread");}
 private NativeRampPlayerHold(){}
}
