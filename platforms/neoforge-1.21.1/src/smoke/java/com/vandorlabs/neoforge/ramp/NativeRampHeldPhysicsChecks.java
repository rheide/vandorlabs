package com.vandorlabs.neoforge.ramp;

import com.mojang.serialization.MapCodec;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real vanilla piston publication while an asynchronous Ramp ARM record is pending. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampHeldPhysicsChecks {
 private static final BlockPos ROOT=new BlockPos(24100,100,24100), PUSH=ROOT.east(4), BASE=ROOT.south(4), CONTROL=ROOT.west(4);
 private static Fixture rootBlock,proxyBlock;private static BlockEntityType<NativeRampEntity> rootType;private static BlockEntityType<NativeRampJournalPart> proxyType;
 private static ServerLevel level;private static ServerPlayer actor;private static NativeRampJournal.Operation operation;private static CountDownLatch gate;private static int phase,ticks,checks;
 private static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").startsWith("ramp-held-physics-");}
 private static void need(boolean ok,String message){checks++;if(!ok)throw new AssertionError("Ramp held physics: "+message);}
 private static class Fixture extends BaseEntityBlock {final boolean proxy;Fixture(boolean p){super(BlockBehaviour.Properties.of().strength(1));proxy=p;}protected MapCodec<Fixture> codec(){return MapCodec.unit(this);}public BlockEntity newBlockEntity(BlockPos p,BlockState s){return proxy?new NativeRampJournalPart(proxyType,p,s):new NativeRampEntity(rootType,p,s,false);}}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static class Registration {
  @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;event.register(Registries.BLOCK,h->{rootBlock=new Fixture(false);proxyBlock=new Fixture(true);h.register(ResourceLocation.parse("vandorlabs:ramp_physics_root"),rootBlock);h.register(ResourceLocation.parse("vandorlabs:ramp_physics_proxy"),proxyBlock);});event.register(Registries.BLOCK_ENTITY_TYPE,h->{rootType=BlockEntityType.Builder.<NativeRampEntity>of((p,s)->new NativeRampEntity(rootType,p,s,false),rootBlock).build(null);proxyType=BlockEntityType.Builder.<NativeRampJournalPart>of((p,s)->new NativeRampJournalPart(proxyType,p,s),proxyBlock).build(null);h.register(ResourceLocation.parse("vandorlabs:ramp_physics_root"),rootType);h.register(ResourceLocation.parse("vandorlabs:ramp_physics_proxy"),proxyType);});}
 }
 private static BlockState piston(){return Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING,Direction.EAST);}
 private static void setup(BlockPos base,boolean stone){level.setBlock(base,piston(),18);level.setBlock(base.above(),Blocks.REDSTONE_BLOCK.defaultBlockState(),18);if(stone)level.setBlock(base.east(),Blocks.STONE.defaultBlockState(),18);}
 private static boolean extend(BlockPos base){return level.getBlockState(base).triggerEvent(level,base,0,Direction.EAST.get3DDataValue());}
 @SubscribeEvent public static void start(ServerStartedEvent event){if(!enabled())return;level=event.getServer().overworld();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var p=ROOT.offset(x*16,0,z*16);level.getChunkAt(p);level.setChunkForced(p.getX()>>4,p.getZ()>>4,true);}actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,5911),"RampPhysics"));actor.setPos(ROOT.getX()+.5,ROOT.getY(),ROOT.getZ()-2);actor.setGameMode(GameType.SURVIVAL);
  if(System.getProperty("vandorlabs.transmitterPhase").endsWith("read")){phase=3;return;}
  level.setBlock(ROOT,rootBlock.defaultBlockState(),18);setup(PUSH,true);setup(BASE,false);setup(CONTROL,true);level.getServer().saveEverything(true,true,true);
 }
 @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(level==null)return;if(++ticks>500)throw new AssertionError("Ramp physics timeout phase="+phase);switch(phase){
  case 0->{var start=NativeRampJournal.prepare((NativeRampEntity)level.getBlockEntity(ROOT),actor,List.of(PUSH.east(),BASE),proxyBlock.defaultBlockState(),false);need(start.status()!=NativeRampJournal.Status.REFUSED,"prepare "+start.error());if(start.operation()==null)return;operation=start.operation();phase=1;}
  case 1->{var status=NativeRampJournal.poll(level,operation);need(status!=NativeRampJournal.Status.REFUSED,"prepare completion "+operation.error());if(operation.phase()!=NativeRampJournal.Phase.PREPARED)return;gate=new CountDownLatch(1);NativeRampJournal.testFault=stage->{if(stage.equals("before-write"))try{if(!gate.await(20,TimeUnit.SECONDS))throw new IllegalStateException("physics ARM gate timeout");}catch(InterruptedException ex){throw new RuntimeException(ex);}};need(NativeRampJournal.arm(level,operation,actor)==NativeRampJournal.Status.PENDING,"real async ARM pending");try{need(NativeRampHeldOwners.blocked(level,PUSH.east())&&NativeRampHeldOwners.blocked(level,BASE),"actual held cells");extend(PUSH);need(level.getBlockState(PUSH.east()).is(Blocks.STONE)&&level.getBlockState(PUSH.east(2)).isAir(),"held pushed stone cannot publish moving target");extend(BASE);need(level.getBlockState(BASE).equals(piston())&&level.getBlockState(BASE.east()).isAir(),"held base cannot publish empty-extension head");need(extend(CONTROL)&&level.getBlockState(CONTROL.east(2)).is(Blocks.MOVING_PISTON),"unheld piston still publishes actual moving target");need(NativeRampJournal.poll(level,operation)==NativeRampJournal.Status.PENDING,"ARM record still genuinely pending during piston probes");}finally{gate.countDown();NativeRampJournal.testFault=null;}phase=2;}
  case 2->{var result=NativeRampJournal.recover(level,operation);need(result!=NativeRampJournal.Status.REFUSED,"recover "+operation.error());if(result!=NativeRampJournal.Status.DONE)return;level.setBlock(PUSH.above(),Blocks.AIR.defaultBlockState(),18);level.setBlock(BASE.above(),Blocks.AIR.defaultBlockState(),18);need(!NativeRampHeldOwners.blocked(level,BASE),"retirement releases hold");finish();}
  case 3->{var loaded=NativeRampJournal.load(level);need(loaded!=NativeRampJournal.Status.REFUSED,"restart journal directory admitted");if(loaded!=NativeRampJournal.Status.READY)return;need(level.getBlockState(PUSH.east()).is(Blocks.STONE),"restart keeps sole original stone");need(level.getBlockState(BASE).equals(piston()),"restart keeps original piston base");need(level.getBlockState(PUSH.east(2)).isAir()&&level.getBlockState(BASE.east()).isAir(),"restart has no phantom moving target/head");need(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(PUSH).inflate(2)).isEmpty(),"restart has no dropped duplicate source");need(NativeRampJournal.atRoot(level,ROOT)==null,"completed operation retired");finish();}
 }}
 private static void finish(){System.out.println("[vandorlabs][ramp-held-physics] "+System.getProperty("vandorlabs.transmitterPhase")+" PASS checks="+checks);var server=level.getServer();level=null;server.saveEverything(true,true,true);server.halt(false);}
}
