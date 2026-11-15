package com.vandorlabs.neoforge.gear;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** Synchronous callback automation must not reuse the journal's own publication authority. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearReentrantChecks {
 private static final BlockPos ROOT=new BlockPos(8,110,8),LEFT=ROOT.below(2),RIGHT=LEFT.east(2);
 private static ServerLevel world;private static net.neoforged.neoforge.common.util.FakePlayer actor;private static int stage,ticks,checks,stolen;private static boolean running,probed;private static List<ChestBlockEntity> originals;
 private static boolean enabled(){return System.getProperty("vandorlabs.gearJournalPhase","").equals("reentrant");}
 private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native arm reentrant stage="+stage+" tick="+ticks+": "+why);}
 private static NativeGearEntity root(){var root=NativeGearFormation.at(world,ROOT);need(root!=null&&!root.inert(),"healthy root");return root;}
 @SubscribeEvent public static void start(ServerStartedEvent event){if(!enabled())return;try{world=event.getServer().overworld();world.setChunkForced(0,0,true);world.getChunk(0,0);actor=new net.neoforged.neoforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,8810123),"ReentrantArm"));actor.setGameMode(GameType.SURVIVAL);actor.setPos(8.5,108,5);if(Boolean.getBoolean("vandorlabs.gearEscrowOnly")){checks+=NativeGearEscrowChecks.run(world,actor);System.out.println("[vandorlabs][gear-reentrant] PASS escrowOnly checks="+checks);world.getServer().halt(false);return;}var choices=NativeGearSettings.defaults().choices();choices.putBoolean("JoinArms",true);choices.putInt("JoinRange",3);var tag=new CompoundTag();tag.put(NativeGearItem.SETTINGS,choices);for(int x:new int[]{0,2}){var at=ROOT.east(x);world.setBlock(at.above(),Blocks.STONE.defaultBlockState(),3);var stack=new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.EXTENDER.id),2);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));actor.setItemInHand(InteractionHand.MAIN_HAND,stack);for(int n=0;n<2;n++){var support=at.above(1-n);need(actor.gameMode.useItemOn(actor,world,stack,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,-.5,0),Direction.DOWN,support,false)).consumesAction(),"actual section placement");}need(stack.isEmpty(),"sections consumed");}running=true;}catch(Throwable failure){fail(failure);}}
 private static void callback(String point){if(!point.equals("after-cell")||probed)return;for(var chest:originals)if(world.getBlockEntity(chest.getBlockPos())==chest&&!chest.getItem(0).isEmpty()){probed=true;var extracted=new InvWrapper(chest).extractItem(0,1,false);stolen+=extracted.getCount();need(!world.setBlock(chest.getBlockPos(),Blocks.GOLD_BLOCK.defaultBlockState(),18),"callback cannot replace later held chest");need(!world.setBlock(ROOT,Blocks.AIR.defaultBlockState(),18),"callback cannot remove held root");need(world.getBlockEntity(chest.getBlockPos())==chest,"physical callback source remains installed");System.out.println("[vandorlabs][gear-reentrant] callback remainingNative="+chest.getItem(0).getCount()+" extracted="+stolen+" phase="+root().progress()+" journalBusy="+NativeGearJournal.busy(root()));return;}}
 @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!running)return;try{need(++ticks<500,"bounded transaction progress="+root().progress()+" busy="+NativeGearJournal.busy(root())+" error="+root().error());if(stage==0){if(!root().controller()||root().columns().size()!=2||root().assembly().columnHeight()!=2)return;world.setBlock(LEFT,Blocks.CHEST.defaultBlockState(),3);world.setBlock(LEFT.east(),Blocks.STONE.defaultBlockState(),3);world.setBlock(RIGHT,Blocks.CHEST.defaultBlockState(),3);var left=(ChestBlockEntity)world.getBlockEntity(LEFT);var right=(ChestBlockEntity)world.getBlockEntity(RIGHT);left.setItem(0,new ItemStack(Items.DIAMOND,7));right.setItem(0,new ItemStack(Items.DIAMOND,11));originals=List.of(left,right);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);root().authorize(actor);NativeGearJournal.testFault=NativeGearReentrantChecks::callback;need(root().command(false),"ordinary capture queued");stage=1;return;}
 if(stage==1){need(!root().blocked(),"capture remains valid "+root().error());if(!probed||NativeGearJournal.busy(root())||root().payload()==null||root().payload().materialized())return;int owned=0;for(var cell:root().payload().cells())if(cell.tile()!=null){var inventory=cell.tile().getList("Items",10);for(var entry:inventory)owned+=ItemStack.parse(world.registryAccess(),(CompoundTag)entry).orElseThrow().getCount();}need(owned==18,"captured receipt retains exactly18 diamonds");need(stolen==0,"reentrant InvWrapper extracted "+stolen+" while captured receipt still owns "+owned+" diamonds");NativeGearJournal.testFault=null;running=false;System.out.println("[vandorlabs][gear-reentrant] PASS checks="+checks+" realAsyncCapture/callbackInventoryFrozen/oneUsableOwner18");event.getServer().halt(false);}}
 catch(Throwable failure){fail(failure);}}
 private static void fail(Throwable failure){running=false;NativeGearJournal.testFault=null;failure.printStackTrace();System.out.println("[vandorlabs][gear-reentrant] FAIL "+failure);if(world!=null)world.getServer().halt(false);}
 private NativeGearReentrantChecks(){}
}
