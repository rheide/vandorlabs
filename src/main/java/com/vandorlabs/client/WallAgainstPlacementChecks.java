package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Actual ItemBlock placement must anchor a flat panel against a normal-face support. */
final class WallAgainstPlacementChecks {
 private static void need(boolean value,String message){if(!value)throw new AssertionError("Wall against placement: "+message);}
 static void run(WorldServer world,EntityPlayerMP player){
  BlockPos support=new BlockPos(1050,112,140);world.getChunkFromBlockCoords(support);Vec3d old=player.getPositionVector();float yaw=player.rotationYaw,pitch=player.rotationPitch;net.minecraft.world.GameType oldMode=player.interactionManager.getGameType();ItemStack oldHeld=player.getHeldItemMainhand();int cases=0;
  try{player.setGameType(net.minecraft.world.GameType.SURVIVAL);
   for(String id:new String[]{"programmable_wall","programmable_glass","programmable_porthole_wall","programmable_porthole_block"})for(EnumFacing normal:EnumFacing.HORIZONTALS)for(int profile=0;profile<10;profile++){
    EnumFacing side=profile<4?normal:profile<7?EnumFacing.UP:normal.rotateY();
    Block block=Block.getBlockFromName("vandorlabs:"+id);need(block!=null,"canonical block "+id);BlockPos dest=support.offset(side);world.setBlockToAir(dest);IBlockState supportState=net.minecraft.init.Blocks.STONE.getDefaultState();if(profile>0){supportState=block.getDefaultState();if(supportState.getPropertyKeys().contains(BlockProgrammableWall.FACING))supportState=supportState.withProperty(BlockProgrammableWall.FACING,normal);else supportState=supportState.withProperty(BlockGlassWall.ROTATED,normal.getAxis()==EnumFacing.Axis.X);supportState=supportState.withProperty(BlockProgrammableWall.DEPTH,(profile-1)%3);}world.setBlockState(support,supportState,2);
    player.setPosition(support.getX()+.5+normal.getFrontOffsetX()*3,support.getY()+1,support.getZ()+.5+normal.getFrontOffsetZ()*3);player.rotationYaw=normal.getOpposite().getHorizontalAngle();player.rotationPitch=0;ItemStack held=new ItemStack(block,2);player.setHeldItem(EnumHand.MAIN_HAND,held);
    AxisAlignedBB supportBox=supportState.getBoundingBox(world,support);float x=side==EnumFacing.EAST?(float)supportBox.maxX:side==EnumFacing.WEST?(float)supportBox.minX:(float)((supportBox.minX+supportBox.maxX)/2),z=side==EnumFacing.SOUTH?(float)supportBox.maxZ:side==EnumFacing.NORTH?(float)supportBox.minZ:(float)((supportBox.minZ+supportBox.maxZ)/2);
    need(player.interactionManager.processRightClickBlock(player,world,held,EnumHand.MAIN_HAND,support,side,x,side==EnumFacing.UP?1:.5F,z)==EnumActionResult.SUCCESS,"actual use "+id+" "+side);
    IBlockState placed=world.getBlockState(dest);need(placed.getBlock()==block&&held.getCount()==1,"one placed/one consumed "+id+" "+side);
    AxisAlignedBB box=placed.getBoundingBox(world,dest);double edge=side==EnumFacing.EAST?box.minX:side==EnumFacing.WEST?box.maxX:side==EnumFacing.SOUTH?box.minZ:box.maxZ;double expected=side==EnumFacing.EAST||side==EnumFacing.SOUTH?0:1;
    if(profile<4)need(Math.abs(edge-expected)<1e-9,"support-facing edge "+id+" "+side+" profile="+profile+" box="+box+" state="+placed);else need(normal.getAxis()==EnumFacing.Axis.X?Math.abs(box.minX-supportBox.minX)<1e-9&&Math.abs(box.maxX-supportBox.maxX)<1e-9:Math.abs(box.minZ-supportBox.minZ)<1e-9&&Math.abs(box.maxZ-supportBox.maxZ)<1e-9,"inherited top/tangent plane "+id+" normal="+normal+" profile="+profile+" box="+box+" support="+supportBox);world.setBlockToAir(dest);world.setBlockToAir(support);cases++;
   }
  }finally{for(EnumFacing side:EnumFacing.values())world.setBlockToAir(support.offset(side));world.setBlockToAir(support);player.setHeldItem(EnumHand.MAIN_HAND,oldHeld);player.setGameType(oldMode);player.setPosition(old.x,old.y,old.z);player.rotationYaw=yaw;player.rotationPitch=pitch;}
  System.out.println("[vandorlabs][reprolab] wall-against-placement PASS cases="+cases+" actualItemBlock/survival/fullCubeSupport/allHorizontalFaces/canonicalIDs/bounds/oneConsumed");
 }

