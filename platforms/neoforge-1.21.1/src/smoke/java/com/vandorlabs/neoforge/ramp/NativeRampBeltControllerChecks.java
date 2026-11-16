package com.vandorlabs.neoforge.ramp;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual scheduled deployed belt, sleeping hysteresis and exact native return. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampBeltControllerChecks {
 private static final BlockPos ROOT=new BlockPos(24560,100,24560),A=ROOT.north(),B=ROOT.north(2);
 private static NativeRampBlock rootBlock;private static NativeRampPartBlock partBlock;
 private static BlockEntityType<NativeRampEntity> roots;private static BlockEntityType<NativeRampPartEntity> parts;
 private static ServerLevel world;private static ServerPlayer actor;private static NativeRampEntity owner;
 private static final Map<BlockPos,CompoundTag> originals=new LinkedHashMap<>();
 private static int stage,ticks,checks,since;private static double distance;
 private static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("ramp-belt-controller");}
 private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message+" stage="+stage+" ticks="+ticks+" runtime="+(owner==null?"none":owner.controller().status()));}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){
            if(!enabled())return;
            event.register(Registries.BLOCK,h->{rootBlock=new NativeRampBlock(BlockBehaviour.Properties.of(),()->roots,()->partBlock.defaultBlockState());partBlock=new NativeRampPartBlock(BlockBehaviour.Properties.of(),()->parts);h.register(ResourceLocation.parse("vandorlabs:ramp_belt_controller_check"),rootBlock);h.register(ResourceLocation.parse("vandorlabs:ramp_belt_controller_check_part"),partBlock);});
            event.register(Registries.BLOCK_ENTITY_TYPE,h->{roots=BlockEntityType.Builder.<NativeRampEntity>of((p,s)->new NativeRampEntity(roots,p,s,false),rootBlock).build(null);parts=BlockEntityType.Builder.<NativeRampPartEntity>of((p,s)->new NativeRampPartEntity(parts,p,s),partBlock).build(null);h.register(ResourceLocation.parse("vandorlabs:ramp_belt_controller_check"),roots);h.register(ResourceLocation.parse("vandorlabs:ramp_belt_controller_check_part"),parts);});
        }
    }

 @SubscribeEvent public static void start(ServerStartedEvent event){
  if(!enabled())return;world=event.getServer().overworld();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var pos=ROOT.offset(x*16,0,z*16);world.getChunkAt(pos);world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);}
  for(var pos:BlockPos.betweenClosed(ROOT.offset(-4,-6,-6),ROOT.offset(4,8,3)))world.setBlock(pos,Blocks.AIR.defaultBlockState(),18);
  world.setBlock(ROOT.below(),Blocks.STONE.defaultBlockState(),18);world.setBlock(ROOT,rootBlock.defaultBlockState(),18);owner=(NativeRampEntity)world.getBlockEntity(ROOT);
  actor=new net.neoforged.neoforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(3,7724),"BeltController"));actor.setPos(ROOT.getX()+.5,ROOT.getY()+5,ROOT.getZ()+.5);actor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);world.addNewPlayer(actor);
  for(var pos:List.of(A,B)){world.setBlock(pos,com.vandorlabs.neoforge.VandorLabsBootstrap.PROGRAMMABLE_SLAB.get().defaultBlockState(),18);var source=world.getBlockEntity(pos);source.getPersistentData().putString("BeltControllerOriginal","preserve");originals.put(pos,source.saveWithFullMetadata(world.registryAccess()));}
 }
 @SubscribeEvent public static void tick(ServerTickEvent.Post event){
  if(!enabled()||world==null)return;
  try{ticks++;need(ticks<1400,"belt scheduler timeout");var runtime=owner.controller();
   if(stage==0&&ticks>4){var next=new NativeRampSettings(0,4,8,false,false,false,0,true,0,1,false,false,0,false,0,com.vandorlabs.redstone.ChannelList.EMPTY);var result=runtime.configure(actor,next);need(result.admitted()&&!result.activationRefused(),"ordinary configuration admits escalator");stage=1;return;}
   if(stage==1){need(!runtime.status().error(),"deployment accepted frozen belt controls");if(runtime.committedPose()!=1||runtime.status().moving())return;since=ticks;distance=runtime.committedBeltDistance();stage=2;return;}
   if(stage==2){need(!runtime.status().error(),"scheduled belt transport");if(ticks-since<25)return;need(runtime.committedBeltDistance()!=distance,"deployed belt advances under ordinary scheduled block ticks");actor.setPos(ROOT.getX()+38,ROOT.getY(),ROOT.getZ());runtime.refreshActivity();distance=runtime.committedBeltDistance();since=ticks;stage=3;return;}
   if(stage==3){need(!runtime.status().error(),"hysteresis belt valid");if(ticks-since<25)return;need(runtime.committedBeltDistance()!=distance,"running belt remains active at38blocks");actor.setPos(ROOT.getX()+45,ROOT.getY(),ROOT.getZ());runtime.refreshActivity();since=ticks;stage=4;return;}
   if(stage==4){need(!runtime.status().error(),"sleep is not an activation error");if(ticks-since<5)return;distance=runtime.committedBeltDistance();since=ticks;stage=5;return;}
   if(stage==5){need(!runtime.status().error(),"sleep remains healthy");if(ticks-since<30)return;need(runtime.committedBeltDistance()==distance,"sleep processes no elapsed-time belt catchup");actor.setPos(ROOT.getX()+38,ROOT.getY(),ROOT.getZ());runtime.refreshActivity();since=ticks;stage=6;return;}
   if(stage==6){if(ticks-since<25)return;need(runtime.committedBeltDistance()==distance,"sleeping belt does not restart outside32blocks");actor.setPos(ROOT.getX()+.5,ROOT.getY()+5,ROOT.getZ()+.5);runtime.refreshActivity();since=ticks;stage=7;return;}
   if(stage==7){need(!runtime.status().error(),"wake accepted");if(ticks-since<5)return;need(runtime.committedBeltDistance()!=distance,"nearby player wakes belt");need(runtime.requestPose(0).admitted(),"deployed belt original return admitted");stage=8;return;}
   if(stage==8){need(!runtime.status().error(),"deployed belt reset uses real rider state");if(runtime.phase()!=NativeRampController.Phase.IDLE)return;for(var pos:List.of(A,B))need(world.getBlockEntity(pos)!=null&&NativeRampJournal.sameSnapshot(originals.get(pos),world.getBlockEntity(pos).saveWithFullMetadata(world.registryAccess())),"exact native source returned");need(NativeRampJournal.atRoot(world,ROOT)==null,"belt return retires durable original owner");stage=9;System.out.println("[vandorlabs][ramp-belt-controller] PASS checks="+checks+" actualScheduled/deployment/hysteresis/sleep/wake/originalReturn");world.getServer().halt(false);}
  }catch(Throwable failure){failure.printStackTrace();System.err.println("[vandorlabs][ramp-belt-controller] FAIL "+failure);world.getServer().halt(false);throw failure;}
 }
 private NativeRampBeltControllerChecks(){}
}
