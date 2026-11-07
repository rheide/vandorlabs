package com.vandorlabs.neoforge.shape;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid="vandorlabs")
public final class WallAgainstPlacementChecks {
 private static int checks,cases;private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError("Wall against placement: "+message);}
 @SubscribeEvent public static void start(ServerStartedEvent event){if(!"wall-against-placement".equals(System.getProperty("vandorlabs.transmitterPhase")))return;var server=event.getServer();try{
  var level=server.overworld();var support=new BlockPos(24800,110,24800);level.getChunkAt(support);var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,5912),"WallPlacement"));player.setGameMode(GameType.SURVIVAL);
  for(String name:new String[]{"programmable_wall","programmable_glass","programmable_porthole_wall","programmable_porthole_block"})for(var normal:Direction.Plane.HORIZONTAL)for(int profile=0;profile<10;profile++){
   var side=profile<4?normal:profile<7?Direction.UP:normal.getClockWise();
   var id=ResourceLocation.parse("vandorlabs:"+name);var block=BuiltInRegistries.BLOCK.get(id);need(block!=Blocks.AIR,"canonical "+id);var dest=support.relative(side);level.setBlock(dest,Blocks.AIR.defaultBlockState(),18);var supportState=Blocks.STONE.defaultBlockState();if(profile>0){supportState=block.defaultBlockState();if(block instanceof com.vandorlabs.neoforge.glass.NativeGlassBlock)supportState=supportState.setValue(com.vandorlabs.neoforge.glass.NativeGlassBlock.ROTATED,normal.getAxis()==Direction.Axis.X).setValue(com.vandorlabs.neoforge.glass.NativeGlassBlock.DEPTH,(profile-1)%3);else supportState=supportState.setValue(NativeFlatWallBlock.FACING,normal).setValue(NativeFlatWallBlock.DEPTH,(profile-1)%3);}level.setBlock(support,supportState,18);var supportBox=supportState.getShape(level,support).bounds();
   player.setPos(support.getX()+.5+normal.getStepX()*3,support.getY()+1,support.getZ()+.5+normal.getStepZ()*3);player.setYRot(normal.getOpposite().toYRot());var stack=new ItemStack(BuiltInRegistries.ITEM.get(id),2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
   var hit=new BlockHitResult(Vec3.atLowerCornerOf(support).add(side==Direction.EAST?supportBox.maxX:side==Direction.WEST?supportBox.minX:(supportBox.minX+supportBox.maxX)/2,side==Direction.UP?1:.5,side==Direction.SOUTH?supportBox.maxZ:side==Direction.NORTH?supportBox.minZ:(supportBox.minZ+supportBox.maxZ)/2),side,support,false);var result=stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
   var state=level.getBlockState(dest);need(result.consumesAction()&&state.is(block)&&stack.getCount()==1,"actual Survival use/consumption "+id+" "+side+" "+result);var box=state.getShape(level,dest).bounds();double edge=side==Direction.EAST?box.minX:side==Direction.WEST?box.maxX:side==Direction.SOUTH?box.minZ:box.maxZ;double expected=side==Direction.EAST||side==Direction.SOUTH?0:1;if(profile<4)need(Math.abs(edge-expected)<1e-9,"support-facing edge "+id+" "+side+" profile="+profile+" state="+state+" box="+box);else need(normal.getAxis()==Direction.Axis.X?Math.abs(box.minX-supportBox.minX)<1e-9&&Math.abs(box.maxX-supportBox.maxX)<1e-9:Math.abs(box.minZ-supportBox.minZ)<1e-9&&Math.abs(box.maxZ-supportBox.maxZ)<1e-9,"inherited top/tangent plane "+id+" "+normal+" profile="+profile);level.setBlock(dest,Blocks.AIR.defaultBlockState(),18);level.setBlock(support,Blocks.AIR.defaultBlockState(),18);cases++;
  }
  System.out.println("[vandorlabs][wall-against-placement] PASS cases="+cases+" checks="+checks);
 }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][wall-against-placement] FAIL "+failure);}finally{server.saveEverything(true,true,true);server.halt(false);}}
}
