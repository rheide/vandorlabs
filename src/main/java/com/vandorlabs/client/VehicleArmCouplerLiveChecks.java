package com.vandorlabs.client;

import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.vehicle.*;
import java.io.File;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Actual placed rig, durable source assembly, real player transmitter input and normal world ticks. */
public final class VehicleArmCouplerLiveChecks {
    private static int stage,frames,checks;private static VehicleScan scan;private static UUID id;private static BlockPos localArm;private static long started;
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static EntityGroundVehicle craft(Minecraft mc){return id==null?null:(EntityGroundVehicle)world(mc).getEntityFromUuid(id);}
    private static void need(boolean v,String why){checks++;if(!v)throw new IllegalStateException("Carried arm Coupler live: "+why+" stage="+stage+" frames="+frames);}
    public static boolean tick(Minecraft mc,File output)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;need(++frames<1800,"timeout");
        if(stage==0){mc.displayGuiScreen(null);mc.getIntegratedServer().addScheduledTask(()->VehicleArmCouplerFixture.setup(world(mc),actor(mc))).get();stage=1;frames=0;return false;}
        if(stage==1){if(frames<30||mc.playerController.getCurrentGameType()!=GameType.CREATIVE||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getItem()!=ModItems.REDSTONE_TRANSMITTER)return false;mc.getIntegratedServer().addScheduledTask(()->{scan=new VehicleScan(world(mc),VehicleArmCouplerFixture.BASE.add(VehicleArmCouplerFixture.SEAT));}).get();stage=2;frames=0;return false;}
        if(stage==2){boolean ready=mc.getIntegratedServer().callFromMainThread(()->scan.step(256)).get();if(!ready)return false;mc.getIntegratedServer().addScheduledTask(()->{try{need(scan.result!=null,"actual placed rig scan: "+scan.error);BlockPos origin=scan.origin;localArm=VehicleArmCouplerFixture.BASE.add(VehicleArmCouplerFixture.ARM).subtract(origin);EntityGroundVehicle craft=VehicleTransfer.assemble(world(mc),scan.result,origin,actor(mc));id=craft.getUniqueID();need(craft.articulation.jointCount()==1,"native placed heads initially create one articulated bridge");need(craft.setHover(true),"actual supported Hover admission");need(craft.board(actor(mc)),"real player boards pilot seat");VehicleNetwork.send(actor(mc),craft.structure,craft.getEntityId(),id,new BlockPos(craft),false,false);VehicleNetwork.state(craft);started=world(mc).getTotalWorldTime();}catch(Exception failure){throw new IllegalStateException(failure);}}).get();stage=3;frames=0;return false;}
        if(stage==3){net.minecraft.entity.Entity raw=mc.world.getEntityByID(mc.player.getRidingEntity()==null?-1:mc.player.getRidingEntity().getEntityId());if(frames<20||!(raw instanceof EntityGroundVehicle)||!raw.getUniqueID().equals(id)||((EntityGroundVehicle)raw).structure==null||mc.playerController.getCurrentGameType()!=GameType.CREATIVE||mc.player.getHeldItemMainhand().getItem()!=ModItems.REDSTONE_TRANSMITTER)return false;mc.player.setSneaking(false);mc.player.rotationPitch=-90;mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.Rotation(mc.player.rotationYaw,-90,false));mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);stage=4;frames=0;return false;}
        if(stage==4){boolean done=mc.getIntegratedServer().callFromMainThread(()->{EntityGroundVehicle craft=craft(mc);need(craft!=null,"placed craft remains installed");GearTileEntity arm=(GearTileEntity)craft.view.getTileEntity(localArm);if(world(mc).getTotalWorldTime()-started<35)return false;need(!arm.target(),"real transmitter input reaches carried configured arm: "+VehicleArmCouplerFixture.status(craft,localArm));System.out.println("[vandorlabs][reprolab] carried-arm-coupler observed "+VehicleArmCouplerFixture.status(craft,localArm));need(craft.articulation.jointCount()==1,"captured head must retain articulated bridge: "+VehicleArmCouplerFixture.status(craft,localArm));need(arm.progress()<.9,"closed head pair moves together through actual arm stroke: "+VehicleArmCouplerFixture.status(craft,localArm));return true;}).get();if(!done)return false;System.out.println("[vandorlabs][reprolab] carried-arm-coupler-live PASS checks="+checks+" actual item mounting/support/payload/vehicle capture/player transmitter/world ticks; further reversal/reload cases pending");return true;}
        return false;
    }
    private VehicleArmCouplerLiveChecks(){}
}
