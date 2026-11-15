package com.vandorlabs.neoforge.gear.track;

import com.vandorlabs.neoforge.gear.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Small first installed-world proof, with normal world ticking for every assembly member. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeTrackRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(208,120,8);
    private static int ticks,stage,checks;
    private static boolean running;
    private static NativeGearEntity root(ServerLevel world){var root=NativeGearFormation.at(world,ROOT);need(root!=null,"root installed");return root;}
    private static void need(boolean value,String why){checks++;if(!value)throw new IllegalStateException("Track stage="+stage+" tick="+ticks+": "+why);}
    @SubscribeEvent public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent event){if(!Boolean.getBoolean("vandorlabs.trackFirst"))return;try{var world=event.getServer().overworld();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){world.setChunkForced(ROOT.getX()/16+x,ROOT.getZ()/16+z,true);world.getChunk(ROOT.getX()/16+x,ROOT.getZ()/16+z);}for(var pos:BlockPos.betweenClosed(ROOT.offset(-5,-4,-5),ROOT.offset(5,4,5)))world.setBlock(pos,Blocks.AIR.defaultBlockState(),18);world.setBlock(ROOT.above(),Blocks.STONE.defaultBlockState(),3);for(var pos:BlockPos.betweenClosed(ROOT.offset(-4,-3,-4),ROOT.offset(4,-3,4)))world.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
        var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(world);actor.setGameMode(GameType.SURVIVAL);actor.setPos(ROOT.getX()+3,ROOT.getY(),ROOT.getZ()-3);actor.setYRot(0);var arm=new ItemStack(NativeGearBindings.ITEMS.get("gear_extender"),2);actor.setItemInHand(InteractionHand.MAIN_HAND,arm);need(actor.gameMode.useItemOn(actor,world,arm,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(ROOT.above()).add(0,-.5,0),Direction.DOWN,ROOT.above(),false)).consumesAction()&&arm.getCount()==1,"ordinary supported Survival arm placement");
        var tracks=new ItemStack(NativeGearBindings.ITEMS.get("compact_track"),3);actor.setItemInHand(InteractionHand.MAIN_HAND,tracks);need(actor.gameMode.useItemOn(actor,world,tracks,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(ROOT).add(0,-.5,0),Direction.DOWN,ROOT,false)).consumesAction(),"ordinary Track against arm tip");var first=ROOT.below();need(actor.gameMode.useItemOn(actor,world,tracks,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(first).add(0,0,.5),Direction.SOUTH,first,false)).consumesAction(),"ordinary adjacent joined Track placement");need(tracks.getCount()==1,"exact one item per native Track: count="+tracks.getCount()+" first="+world.getBlockState(first)+" next="+world.getBlockState(first.south()));running=true;
    }catch(Throwable failed){fail(event.getServer(),failed);}}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(!running)return;try{ticks++;var world=event.getServer().overworld();var owner=root(world);if(ticks>240)throw new IllegalStateException("Track bounded stage timeout: "+owner.error());if(stage==0&&ticks>=10){need(owner.controller()&&!owner.inert()&&owner.assembly()!=null&&owner.assembly().kind()==com.vandorlabs.gear.core.GearTopology.FootKind.TRACK,"real scheduled formation "+owner.error());need(owner.assembly().feet().size()==2&&owner.trackDescriptor()!=null&&owner.trackDescriptor().feet().size()==2,"immutable full-foot view");need(!owner.collisionBoxes().isEmpty(),"authored full collision");for(var cell:owner.assembly().feet()){var pos=ROOT.offset(cell.x,cell.y,cell.z);need(world.getBlockEntity(pos) instanceof NativeTrackEntity track&&track.controllerEntity()==owner,"one owner for both native Track cells");}need(owner.command(false),"retract command accepted");stage=1;}else if(stage==1&&owner.progress()==0){need(!owner.blocked(),"clear stroke retracts "+owner.error());need(owner.command(true),"reverse command accepted");stage=2;}else if(stage==2&&owner.progress()==1){need(!owner.blocked(),"return deploys safely");var saved=owner.saveWithFullMetadata(world.registryAccess());need(saved.contains("NativeTrackFootView"),"native snapshot includes complete descriptor");var factory=owner.getType().create(ROOT,owner.getBlockState());factory.setLevel(world);factory.loadWithComponents(saved.copy(),world.registryAccess());need(factory instanceof NativeGearEntity copy&&!copy.inert()&&copy.trackDescriptor()!=null&&copy.trackDescriptor().write().equals(owner.trackDescriptor().write()),"strict saved-data roundtrip");world.removeBlock(ROOT.below().south(),false);stage=3;}else if(stage==3&&owner.assembly()!=null&&owner.assembly().feet().size()==1){need(owner.trackDescriptor()!=null&&owner.trackDescriptor().feet().size()==1,"mining rebuilds the surviving Track descriptor");System.out.println("[vandorlabs][modern] track-first PASS checks="+checks);running=false;event.getServer().halt(false);}}catch(Throwable failed){fail(event.getServer(),failed);}}
    private static void fail(net.minecraft.server.MinecraftServer server,Throwable failed){running=false;failed.printStackTrace();System.out.println("[vandorlabs][modern] track-first FAIL "+failed);server.halt(false);}
    private NativeTrackRuntimeChecks(){}
}
