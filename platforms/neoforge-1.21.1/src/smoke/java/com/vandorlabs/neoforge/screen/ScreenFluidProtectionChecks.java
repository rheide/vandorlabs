package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Real bucket and flowing-fluid paths must preserve canonical configured Screen ownership. */
@EventBusSubscriber(modid="vandorlabs")
public final class ScreenFluidProtectionChecks {
 private static final BlockPos BASE=new BlockPos(12640,100,12640);private static int checks;
 private static String phase(){return System.getProperty("vandorlabs.transmitterPhase","");}
 private static void need(boolean ok,String reason){checks++;if(!ok)throw new AssertionError("Screen fluid: "+reason);}
 private static NativeScreenEntity install(ServerLevel level,BlockPos pos,NativeScreenBlock block){
  level.setBlock(pos,block.defaultBlockState(),18);NativeScreenEntity tile=(NativeScreenEntity)level.getBlockEntity(pos);
  CompoundTag tag=tile.saveWithoutMetadata(level.registryAccess()),reader=new CompoundTag(),secret=new CompoundTag();
  var chip=NativeCredentialItems.dataChip(2);secret.putString("FluidOwnershipSecret","sole-owner");chip.set(DataComponents.CUSTOM_DATA,CustomData.of(secret));
  reader.put("InsertedToken",NativeCredentialItems.writeStack(chip,level.registryAccess()));tag.put("ScreenChipReader",reader);tile.loadWithComponents(tag,level.registryAccess());need(!tile.inert(),"supported configured chip");return tile;
 }
 private static CompoundTag physical(NativeScreenEntity tile,ServerLevel level){return tile.saveWithoutMetadata(level.registryAccess()).getCompound("ScreenChipReader");}
 private static void run(ServerLevel level,boolean read){
  var failures=new java.util.ArrayList<String>();
  for(var family:ScreenFamily.values())for(boolean lava:new boolean[]{false,true}){
   var block=NativeScreenRegistrations.BLOCKS.get(family).get();var pos=BASE.east((family.ordinal()*2+(lava?1:0))*8);level.getChunkAt(pos);
   if(read){need(level.getBlockEntity(pos) instanceof NativeScreenEntity,"saved host "+family);var tile=(NativeScreenEntity)level.getBlockEntity(pos);var chip=NativeCredentialItems.readStack(physical(tile,level).getCompound("InsertedToken"),level.registryAccess());need(!tile.inert()&&chip.is(NativeCredentialItems.DATA_CHIP.get())&&chip.getCount()==1&&chip.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("FluidOwnershipSecret").equals("sole-owner"),"one private chip after restart "+family);continue;}
   level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),18);var tile=install(level,pos,block);var before=physical(tile,level).copy();
   var fluid=lava?Fluids.LAVA:Fluids.WATER;boolean replaceable=level.getBlockState(pos).canBeReplaced(fluid);
   boolean bucket=((BucketItem)(lava?Items.LAVA_BUCKET:Items.WATER_BUCKET)).emptyContents(null,level,pos,null);
   boolean bucketKept=level.getBlockEntity(pos)==tile&&physical(tile,level).equals(before);
   // Reset the isolated negative scene before independently exercising actual flow.
   if(!bucketKept)tile=install(level,pos,block);before=physical(tile,level).copy();
   var source=pos.above();for(var side:Direction.Plane.HORIZONTAL)level.setBlock(source.relative(side),Blocks.STONE.defaultBlockState(),18);
   level.setBlock(source,(lava?Blocks.LAVA:Blocks.WATER).defaultBlockState(),18);level.getFluidState(source).tick(level,source);
   boolean flowed=level.getBlockEntity(pos)!=tile;System.out.println("Screen fluid boundary role="+family+" lava="+lava+" replacement="+replaceable+" bucket="+bucket+" flowed="+flowed);
   if(replaceable||bucket||!bucketKept||flowed||!physical(tile,level).equals(before)||!level.getFluidState(pos).isEmpty())failures.add("ownership "+family+" lava="+lava);
   if(!level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(3)).isEmpty())failures.add("unexpected dropped owner "+family+" lava="+lava);
   for(var state:block.getStateDefinition().getPossibleStates()){checks++;if(!state.blocksMotion()||state.canBeReplaced(fluid)){failures.add("logical material "+family+" lava="+lava);break;}}
   level.removeBlock(source,false);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
  }
  need(failures.isEmpty(),"fluid matrix "+failures);
  System.out.println("[vandorlabs][screen-fluid-protection] "+(read?"read":"write")+" PASS checks="+checks+" canonical seven-role water/lava replacement/buckets/flow/chip ownership");
 }
 @SubscribeEvent public static void started(ServerStartedEvent event){if(!phase().startsWith("screen-fluid-protection-"))return;try{run(event.getServer().overworld(),phase().endsWith("read"));event.getServer().saveEverything(true,true,true);}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][screen-fluid-protection] FAIL "+failure);}finally{event.getServer().halt(false);}}
}
