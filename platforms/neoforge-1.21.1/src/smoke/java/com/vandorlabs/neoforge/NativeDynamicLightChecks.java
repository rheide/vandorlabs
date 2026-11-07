package com.vandorlabs.neoforge;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual chunk source discovery, independent of the caller's gameplay publication of auxiliary light. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeDynamicLightChecks {
 @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{
  if(!System.getProperty("vandorlabs.transmitterPhase","").equals("dynamic-light"))return;var level=event.getServer().overworld();List<String> omitted=new ArrayList<>();int blocks=0,states=0;
  for(var entry:BuiltInRegistries.BLOCK.entrySet()){
   if(!entry.getKey().location().getNamespace().equals("vandorlabs"))continue;var block=entry.getValue();var method=block.getClass().getMethod("getLightEmission",BlockState.class,BlockGetter.class,BlockPos.class);if(!method.getDeclaringClass().getName().startsWith("com.vandorlabs."))continue;
   var pos=new BlockPos(1024+blocks*32,128,2048);var chunk=level.getChunkAt(pos);var state=block.defaultBlockState();level.setBlock(pos,state,2);if(!level.getBlockState(pos).is(block))throw new AssertionError("Dynamic light fixture did not install "+entry.getKey().location());var manager=level.getAuxLightManager(pos);if(manager==null)throw new AssertionError("Loaded chunk lacks auxiliary light manager");
   try{manager.setLightAt(pos,15);if(state.getLightEmission(level,pos)!=15)throw new AssertionError("Contextual emission missing "+entry.getKey().location());boolean[] found={false};chunk.findBlockLightSources((at,value)->{if(at.equals(pos))found[0]=true;});if(!found[0])omitted.add(entry.getKey().location().toString());manager.setLightAt(pos,0);found[0]=false;chunk.findBlockLightSources((at,value)->{if(at.equals(pos))found[0]=true;});if(found[0]||state.getLightEmission(level,pos)!=0)throw new AssertionError("Withdrawn dynamic emitter remains a chunk source");for(var variant:block.getStateDefinition().getPossibleStates()){if(variant.hasDynamicLightEmission())states++;else if(omitted.isEmpty())throw new AssertionError("Missing state flag "+entry.getKey().location());}}finally{manager.setLightAt(pos,0);level.removeBlock(pos,false);}blocks++;
  }
  if(blocks<15)throw new AssertionError("Dynamic emitter registry coverage "+blocks);if(!omitted.isEmpty())throw new AssertionError("Chunk source discovery omitted "+omitted.size()+" contextual emitters: "+omitted.subList(0,Math.min(3,omitted.size())));System.out.println("[vandorlabs][dynamic-light] dedicated PASS blocks="+blocks+" states="+states+" actual ChunkAccess discovery/withdrawal with loaded auxiliary manager; gameplay publication checked separately");event.getServer().halt(false);
 }
}
