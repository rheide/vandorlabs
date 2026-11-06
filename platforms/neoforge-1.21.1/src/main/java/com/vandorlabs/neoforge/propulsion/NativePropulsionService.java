package com.vandorlabs.neoforge.propulsion;

import com.vandorlabs.propulsion.PropulsionEmission;
import com.vandorlabs.render.ConnectedSquare;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Owning-thread prepared engine partitions. Published render reads never inspect the world. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativePropulsionService {
 private static final Map<Level,Set<NativePropulsionEntity>> PENDING=new IdentityHashMap<>();
 private static final Set<Level> CLOSED=Collections.newSetFromMap(new WeakHashMap<>()),APPLYING=Collections.newSetFromMap(new IdentityHashMap<>());
 private NativePropulsionService(){}
 private static boolean open(Level world){return world!=null&&!CLOSED.contains(world)&&(!(world instanceof ServerLevel server)||NativeRedstoneChannels.open(server));}
 static boolean loaded(Level world,BlockPos p){return world!=null&&!world.isOutsideBuildHeight(p)&&world.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=null;}
 public static boolean installed(NativePropulsionEntity owner){var world=owner.getLevel();return open(world)&&loaded(world,owner.getBlockPos())&&world.getBlockEntity(owner.getBlockPos())==owner&&world.getBlockState(owner.getBlockPos()).getBlock() instanceof NativePropulsionBlock;}
 public static boolean hasPending(Level world){return PENDING.containsKey(world);}
 public static void queue(NativePropulsionEntity owner){var world=owner.getLevel();if(open(world))PENDING.computeIfAbsent(world,k->Collections.newSetFromMap(new IdentityHashMap<>())).add(owner);}
 public static void queue(Level world,BlockPos pos){if(open(world)&&loaded(world,pos)&&world.getBlockEntity(pos) instanceof NativePropulsionEntity owner)queue(owner);}
 static void retire(NativePropulsionEntity owner){if(NativePropulsionShape.mutating(owner.getLevel(),owner.getBlockPos()))return;queue(owner);if(owner.getLevel() instanceof ServerLevel world&&open(world)&&loaded(world,owner.getBlockPos())){var light=world.getAuxLightManager(owner.getBlockPos());if(light!=null)light.removeLightAt(owner.getBlockPos());}}
 public static java.util.List<NativePropulsionEntity> members(NativePropulsionEntity owner){if(!installed(owner)||!owner.available())return java.util.List.of();var v=owner.visual();var part=v.part();int face=v.facing().get3DDataValue(),right=PropulsionEmission.right(face),up=PropulsionEmission.up(face);var result=new java.util.ArrayList<NativePropulsionEntity>();for(int x=0;x<part.size();x++)for(int y=0;y<part.size();y++){var pos=owner.getBlockPos().offset(PropulsionEmission.dx(right)*(x-part.x())+PropulsionEmission.dx(up)*(y-part.y()),PropulsionEmission.dy(right)*(x-part.x())+PropulsionEmission.dy(up)*(y-part.y()),PropulsionEmission.dz(right)*(x-part.x())+PropulsionEmission.dz(up)*(y-part.y()));if(!loaded(owner.getLevel(),pos)||!(owner.getLevel().getBlockEntity(pos) instanceof NativePropulsionEntity peer)||!peer.available())return java.util.List.of();result.add(peer);}return java.util.List.copyOf(result);}
 static boolean manual(NativePropulsionEntity owner){refresh(owner);var peers=members(owner);if(peers.isEmpty())return false;for(var peer:peers)if(peer.settings().channels().first()>0)return true;int mode=(peers.getFirst().settings().data().manualMode()+1)%3;for(var peer:peers)peer.manualMode(mode);for(var peer:peers)queue(peer);flush(owner.getLevel());return true;}
 static void changedChannel(NativePropulsionEntity owner){queue(owner);if(com.vandorlabs.neoforge.redstone.NativeSignalUpdateBatch.isActive())com.vandorlabs.neoforge.redstone.NativeSignalUpdateBatch.afterSignals(owner,()->flush(owner.getLevel()));else flush(owner.getLevel());}
 public static void refresh(NativePropulsionEntity owner){queue(owner);flush(owner.getLevel());}
 public static void refreshProjection(NativePropulsionEntity owner){queue(owner);flush(owner.getLevel(),false);}
 public static void flush(Level world){flush(world,true);}
 private static void flush(Level world,boolean physics){if(!open(world)||!APPLYING.add(world))return;try{
  if(physics&&world instanceof ServerLevel server)NativeRedstoneChannels.flush(server);var seeds=PENDING.remove(world);if(seeds==null)return;
  Set<NativePropulsionEntity> owners=new LinkedHashSet<>();
  for(var seed:seeds){var state=seed.getBlockState();if(!(state.getBlock() instanceof NativePropulsionBlock block))continue;int max=block.definition().maxConnected,face=state.getValue(NativePropulsionBlock.FACING).get3DDataValue(),right=PropulsionEmission.right(face),up=PropulsionEmission.up(face);for(int x=-max;x<=max;x++)for(int y=-max;y<=max;y++){var pos=seed.getBlockPos().offset(PropulsionEmission.dx(right)*x+PropulsionEmission.dx(up)*y,PropulsionEmission.dy(right)*x+PropulsionEmission.dy(up)*y,PropulsionEmission.dz(right)*x+PropulsionEmission.dz(up)*y);if(loaded(world,pos)&&world.getBlockState(pos).getBlock()==block&&world.getBlockEntity(pos) instanceof NativePropulsionEntity owner&&!owner.isRemoved())owners.add(owner);}}
  Map<NativePropulsionEntity,NativePropulsionEntity.Visual> before=new IdentityHashMap<>(),staged=new IdentityHashMap<>();for(var owner:owners)if(installed(owner)){before.put(owner,owner.visual());staged.put(owner,physics?owner.stage():owner.stageCurrent());}
  for(var owner:before.keySet()){var v=staged.get(owner);int max=v.definition().maxConnected;if(max==0||!v.join())continue;int face=v.facing().get3DDataValue(),right=PropulsionEmission.right(face),up=PropulsionEmission.up(face);boolean[] cold={false};int mode=v.powered()?v.selected()?2:1:0;
   var part=ConnectedSquare.findPartitioned(max,(x,y)->{var pos=owner.getBlockPos().offset(PropulsionEmission.dx(right)*x+PropulsionEmission.dx(up)*y,PropulsionEmission.dy(right)*x+PropulsionEmission.dy(up)*y,PropulsionEmission.dz(right)*x+PropulsionEmission.dz(up)*y);if(!loaded(world,pos)){cold[0]=true;return false;}if(world.getBlockState(pos).getBlock()!=owner.getBlockState().getBlock()||!(world.getBlockEntity(pos) instanceof NativePropulsionEntity peer)||!peer.available())return false;var p=staged.getOrDefault(peer,peer.visual());return p.join()&&p.facing()==v.facing()&&(p.powered()?p.selected()?2:1:0)==mode;});if(cold[0])part=ConnectedSquare.Part.SINGLE;var p=part.isSingle()?NativePropulsionEntity.Part.SINGLE:new NativePropulsionEntity.Part(part.size,part.x,part.y);staged.put(owner,new NativePropulsionEntity.Visual(v.definition(),v.facing(),v.powered(),v.brightness(),v.sideTexture(),v.particleLevel(),v.selected(),v.join(),p,v.inert()));
  }
  for(var entry:staged.entrySet())entry.getKey().publish(entry.getValue());
  for(var entry:before.entrySet())if((entry.getKey().needsNotification()||!entry.getValue().equals(entry.getKey().visual()))&&installed(entry.getKey()))entry.getKey().notifyPublication();
 }finally{APPLYING.remove(world);}}
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(PENDING.containsKey(event.getLevel()))flush(event.getLevel());}
 @SubscribeEvent public static void load(LevelEvent.Load event){if(event.getLevel() instanceof Level world)CLOSED.remove(world);}
 @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof Level world){CLOSED.add(world);PENDING.remove(world);APPLYING.remove(world);}}
}
