package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.io.File;

/** Physical entity movement through a retracted panel beyond the original block scan. */
final class SegmentedCollisionLiveChecks {
 private static final BlockPos ROOT=new BlockPos(1071,96,1071);private static int stage,ticks;
 private static void need(boolean value,String why){if(!value)throw new AssertionError("Segmented collision stage="+stage+": "+why);}
 private static void fixture(World world){
  net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:segmented_door");
  for(int row=0;row<4;row++){BlockPos at=ROOT.down(row);world.setBlockState(at,block.getDefaultState().withProperty(SegmentedDoorBlock.FACING,EnumFacing.NORTH),3);SegmentedDoorTile tile=SegmentedDoorTile.at(world,at);tile.topOffset=row;tile.height=4;tile.column=0;tile.columns=1;tile.formed=true;tile.configured=true;tile.settings=SegmentedDoorSettings.DEFAULT;tile.from=1;tile.targetOpen=true;tile.started=world.getTotalWorldTime();tile.duration=960;tile.readFromNBT(tile.writeToNBT(new NBTTagCompound()));world.notifyBlockUpdate(at,world.getBlockState(at),world.getBlockState(at),3);}
 }
 private static void movement(World world){
  SegmentedDoorTile root=SegmentedDoorTile.at(world,ROOT);need(root!=null&&root.ready()&&root.pose(0)==1,"observed open owner");double y=ROOT.getY()+1,z=ROOT.getZ()+4;
  AxisAlignedBB query=new AxisAlignedBB(ROOT.getX()+.2,y-.2,z-.2,ROOT.getX()+.8,y+.2,z+.2);need(!world.getCollisionBoxes(null,query).isEmpty(),"open panel has no collision outside its original cells");
  long built=SegmentedDoorCollisions.builds();com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId(),allocated=meter.getThreadAllocatedBytes(thread);for(int i=0;i<200;i++)need(!world.getCollisionBoxes(null,query).isEmpty(),"warm collision vanished");long bytes=meter.getThreadAllocatedBytes(thread)-allocated;need(SegmentedDoorCollisions.builds()==built,"stationary collisions rebuilt prepared geometry");System.out.println("[vandorlabs][reprolab] segmented-collision warm PASS side="+(world.isRemote?"client":"server")+" queries=200 geometryRebuilds=0 allocatedBytes="+bytes);
  EntityArmorStand actor=new EntityArmorStand(world);actor.setPosition(ROOT.getX()+.5,y-3,z);actor.move(MoverType.SELF,0,5,0);need(actor.getEntityBoundingBox().maxY<=y-SegmentedDoorGeometry.HALF_THICKNESS+1e-5,"entity passed through open panel y="+actor.posY);actor.setDead();
 }
 private static void matrix(World world){
  int checks=0;AxisAlignedBB lastQuery=null;net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:segmented_door");
  for(int height:new int[]{1,4,32}){BlockPos base=height==32?ROOT.up(64):ROOT;for(int row=0;row<4;row++)world.setBlockToAir(ROOT.down(row));
   for(int row=0;row<height;row++){BlockPos at=base.down(row);world.setBlockState(at,block.getDefaultState(),2);SegmentedDoorTile tile=SegmentedDoorTile.at(world,at);tile.topOffset=row;tile.height=height;tile.columns=1;tile.column=0;tile.formed=true;tile.configured=true;tile.readFromNBT(tile.writeToNBT(new NBTTagCompound()));}
   for(EnumFacing face:EnumFacing.HORIZONTALS){for(int row=0;row<height;row++)world.setBlockState(base.down(row),block.getDefaultState().withProperty(SegmentedDoorBlock.FACING,face),0);
    for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true})for(int pixels:new int[]{1,2,4,8,16})for(int position=0;position<3;position++)for(double progress:new double[]{.25,.5,1}){
     SegmentedDoorSettings settings=new SegmentedDoorSettings(direction,straight,1,0,false,0,false,SegmentedDoorSettings.DEFAULT.glass,position,pixels);for(int row=0;row<height;row++){SegmentedDoorTile tile=SegmentedDoorTile.at(world,base.down(row));tile.settings=settings;tile.from=progress;tile.targetOpen=true;tile.started=world.getTotalWorldTime();tile.duration=1000000;}
     for(int row:new java.util.TreeSet<Integer>(java.util.Arrays.asList(0,height-1))){int count=pixels<16&&!straight?16/pixels:1,strip=count/2,rotation=((int)(180-face.getHorizontalAngle())/90)&3;com.vandorlabs.physics.RigidTransform transform=count==1?SegmentedHitGeometry.transform(base.getX(),base.getY(),base.getZ(),rotation,row,height,0,1,progress,direction,straight,settings.depthOffset()):SegmentedHitGeometry.treadTransform(base.getX(),base.getY(),base.getZ(),rotation,row,height,0,1,progress,direction,pixels,settings.depthOffset(),strip);double center=(strip+.5)/count;double[] point=transform.point(direction<2?.5:center,direction<2?center-.5:0,0);AxisAlignedBB query=new AxisAlignedBB(point[0]-.05,point[1]-.05,point[2]-.05,point[0]+.05,point[1]+.05,point[2]+.05);lastQuery=query;need(!world.getCollisionBoxes(null,query).isEmpty(),"missing moved body height="+height+" row="+row+" direction="+direction+" straight="+straight+" tread="+pixels);
      EntityArmorStand actor=new EntityArmorStand(world);int axis=Math.abs(transform.r[5])>Math.abs(transform.r[2])?1:0;if(Math.abs(transform.r[8])>Math.abs(transform.r[axis*3+2]))axis=2;double[] start={point[0],point[1]-actor.height/2D,point[2]},move={0,0,0};start[axis]-=3;move[axis]=6;actor.setPosition(start[0],start[1],start[2]);actor.move(MoverType.SELF,move[0],move[1],move[2]);double at=axis==0?actor.posX:axis==1?actor.posY:actor.posZ;need(at<start[axis]+5.9,"entity crossed moved surface height="+height+" row="+row+" direction="+direction+" tread="+pixels);actor.setDead();checks++;
     }
    }
   }
   for(int row=0;row<height;row++)world.setBlockToAir(base.down(row));need(lastQuery!=null&&world.getCollisionBoxes(null,lastQuery).isEmpty(),"removed door retained ghost collision");
  }
  System.out.println("[vandorlabs][reprolab] segmented-collision matrix PASS cases="+checks+" all facings directions Straight/Perpendicular five treads three depths heights1/4/32 real entity moves");
 }
 static boolean tick(Minecraft mc,File output)throws Exception{
  need(++ticks<600,"timeout observing client owner mode="+mc.playerController.getCurrentGameType()+" flying="+mc.player.capabilities.isFlying+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPosition());mc.gameSettings.pauseOnLostFocus=false;
  if(stage==0){mc.player.closeScreen();mc.playerController.setGameType(net.minecraft.world.GameType.CREATIVE);mc.getIntegratedServer().addScheduledTask(()->{World world=mc.getIntegratedServer().getWorld(0);fixture(world);net.minecraft.entity.player.EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventory.currentItem=0;player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);player.inventoryContainer.detectAndSendChanges();player.connection.setPlayerLocation(ROOT.getX()+.5,ROOT.getY()+2,ROOT.getZ()+7,180,15);}).get();stage=1;return false;}
  SegmentedDoorTile tile=SegmentedDoorTile.at(mc.world,ROOT);if(!mc.player.capabilities.isFlying||mc.player.inventory.currentItem!=0||!mc.player.getHeldItemMainhand().isEmpty()||tile==null||!tile.ready()||tile.pose(0)!=1||mc.player.getDistanceSq(ROOT.getX()+.5,ROOT.getY()+2,ROOT.getZ()+7)>.1)return false;
  mc.getIntegratedServer().addScheduledTask(()->movement(mc.getIntegratedServer().getWorld(0))).get();movement(mc.world);mc.getIntegratedServer().addScheduledTask(()->matrix(mc.getIntegratedServer().getWorld(0))).get();
  System.out.println("[vandorlabs][reprolab] segmented-collision PASS actual server/client World collision and entity movement beyond original cells");System.out.println("[vandorlabs][reprolab] utility26-focused PASS segmented-collision-only");return true;
 }
 private SegmentedCollisionLiveChecks(){}
}
