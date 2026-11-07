package com.vandorlabs.neoforge.copifier;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** One bounded, settings-only operation per player. Physical inventories are never restored. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCopifierUndo {
 private static final long PLAYER_BYTES=16L*1024*1024,TOTAL_BYTES=64L*1024*1024;
 private static long nextOrder;
 private static final class Entry {final Block block;final CompoundTag before;CompoundTag after;Entry(Block block,CompoundTag before){this.block=block;this.before=before.copy();}}
 private static final class Operation {final ServerLevel world;final Map<BlockPos,Entry> entries=new LinkedHashMap<>();final long order=nextOrder++;long bytes;boolean complete=true;Operation(ServerLevel world){this.world=world;}}
 private static final Map<ServerPlayer,Operation> history=new WeakHashMap<>(),pending=new WeakHashMap<>();
 private static long bytes(Tag tag){
  if(tag instanceof CompoundTag compound){long size=128;for(String key:compound.getAllKeys())size+=112L+2L*key.length()+bytes(compound.get(key));return size;}
  if(tag instanceof ListTag list){long size=80L+8L*list.size();for(Tag value:list)size+=bytes(value);return size;}
  if(tag instanceof StringTag text)return 64L+2L*text.getAsString().length();
  if(tag instanceof IntArrayTag array)return 40L+4L*array.getAsIntArray().length;
  if(tag instanceof ByteArrayTag array)return 40L+array.getAsByteArray().length;
  return tag.getId()==Tag.TAG_LONG_ARRAY?PLAYER_BYTES+1:32;
 }
 private static long totalBytes(){long total=0;for(var operation:history.values())total+=operation.bytes;for(var operation:pending.values())total+=operation.bytes;return total;}
 private static void discard(ServerPlayer player,Operation operation){operation.entries.clear();operation.bytes=0;operation.complete=false;history.remove(player);}
 private static boolean reserve(ServerPlayer player,Operation operation,long amount){
  if(!operation.complete)return false;var previous=history.get(player);if(previous!=null&&previous.bytes+operation.bytes+amount>PLAYER_BYTES)history.remove(player);
  if(amount>PLAYER_BYTES-operation.bytes){discard(player,operation);return false;}
  while(totalBytes()+amount>TOTAL_BYTES){ServerPlayer oldest=null;long order=Long.MAX_VALUE;for(var entry:history.entrySet())if(entry.getValue().order<order){oldest=entry.getKey();order=entry.getValue().order;}if(oldest==null){discard(player,operation);return false;}history.remove(oldest);}
  operation.bytes+=amount;return true;
 }
 private static CompoundTag capture(ServerLevel world,BlockPos pos){
  if(!world.hasChunkAt(pos)||!(world.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.ProgrammableBlockEntity tile)||tile.isRemoved()||tile.getLevel()!=world||tile.getBlockState()!=world.getBlockState(pos))return null;
  var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(tile.getBlockState().getBlock());if(!id.getNamespace().equals("vandorlabs"))return null;
  try{return NativeCopifierHousingCapture.undo(NativeCopifierHousingCapture.Family.of(id.getPath()),tile.saveWithoutMetadata(world.registryAccess()));}catch(IllegalArgumentException unsupported){return null;}
 }
 public static void begin(ServerPlayer player,ServerLevel world){if(world.getServer().isSameThread()&&player.level()==world)pending.put(player,new Operation(world));}
 static void before(ServerPlayer player,ServerLevel world,BlockPos pos){
  var operation=pending.get(player);if(operation==null||!operation.complete||operation.world!=world||operation.entries.containsKey(pos)||operation.entries.size()>=com.vandorlabs.items.ConnectedCopySelection.MAX_CELLS)return;
  var settings=capture(world,pos);if(settings!=null&&reserve(player,operation,160+bytes(settings)))operation.entries.put(pos.immutable(),new Entry(world.getBlockState(pos).getBlock(),settings));
 }
 public static void finish(ServerPlayer player){
  if(!player.getServer().isSameThread())return;var operation=pending.get(player);if(operation==null)return;
  if(!operation.complete){pending.remove(player);player.displayClientMessage(net.minecraft.network.chat.Component.literal("Duplifier applied settings; undo unavailable because the memory limit was exceeded"),true);return;}
  var iterator=operation.entries.entrySet().iterator();while(iterator.hasNext()){var value=iterator.next();value.getValue().after=capture(operation.world,value.getKey());if(value.getValue().after==null||value.getValue().before.equals(value.getValue().after)){operation.bytes-=160+bytes(value.getValue().before);iterator.remove();}else if(!reserve(player,operation,bytes(value.getValue().after))){finish(player);return;}}
  pending.remove(player);if(!operation.entries.isEmpty())history.put(player,operation);
 }
 public static boolean undo(ServerPlayer player){
  if(!player.getServer().isSameThread()||!player.isAlive()||player.isSpectator())return false;var operation=history.get(player);if(operation==null||operation.world!=player.level())return false;
  for(var value:operation.entries.entrySet()){
   var target=NativeCopifierHousingWorld.target(operation.world,value.getKey(),player,Direction.UP,false);
   if(target==null||target.tile().getBlockState().getBlock()!=value.getValue().block||!value.getValue().after.equals(capture(operation.world,value.getKey())))return false;
  }
  var iterator=operation.entries.entrySet().iterator();while(iterator.hasNext()){var value=iterator.next();var target=NativeCopifierHousingWorld.target(operation.world,value.getKey(),player,Direction.UP,false);if(target==null||!NativeCopifierHousingWorld.apply(operation.world,value.getKey(),target,value.getValue().before,player))return false;iterator.remove();}
  history.remove(player);return true;
 }
 @SubscribeEvent public static void unloaded(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(!(event.getLevel() instanceof ServerLevel world)||!world.getServer().isSameThread())return;history.values().removeIf(operation->operation.world==event.getLevel());pending.values().removeIf(operation->operation.world==event.getLevel());}
 @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player&&player.getServer().isSameThread()){history.remove(player);pending.remove(player);}}
 private NativeCopifierUndo(){}
}
