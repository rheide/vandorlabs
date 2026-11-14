package com.vandorlabs.neoforge.gear;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** A newly obstructed stroke must immediately expose the last valid native inventory and allow retry. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearObstructionChecks {
    private static final BlockPos ROOT=new BlockPos(0,110,0),SOURCE=ROOT.below(2);private static ServerLevel world;private static Actor actor;private static BlockPos obstruction;private static boolean running;private static int stage,ticks,checks;
    private static final class Actor extends net.neoforged.neoforge.common.util.FakePlayer {Actor(ServerLevel world){super(world,new com.mojang.authlib.GameProfile(new UUID(0,2211491),"NativeGearObstruction"));}}
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native obstruction stage="+stage+" ticks="+ticks+": "+why);}
    private static NativeGearEntity root(){var root=NativeGearFormation.at(world,ROOT);need(root!=null&&!root.inert(),"healthy source root");return root;}
    private static int diamonds(BlockPos at){return world.getBlockEntity(at) instanceof ChestBlockEntity chest&&chest.getItem(0).is(Items.DIAMOND)?chest.getItem(0).getCount():0;}
    private static void click(){actor.setPos(-3,108,-3);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);need(actor.gameMode.useItemOn(actor,world,ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(ROOT).add(0,0,-.5),Direction.NORTH,ROOT,false)).consumesAction(),"ordinary manual retry");}
    @SubscribeEvent public static void started(ServerStartedEvent event){if(!Boolean.getBoolean("vandorlabs.gearObstructionCheck"))return;try{world=event.getServer().overworld();actor=new Actor(world);actor.setGameMode(GameType.SURVIVAL);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){world.setChunkForced(x,z,true);world.getChunk(x,z);}for(int x=-6;x<=6;x++)for(int y=104;y<=116;y++)for(int z=-6;z<=6;z++)world.setBlock(new BlockPos(x,y,z),y==104?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);world.setBlock(ROOT.above(),Blocks.STONE.defaultBlockState(),3);actor.setPos(-3,108,-3);var stack=new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.ROTATING.id),2);var choices=NativeGearSettings.defaults().choices();choices.putBoolean("RotatePayload",true);var data=new CompoundTag();data.put(NativeGearItem.SETTINGS,choices);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));actor.setItemInHand(InteractionHand.MAIN_HAND,stack);for(int n=0;n<2;n++){var support=ROOT.relative(Direction.DOWN,n-1);need(actor.gameMode.useItemOn(actor,world,stack,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,-.5,0),Direction.DOWN,support,false)).consumesAction(),"actual supported Survival section");}need(stack.isEmpty(),"two sections consumed");running=true;}catch(Throwable failure){fail(failure);}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!running)return;try{need(++ticks<600,"timeout "+root().error()+" phase="+root().progress());if(stage==0){if(!root().controller()||root().assembly().columnHeight()!=2)return;world.setBlock(SOURCE,Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,Direction.EAST),3);var chest=(ChestBlockEntity)world.getBlockEntity(SOURCE);chest.setItem(0,new ItemStack(Items.DIAMOND,7));chest.setChanged();click();stage=1;return;}
        if(stage==1){need(!root().blocked(),"initial supported stroke "+root().error());if(root().payload()==null||root().payload().materialized()||root().progress()>.8||NativeGearJournal.busy(root()))return;var future=root().payload().boxes(root(),0);need(!future.isEmpty(),"future physical attachment bounds");obstruction=BlockPos.containing(future.getFirst().getCenter());need(!root().members().contains(obstruction)&&!obstruction.equals(SOURCE)&&world.getBlockState(obstruction).isAir(),"new physical obstruction lies beyond source assembly");world.setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);stage=2;return;}
        if(stage==2){if(!root().blocked()||root().progress()!=1||NativeGearJournal.busy(root())||diamonds(SOURCE)!=7)return;need(root().target()&&root().payload()!=null&&root().payload().materialized()&&root().payloadView()==null,"blocked arm immediately exposes native inventory at last valid endpoint");need(world.getBlockState(obstruction).is(Blocks.STONE),"real obstruction retained");need(NativeGearFormation.at(world,ROOT.below())!=null&&ROOT.equals(NativeGearFormation.at(world,ROOT.below()).owner()),"source assembly ownership retained");world.setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);click();stage=3;return;}
        if(stage==3){need(!root().blocked(),"clear stroke retry "+root().error());if(root().progress()!=0||NativeGearJournal.busy(root())||root().payload()==null||root().payload().materialized())return;var captured=(ChestBlockEntity)NativeGearSnapshot.stage(world,root().payload().cells().getFirst(),false);need(captured.getItem(0).getCount()==7&&diamonds(SOURCE)==0,"unsupported native Chest quarter orientation remains one visible captured owner");click();stage=4;return;}
        if(stage==4){need(!root().blocked(),"retry reversal "+root().error());if(root().progress()!=1||NativeGearJournal.busy(root())||diamonds(SOURCE)!=7)return;need(diamonds(obstruction)==0&&root().payloadView()==null,"exactly one native seven-diamond owner after retry/reversal");System.out.println("[vandorlabs][gear-obstruction] PASS checks="+checks+" actualSupportedPlacement/newObstruction/safeNativeRestore/clickAccess/retry/reversal");running=false;event.getServer().halt(false);}
    }catch(Throwable failure){fail(failure);}}
    private static void fail(Throwable failure){running=false;failure.printStackTrace();System.out.println("[vandorlabs][gear-obstruction] FAIL "+failure);if(world!=null)world.getServer().halt(false);}
}
