package com.vandorlabs.neoforge.builders;

import com.vandorlabs.neoforge.NativeMountedSwitchBlock;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.*;
import java.util.*;

/** Real Survival BlockItem routing on machine walls, including actual redstone activation. */
public final class NativeMachinePlacementChecks {
 private static int checks;
 public static void run(ServerLevel world,ServerPlayer actor,NativeBuilderEntity root){
  var position=actor.position();float yaw=actor.getYRot(),pitch=actor.getXRot();var mode=actor.gameMode.getGameModeForPlayer();var main=actor.getMainHandItem().copy();var off=actor.getOffhandItem().copy();boolean crouching=actor.isShiftKeyDown();int activation=root.activation();var channels=root.channels();var core=root.getBlockPos();var part=root.cell(root.design().width()-1,1,0);var lever=core.north();var button=part.north();var oldLever=world.getBlockState(lever);var oldButton=world.getBlockState(button);var key=root.key();var partTag=world.getBlockEntity(part).saveWithoutMetadata(world.registryAccess());
  try{
   need(root.formed()&&root.synthesisReady()&&!root.processing()&&root.facing()==Direction.NORTH,"formed idle North-facing fixture");need(oldLever.isAir()&&oldButton.isAir()&&world.getBlockEntity(lever)==null&&world.getBlockEntity(button)==null,"empty attachment targets");need(world.getBlockEntity(part) instanceof NativeBuilderPartEntity member&&member.root()==root,"selected front wall is owned part");actor.setGameMode(GameType.SURVIVAL);actor.setShiftKeyDown(false);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);actor.closeContainer();root.configureRedstone(1,ChannelList.EMPTY);root.samplePower();need(!root.enabled(),"unpowered redstone activation starts disabled");
   place(world,actor,core,new ItemStack(Items.LEVER,2));var leverState=world.getBlockState(lever);need(leverState.is(Blocks.LEVER)&&actor.getMainHandItem().is(Items.LEVER)&&actor.getMainHandItem().getCount()==1,"actual Survival lever placed on formed core and one held consumed");need(leverState.getValue(LeverBlock.FACE)==AttachFace.WALL&&leverState.getValue(LeverBlock.FACING)==Direction.NORTH&&!leverState.getValue(LeverBlock.POWERED),"lever attaches to North wall with correct orientation");
   place(world,actor,part,new ItemStack(VandorLabsBootstrap.PUSH_BUTTON_ITEM.get(),2));var buttonState=world.getBlockState(button);need(buttonState.is(VandorLabsBootstrap.PUSH_BUTTON.get())&&buttonState.getValue(NativeMountedSwitchBlock.FACING)==Direction.NORTH&&actor.getMainHandItem().getCount()==1,"actual Survival native button placed on owned part and one held consumed");need(root.formed()&&root.key().equals(key)&&world.getBlockEntity(core)==root&&world.getBlockEntity(part) instanceof NativeBuilderPartEntity member&&member.root()==root&&partTag.equals(member.saveWithoutMetadata(world.registryAccess())),"placements preserve formed assembly and exact part ownership");
   actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);near(actor,lever);var result=actor.gameMode.useItemOn(actor,world,ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit(lever));root.samplePower();need(result.consumesAction()&&world.getBlockState(lever).getValue(LeverBlock.POWERED)&&root.enabled(),"real placed lever toggle powers formed assembler redstone activation");actor.gameMode.useItemOn(actor,world,ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit(lever));root.samplePower();need(!world.getBlockState(lever).getValue(LeverBlock.POWERED)&&!root.enabled(),"second real lever toggle disables assembler");
   observers(world,actor,core.offset(12,0,-7));System.out.println("[vandorlabs][machine-placement] PASS checks="+checks+" realSurvival/coreLever/ownedPartNativeButton/attachmentOrientation/heldCounts/redstone/ownership");
  }finally{actor.closeContainer();if(oldButton.isAir())world.removeBlock(button,false);else world.setBlock(button,oldButton,3);if(oldLever.isAir())world.removeBlock(lever,false);else world.setBlock(lever,oldLever,3);root.configureRedstone(activation,channels);root.samplePower();actor.setItemInHand(InteractionHand.MAIN_HAND,main);actor.setItemInHand(InteractionHand.OFF_HAND,off);actor.setPos(position.x,position.y,position.z);actor.setYRot(yaw);actor.setXRot(pitch);actor.setShiftKeyDown(crouching);actor.setGameMode(mode);}
 }
 private static void observers(ServerLevel world,ServerPlayer actor,BlockPos start){int i=0;for(String id:List.of("standalone_dish","standalone_antenna")){var p=start.east(i++*3);if(!world.hasChunkAt(p)||!world.hasChunkAt(p.north())||!world.isEmptyBlock(p)||!world.isEmptyBlock(p.north()))continue;var block=com.vandorlabs.neoforge.navigation.observer.NativeObserverBindings.CORES.get(id);need(block!=null,"source core registered "+id);world.setBlock(p,block.defaultBlockState(),3);try{var original=world.getBlockState(p);place(world,actor,p,new ItemStack(Items.LEVER,2));need(world.getBlockState(p.north()).is(Blocks.LEVER)&&actor.getMainHandItem().getCount()==1&&world.getBlockState(p)==original,"unformed "+id+" allows real wall attachment without formation");}finally{world.removeBlock(p.north(),false);world.removeBlock(p,false);}}}
 private static void place(ServerLevel world,ServerPlayer actor,BlockPos support,ItemStack held){actor.closeContainer();actor.setItemInHand(InteractionHand.MAIN_HAND,held);near(actor,support);var result=actor.gameMode.useItemOn(actor,world,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit(support));need(result.consumesAction(),"real BlockItem use handled support="+support+" held="+actor.getMainHandItem()+" result="+result);}
 private static void near(ServerPlayer actor,BlockPos p){actor.setPos(p.getX()+.5,p.getY(),p.getZ()-2.5);actor.setYRot(0);actor.setXRot(10);}
 private static BlockHitResult hit(BlockPos p){return new BlockHitResult(new Vec3(p.getX()+.5,p.getY()+.5,p.getZ()),Direction.NORTH,p,false);}
 private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private NativeMachinePlacementChecks(){}
}
