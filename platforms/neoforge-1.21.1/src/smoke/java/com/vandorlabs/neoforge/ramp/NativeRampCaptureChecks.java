package com.vandorlabs.neoforge.ramp;
import com.google.gson.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
/** Placed-world source selection against the accepted Source capture subset; no motion/journal claim. */
final class NativeRampCaptureChecks {
 static void run(ServerLevel world,NativeRampBlock full,NativeRampSlabBlock slab,Path reference)throws Exception{
  var root=new BlockPos(22832,100,22832);for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){var p=root.offset(x,0,z);world.getChunkAt(p);for(int y=-3;y<=4;y++)world.setBlock(p.offset(0,y,0),Blocks.AIR.defaultBlockState(),18);}
  var actor=new net.neoforged.neoforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,5715),"RampCapture"));actor.setPos(root.getX(),root.getY(),root.getZ());int rows=0,accepted=0,checks=0;var previous=new ArrayList<BlockPos>();
  try(var lines=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(reference))))){String line;while((line=lines.readLine())!=null){for(var pos:previous)world.setBlock(pos,Blocks.AIR.defaultBlockState(),18);previous.clear();var row=JsonParser.parseString(line).getAsJsonObject();var block=row.get("block").getAsString().endsWith("_slab")?slab:full;world.setBlock(root,Blocks.AIR.defaultBlockState(),18);world.setBlock(root,block.legacyState(row.get("meta").getAsInt()),18);var owner=(NativeRampEntity)world.getBlockEntity(root);int mode=row.get("mode").getAsInt();var settings=new CompoundTag();settings.putInt("ControllerVersion",2);settings.putInt("StartOffsetHalfSteps",0);settings.putInt("EndOffsetHalfSteps",-4);settings.putInt("TreadPixels",8);settings.putInt("SpeedMode",0);settings.putBoolean("Elevator",mode==1);settings.putInt("TravelAxis",mode==2?2:0);settings.putBoolean("ExtendSegments",mode==3);settings.putBoolean("MoatMode",mode==4);settings.putBoolean("RampVerticalLayout",mode==5);owner.loadWithComponents(settings,world.registryAccess());
   int material=row.get("material").getAsInt();var source=material==0?Blocks.STONE.defaultBlockState():material==3?Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH):Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,material==1?SlabType.BOTTOM:SlabType.TOP);
   var expected=new HashSet<BlockPos>();for(String key:List.of("seed","second")){var a=row.getAsJsonArray(key);var pos=root.offset(a.get(0).getAsInt()-4,a.get(1).getAsInt()-80,a.get(2).getAsInt()-4);world.setBlock(pos,source,18);previous.add(pos);expected.add(pos);}
   var result=NativeRampCapture.select(owner,actor);need(result.accepted(),"selection case="+rows+" error="+result.error());checks++;
   var found=new HashSet<BlockPos>();for(var selected:result.plan().sources())found.add(selected.position());need(!found.isEmpty()&&expected.containsAll(found),"selected only installed source cells case="+rows);checks++;
   if(row.get("accepted").getAsBoolean()){var saved=TagParser.parseTag(row.getAsJsonArray("states").get(0).getAsJsonObject().get("saved").getAsString());var sourcePositions=new HashSet<BlockPos>();for(var element:saved.getList("Sources",10)){var sourceTag=(CompoundTag)element;sourcePositions.add(root.offset(sourceTag.getInt("X")-4,sourceTag.getInt("Y")-80,sourceTag.getInt("Z")-4));}need(found.equals(sourcePositions),"exact accepted Source selection case="+rows);checks++;need(result.plan().length()==saved.getInt("Length")&&result.plan().low()==saved.getDouble("Low")&&result.plan().high()==saved.getDouble("High"),"captured Source scalars case="+rows);checks++;accepted++;}rows++;
  }}
  for(var pos:previous)world.setBlock(pos,Blocks.AIR.defaultBlockState(),18);world.setBlock(root,full.defaultBlockState(),18);var owner=(NativeRampEntity)world.getBlockEntity(root);var seed=root.relative(owner.scanFacing());for(var source:List.of(Blocks.SAND,Blocks.TNT)){world.setBlock(seed,source.defaultBlockState(),18);need(!NativeRampCapture.select(owner,actor).accepted()&&world.getBlockState(seed).is(source),"dynamic source refusal before mutation");checks++;}world.setBlock(seed,Blocks.AIR.defaultBlockState(),18);world.setBlock(root,Blocks.AIR.defaultBlockState(),18);
  var coldRoot=new BlockPos(288000,100,288000);world.getChunkAt(coldRoot);world.setBlock(coldRoot,full.defaultBlockState().setValue(NativeRampBlock.FACING,Direction.NORTH).setValue(NativeRampBlock.ACTIVE_FACE,Direction.NORTH),18);var coldOwner=(NativeRampEntity)world.getBlockEntity(coldRoot);var coldSeed=coldRoot.north();need(world.getChunkSource().getChunkNow(coldSeed.getX()>>4,coldSeed.getZ()>>4)==null,"cold capture precondition");need(!NativeRampCapture.select(coldOwner,actor).accepted()&&world.getChunkSource().getChunkNow(coldSeed.getX()>>4,coldSeed.getZ()>>4)==null,"capture refuses cold seed without loading");need(!java.util.concurrent.CompletableFuture.supplyAsync(()->NativeRampCapture.select(coldOwner,actor)).join().accepted(),"worker capture refused before world queries");world.setBlock(coldRoot,Blocks.AIR.defaultBlockState(),18);checks+=3;need(rows==768&&accepted==612,"Source capture coverage");System.out.println("[vandorlabs][ramp-capture] PASS rows="+rows+" acceptedSource="+accepted+" checks="+checks+" selection only");
 }
 private static void need(boolean ok,String message){if(!ok)throw new AssertionError("Ramp capture: "+message);}
}