 private static final String[] IDS={"programmable_wall","programmable_glass","programmable_porthole_wall","programmable_porthole_block"};
 private static final BlockPos PACKET_SUPPORT=new BlockPos(1060,112,140);
 private static int liveTicks,stage,fixture,wait;
 static boolean tick(net.minecraft.client.Minecraft mc)throws Exception{
  if(++liveTicks<80)return false;
  if(++wait>400)throw new AssertionError("Wall packet timeout fixture="+fixture+" stage="+stage+" clientMode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector());
  if(stage==0){mc.getIntegratedServer().callFromMainThread(()->{run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;}).get();stage=1;wait=0;}
  if(fixture==16){System.out.println("[vandorlabs][reprolab] wall-placement-packets PASS cases=16 synchronizedMode/slot/held/position/clientServerBounds/oneConsumed");System.out.println("[vandorlabs][reprolab] utility26-focused PASS wall-placement-only");return true;}
  EnumFacing side=EnumFacing.HORIZONTALS[fixture%4];Block block=Block.getBlockFromName("vandorlabs:"+IDS[fixture/4]);BlockPos dest=PACKET_SUPPORT.offset(side);double px=PACKET_SUPPORT.getX()+.5+side.getFrontOffsetX()*3,pz=PACKET_SUPPORT.getZ()+.5+side.getFrontOffsetZ()*3;
  if(stage==1){mc.getIntegratedServer().callFromMainThread(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);world.getChunkFromBlockCoords(PACKET_SUPPORT);world.setBlockToAir(dest);world.setBlockState(PACKET_SUPPORT,net.minecraft.init.Blocks.STONE.getDefaultState(),3);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.setBlockState(new BlockPos(px+x,111,pz+z),net.minecraft.init.Blocks.STONE.getDefaultState(),3);player.setGameType(net.minecraft.world.GameType.SURVIVAL);player.inventory.currentItem=0;player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block,2));player.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(0));player.inventoryContainer.detectAndSendChanges();player.rotationYaw=side.getOpposite().getHorizontalAngle();player.rotationPitch=22;player.setPositionAndUpdate(px,112,pz);return null;}).get();stage=2;wait=0;return false;}
  if(stage==2){if(mc.playerController.getCurrentGameType()!=net.minecraft.world.GameType.SURVIVAL||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getItem()!=net.minecraft.item.Item.getItemFromBlock(block)||mc.player.getHeldItemMainhand().getCount()!=2||mc.player.getDistanceSq(px,112,pz)>.05||mc.world.getBlockState(PACKET_SUPPORT).getBlock()!=net.minecraft.init.Blocks.STONE||!mc.world.isAirBlock(dest))return false;mc.player.rotationYaw=side.getOpposite().getHorizontalAngle();mc.player.rotationPitch=22;mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.Rotation(mc.player.rotationYaw,mc.player.rotationPitch,mc.player.onGround));Vec3d hit=new Vec3d(PACKET_SUPPORT).addVector(side==EnumFacing.EAST?1:side==EnumFacing.WEST?0:.5,.5,side==EnumFacing.SOUTH?1:side==EnumFacing.NORTH?0:.5);mc.playerController.processRightClickBlock(mc.player,mc.world,PACKET_SUPPORT,side,hit,EnumHand.MAIN_HAND);stage=3;wait=0;return false;}
  if(stage==3){boolean server=mc.getIntegratedServer().callFromMainThread(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);return world.getBlockState(dest).getBlock()==block&&player.getHeldItemMainhand().getCount()==1;}).get();if(!server||mc.world.getBlockState(dest).getBlock()!=block||mc.player.getHeldItemMainhand().getCount()!=1)return false;AxisAlignedBB box=mc.world.getBlockState(dest).getBoundingBox(mc.world,dest);double edge=side==EnumFacing.EAST?box.minX:side==EnumFacing.WEST?box.maxX:side==EnumFacing.SOUTH?box.minZ:box.maxZ;need(Math.abs(edge-(side==EnumFacing.EAST||side==EnumFacing.SOUTH?0:1))<1e-9,"client packet support-facing edge fixture="+fixture+" "+box);mc.getIntegratedServer().callFromMainThread(()->{mc.getIntegratedServer().getWorld(0).setBlockToAir(dest);return null;}).get();fixture++;stage=1;wait=0;}
  return false;
 }
}
