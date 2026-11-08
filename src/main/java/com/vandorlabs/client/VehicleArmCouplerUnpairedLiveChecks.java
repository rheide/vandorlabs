package com.vandorlabs.client;

import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.vehicle.*;
import java.io.File;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Disposable real-player standalone head; no missing-pair relaxation in the fixture. */
public final class VehicleArmCouplerUnpairedLiveChecks {
 private static int stage,frames,checks;private static VehicleScan scan;private static UUID id;private static BlockPos armPos;private static long started;private static boolean reloaded,done;
 private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
 private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
 private static EntityGroundVehicle craft(Minecraft mc){return (EntityGroundVehicle)world(mc).getEntityFromUuid(id);}
 private static void need(boolean b,String why){checks++;if(!b)throw new IllegalStateException("Standalone closed Coupler stage="+stage+" frame="+frames+": "+why);}
 private static void solitary(EntityGroundVehicle craft){need(craft.articulation.jointCount()==0,"standalone head never invents a joint");VehicleArmCouplers.Snapshot heads=VehicleArmCouplers.observe(craft);need(heads.heads.size()==1&&heads.heads.get(0).locked,"one physically closed owned head");need(VehicleArmCouplerPairs.discoverForParking(heads).pairs.isEmpty(),"explicit standalone association admits no-pair parking topology");}
 private static void input(Minecraft mc)throws Exception{
  need(mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().getItem()==ModItems.REDSTONE_TRANSMITTER&&com.vandorlabs.items.ItemRedstoneTransmitter.channels(mc.player.getHeldItemMainhand()).equals(com.vandorlabs.redstone.ChannelList.of(VehicleArmCouplerFixture.CHANNEL)),"client mode/slot/exact transmitter before packet");
  mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=actor(mc);need(p.interactionManager.getGameType()==GameType.CREATIVE&&p.inventory.currentItem==0&&p.getHeldItemMainhand().getItem()==ModItems.REDSTONE_TRANSMITTER&&com.vandorlabs.items.ItemRedstoneTransmitter.channels(p.getHeldItemMainhand()).equals(com.vandorlabs.redstone.ChannelList.of(VehicleArmCouplerFixture.CHANNEL)),"server mode/slot/exact transmitter before packet");}).get();
  mc.player.setSneaking(false);mc.player.rotationPitch=-90;mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketEntityAction(mc.player,net.minecraft.network.play.client.CPacketEntityAction.Action.STOP_SNEAKING));mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketHeldItemChange(0));mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.Rotation(mc.player.rotationYaw,-90,false));mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);started=mc.getIntegratedServer().callFromMainThread(()->world(mc).getTotalWorldTime()).get();
 }
 public static boolean tick(Minecraft mc,File output)throws Exception{
  if(done)return true;mc.gameSettings.pauseOnLostFocus=false;need(++frames<1500,"bounded actual player/tick path");
  if(stage==0){System.setProperty("vandorlabs.armCouplerRuntime","true");mc.displayGuiScreen(null);mc.getIntegratedServer().addScheduledTask(()->{VehicleArmCouplerFixture.setup(world(mc),actor(mc));world(mc).setBlockToAir(VehicleArmCouplerFixture.BASE.add(VehicleArmCouplerFixture.SECOND));need(world(mc).getTileEntity(VehicleArmCouplerFixture.BASE.add(VehicleArmCouplerFixture.FIRST)) instanceof com.vandorlabs.coupler.TileCoupler,"ordinary placed standalone head retained");}).get();stage=1;frames=0;return false;}
  if(stage==1){if(frames<30||mc.playerController.getCurrentGameType()!=GameType.CREATIVE||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getItem()!=ModItems.REDSTONE_TRANSMITTER)return false;mc.getIntegratedServer().addScheduledTask(()->scan=new VehicleScan(world(mc),VehicleArmCouplerFixture.BASE.add(VehicleArmCouplerFixture.SEAT))).get();stage=2;frames=0;return false;}
  if(stage==2){if(!mc.getIntegratedServer().callFromMainThread(()->scan.step(256)).get())return false;mc.getIntegratedServer().addScheduledTask(()->{try{need(scan.result!=null,"ordinary first-body scan: "+scan.error);armPos=VehicleArmCouplerFixture.BASE.add(VehicleArmCouplerFixture.ARM).subtract(scan.origin);EntityGroundVehicle c=VehicleTransfer.assemble(world(mc),scan.result,scan.origin,actor(mc));id=c.getUniqueID();need(c.articulation.jointCount()==0,"standalone assembly starts without an invented partner");need(c.setHover(true)&&c.board(actor(mc)),"actual supported Hover and Pilot Seat");VehicleNetwork.send(actor(mc),c.structure,c.getEntityId(),id,new BlockPos(c),false,false);VehicleNetwork.state(c);}catch(Exception e){throw new IllegalStateException(e);}}).get();stage=3;frames=0;return false;}
  if(stage==3){if(frames<20||!(mc.player.getRidingEntity() instanceof EntityGroundVehicle)||!mc.player.getRidingEntity().getUniqueID().equals(id))return false;input(mc);stage=4;frames=0;return false;}
  if(stage==4){boolean ready=mc.getIntegratedServer().callFromMainThread(()->{EntityGroundVehicle c=craft(mc);need(c!=null&&c.structure!=null,"one live usable owner");GearTileEntity arm=(GearTileEntity)c.view.getTileEntity(armPos);long elapsed=world(mc).getTotalWorldTime()-started;String status=VehicleArmCouplerFixture.status(c,armPos);if(arm.target()){need(elapsed<40,"actual channel input: "+status);return false;}need(elapsed<220,"standalone captured stroke: "+status);if(arm.payload()==null||arm.payload().materialized())return false;solitary(c);return arm.progress()==0;}).get();if(!ready)return false;stage=5;frames=0;return false;}
  if(stage==5){if(frames<15)return false;input(mc);stage=6;frames=0;return false;}
  if(stage==6){boolean ready=mc.getIntegratedServer().callFromMainThread(()->{EntityGroundVehicle c=craft(mc);GearTileEntity arm=(GearTileEntity)c.view.getTileEntity(armPos);long elapsed=world(mc).getTotalWorldTime()-started;need(elapsed<240,"actual reversal: "+VehicleArmCouplerFixture.status(c,armPos));if(!arm.target())return false;solitary(c);
   if(!reloaded&&arm.progress()>.25&&arm.progress()<.8){double progress=arm.progress();NBTTagCompound saved=c.writeToNBT(new NBTTagCompound());EntityPlayerMP p=actor(mc);p.dismountRidingEntity();c.setDead();world(mc).removeEntityDangerously(c);EntityGroundVehicle restored=new EntityGroundVehicle(world(mc));restored.readFromNBT(saved);need(world(mc).spawnEntity(restored)&&restored.structure!=null,"saved moving owner reloads usable");need(Double.doubleToLongBits(((GearTileEntity)restored.view.getTileEntity(armPos)).progress())==Double.doubleToLongBits(progress),"reload preserves exact phase");need(restored.board(p),"real pilot remounts replacement");VehicleNetwork.send(p,restored.structure,restored.getEntityId(),id,new BlockPos(restored),false,false);VehicleNetwork.state(restored);solitary(restored);reloaded=true;return false;}return reloaded&&arm.progress()==1;}).get();if(!ready)return false;done=true;System.out.println("[vandorlabs][reprolab] utility26-focused PASS standalone-closed-coupler checks="+checks+" actual placement/assembly/input/captured stroke/reversal/moving reload/strict no-pair topology");return true;}
  return false;
 }
 private VehicleArmCouplerUnpairedLiveChecks(){}
}
