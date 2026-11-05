package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.fluid.*;
import com.vandorlabs.neoforge.shape.ProgrammableShapeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
@EventBusSubscriber(modid="vandorlabs")
public final class NativeFluidBindingChecks {
 private static void require(boolean ok,String why){if(!ok)throw new AssertionError("Native fluid callbacks: "+why);}
 @SubscribeEvent public static void started(ServerStartedEvent event){if(!System.getProperty("vandorlabs.transmitterPhase","").equals("fluid-bind"))return;var level=event.getServer().overworld();BlockPos pos=new BlockPos(0,80,0);level.getChunkAt(pos);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
  level.setBlock(pos,Blocks.WATER.defaultBlockState(),3);var replaced=net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(),level,pos);var slab=VandorLabsBootstrap.PROGRAMMABLE_SLAB.get();level.setBlock(pos,slab.defaultBlockState(),3);var host=(ProgrammableShapeEntity)level.getBlockEntity(pos);require(!host.fluidVolumeSnapshot().hasSide(0),"fixture no longer starts with deferred empty volume");NativeFluidWorld.placed(new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(replaced,Blocks.STONE.defaultBlockState(),null));require(host.containedFluids().get(0).is(Fluids.WATER)&&host.containedFluids().get(0).getAmount()==1000,"replaced water captured before first deferred tick");
  level.removeBlock(pos,false);level.setBlock(pos,slab.defaultBlockState(),3);host=(ProgrammableShapeEntity)level.getBlockEntity(pos);require(!host.fluidVolumeSnapshot().hasSide(0),"fresh callback starts deferred");require(!((LiquidBlockContainer)slab).canPlaceLiquid(null,level,pos,level.getBlockState(pos),Fluids.WATER)&&!((LiquidBlockContainer)slab).placeLiquid(level,pos,level.getBlockState(pos),Fluids.WATER.defaultFluidState()),"natural source flow must not fill later");
  require(((BucketItem)Items.WATER_BUCKET).emptyContents(null,level,pos,null,new ItemStack(Items.WATER_BUCKET)),"actual NeoForge BucketItem/dispenser callback before first tick");require(host.containedFluids().get(0).is(Fluids.WATER)&&!NativeBucketContext.permits(level,pos),"actual wrapped callback stores bucket and cleans context");
  require(((BucketPickup)slab).pickupBlock(null,level,pos,level.getBlockState(pos)).is(Items.WATER_BUCKET)&&!host.containedFluids().occupied(0),"real pickup callback drains one bucket");
  var root=new CompoundTag();var slots=new CompoundTag();var unknown=new CompoundTag();unknown.putString("FluidName","missing:opaque");unknown.putInt("Amount",1000);unknown.putString("ForeignField","unchanged");slots.put("Side0",unknown);root.put("ContainedFluids",slots);host.containedFluids().read(root,level.registryAccess());boolean opaqueResult=((BucketItem)Items.WATER_BUCKET).emptyContents(null,level,pos,null,new ItemStack(Items.WATER_BUCKET));require(!opaqueResult&&level.getBlockEntity(pos)==host&&host.containedFluids().raw(0).equals(unknown),"opaque occupied slot protected by actual callback result="+opaqueResult+" state="+level.getBlockState(pos)+" sameHost="+(level.getBlockEntity(pos)==host)+" raw="+host.containedFluids().raw(0));
  require(java.util.Arrays.stream(BucketItem.class.getDeclaredMethods()).anyMatch(method->method.getName().contains("explicitBucket")),"actual BucketItem mixin did not bind");
  System.out.println("[vandorlabs][fluid-bind] dedicated PASS actual registered initial-placement, immediate BucketItem, no natural flow, pickup, opaque data; mixin class binding");event.getServer().halt(false);
 }
}
