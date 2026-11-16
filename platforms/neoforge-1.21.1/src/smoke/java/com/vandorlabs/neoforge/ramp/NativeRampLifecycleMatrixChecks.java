package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Canonical scheduled capture/deployment/return across modes, scan faces and slab halves. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampLifecycleMatrixChecks {
 private record Case(Direction scan,int housing,int mode,boolean vertical){}
 private static final List<Case> CASES=new ArrayList<>();
 private static final BlockPos ROOT=new BlockPos(24760,100,24760);
 private static final Map<BlockPos,CompoundTag> originals=new LinkedHashMap<>();
 private static ServerLevel world;private static ServerPlayer actor;private static NativeRampEntity owner;
 private static int index,stage,ticks,checks,caseTicks;
 private static boolean enabled(){return "ramp-lifecycle-matrix".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message+" case="+index+"/"+(index<CASES.size()?CASES.get(index):"done")+" stage="+stage+" ticks="+caseTicks+" status="+(owner==null?"none":owner.controller().status()));}
 @SubscribeEvent public static void start(net.neoforged.neoforge.event.server.ServerStartedEvent event){
  if(!enabled())return;need(NativeRampBindings.enabled(),"canonical bindings enabled");world=event.getServer().overworld();world.getServer().tickRateManager().setTickRate(200);
  for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var pos=ROOT.offset(x*16,0,z*16);world.getChunkAt(pos);world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);}
  actor=new net.neoforged.neoforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(3,7725),"RampLifecycleMatrix"));actor.setPos(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()+.5);actor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
  for(var face:Direction.values())for(int housing=0;housing<3;housing++)for(int mode=0;mode<7;mode++)CASES.add(new Case(face,housing,mode,false));
  for(var face:Direction.Plane.HORIZONTAL)for(int housing=0;housing<3;housing++)for(int mode=0;mode<7;mode++)CASES.add(new Case(face,housing,mode,true));
 }
 private static void prepare(Case sample){
  need(NativeRampJournal.atRoot(world,ROOT)==null,"previous durable owner retired before replacement");
  for(var pos:BlockPos.betweenClosed(ROOT.offset(-5,-7,-5),ROOT.offset(5,7,5)))world.setBlock(pos,Blocks.AIR.defaultBlockState(),18);
  var facing=sample.scan.getAxis().isHorizontal()?sample.scan:Direction.NORTH;
  var state=sample.housing==0?NativeRampBindings.BLOCK.defaultBlockState():NativeRampBindings.SLAB.defaultBlockState().setValue(NativeRampSlabBlock.HALF,sample.housing==2?Half.TOP:Half.BOTTOM);
  state=state.setValue(NativeRampBlock.FACING,facing).setValue(NativeRampBlock.ACTIVE_FACE,sample.scan);
  world.setBlock(ROOT,state,18);owner=(NativeRampEntity)world.getBlockEntity(ROOT);need(owner!=null,"canonical owner installed");
  var seed=ROOT.relative(sample.scan);var second=sample.vertical||sample.mode==6?seed.relative(sample.scan==Direction.DOWN?Direction.DOWN:Direction.UP):seed.relative(facing);
  originals.clear();for(var pos:sample.mode==6&&sample.scan==Direction.DOWN?List.of(seed):List.of(seed,second)){world.setBlock(pos,com.vandorlabs.neoforge.VandorLabsBootstrap.PROGRAMMABLE_SLAB.get().defaultBlockState(),18);var tile=world.getBlockEntity(pos);tile.getPersistentData().putString("LifecycleMatrixOriginal","exact");originals.put(pos,tile.saveWithFullMetadata(world.registryAccess()));}
  int end=sample.scan==Direction.DOWN?-4:4;boolean lift=sample.mode==2||sample.mode==3,fill=sample.mode==1||sample.mode==3;int belt=sample.mode==4?1:sample.mode==5?2:0;
  // Item Direction is the released horizontal index, independent of six-face scan metadata.
  int direction=facing==Direction.SOUTH?0:facing==Direction.WEST?1:facing==Direction.NORTH?2:3;
  var choices=new NativeRampSettings(0,end,8,false,lift,fill,0,true,0,belt,sample.mode==6,sample.vertical,0,true,direction,com.vandorlabs.redstone.ChannelList.EMPTY);
  var result=owner.controller().configure(actor,choices);need(result.admitted()&&result.choicesCommitted()&&!result.activationRefused(),"ordinary canonical configuration admitted");caseTicks=0;stage=1;
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
  if(!enabled()||world==null)return;
  try{ticks++;if(stage==0){if(index==CASES.size()){System.out.println("[vandorlabs][ramp-lifecycle-matrix] PASS cases="+CASES.size()+" checks="+checks+" scheduled/canonical/sevenModes/sixScanFaces/slabHalves/verticalLayout/exactReturn");world.getServer().halt(false);stage=3;return;}prepare(CASES.get(index));return;}
   if(stage==3)return;need(++caseTicks<1600,"scheduled lifecycle bounded");var runtime=owner.controller();need(!runtime.status().error(),"configured lifecycle remains valid");
   if(stage==1){if(runtime.committedPose()!=1||runtime.status().moving())return;need(runtime.attached(),"deployed endpoint retains durable originals");need(runtime.requestPose(0).admitted(),"ordinary original return admitted");stage=2;return;}
   if(stage==2){if(runtime.phase()!=NativeRampController.Phase.IDLE)return;for(var entry:originals.entrySet())need(world.getBlockEntity(entry.getKey())!=null&&NativeRampJournal.sameSnapshot(entry.getValue(),world.getBlockEntity(entry.getKey()).saveWithFullMetadata(world.registryAccess())),"exact native source ownership returned");need(NativeRampJournal.atRoot(world,ROOT)==null,"durable ownership retired");index++;if(index%21==0)System.out.println("[vandorlabs][ramp-lifecycle-matrix] progress cases="+index+"/"+CASES.size());stage=0;}
  }catch(Throwable failure){failure.printStackTrace();System.err.println("[vandorlabs][ramp-lifecycle-matrix] FAIL "+failure);world.getServer().halt(false);throw failure;}
 }
 private NativeRampLifecycleMatrixChecks(){}
}
