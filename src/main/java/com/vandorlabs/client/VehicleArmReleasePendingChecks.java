package com.vandorlabs.client;

import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.items.*;
import com.vandorlabs.vehicle.*;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;

/** Actual pending preparation, client pause and real transmitter cancellation; no direct planner call. */
public final class VehicleArmReleasePendingChecks {
    private static final boolean ACTIVE="1".equals(System.getenv("VANDOR_LABS_COUPLER_ARM_RELEASE_CANCEL_ONLY"));
    private static int stage,frames,checks;private static long since;private static double frozen;private static boolean clicked;
    private static final java.lang.reflect.Method SAMPLE;
    static{try{SAMPLE=Class.forName("com.vandorlabs.gear.client.GearVisualMotion").getDeclaredMethod("sample",GearTileEntity.class,float.class);SAMPLE.setAccessible(true);}catch(Exception failure){throw new ExceptionInInitializerError(failure);}}
    public static boolean active(){return ACTIVE;}
    private static void need(boolean v,String why){checks++;if(!v)throw new IllegalStateException("Pending arm release: "+why+" stage="+stage+" frames="+frames);}
    private static EntityGroundVehicle server(Minecraft mc,UUID id){return (EntityGroundVehicle)mc.getIntegratedServer().getWorld(0).getEntityFromUuid(id);}
    private static EntityGroundVehicle client(Minecraft mc,UUID id){for(net.minecraft.entity.Entity entity:mc.world.loadedEntityList)if(entity instanceof EntityGroundVehicle&&entity.getUniqueID().equals(id))return (EntityGroundVehicle)entity;return null;}
    public static boolean tick(Minecraft mc,UUID id,BlockPos root)throws Exception{
        frames++;need(frames<600,"bounded cancellation/client restoration");
        if(stage==0){boolean pending=mc.getIntegratedServer().callFromMainThread(()->{EntityGroundVehicle c=server(mc,id);if(!VehicleCouplings.preparingArmRelease(c))return false;GearTileEntity gear=(GearTileEntity)c.view.getTileEntity(root);need(!c.transferInProgress&&!gear.motionBlocked(),"preparation does not mutate transfer or authoritative blocked flags");frozen=gear.progress();need(frozen>0&&frozen<1,"frozen fractional pose");since=c.world.getTotalWorldTime();return true;}).get();if(!pending)return false;stage=1;frames=0;CouplerWireProbe.install(mc);CouplerWireProbe.phase(4);return false;}
        if(stage==1){boolean enough=mc.getIntegratedServer().callFromMainThread(()->{EntityGroundVehicle c=server(mc,id);GearTileEntity gear=(GearTileEntity)c.view.getTileEntity(root);need(VehicleCouplings.preparingArmRelease(c)&&gear.progress()==frozen&&!gear.motionBlocked()&&VehicleCouplingTransfer.pendingCount((net.minecraft.world.WorldServer)c.world)==0,"long pending keeps exact authority and starts no journal");return c.world.getTotalWorldTime()-since>=16;}).get();if(!enough)return false;EntityGroundVehicle c=client(mc,id);if(c==null||c.view==null)return false;GearTileEntity visual=(GearTileEntity)c.view.getTileEntity(root);if(visual==null||!visual.motionBlocked())return false;double sample=(double)SAMPLE.invoke(null,visual,1F);if(Math.abs(sample-frozen)>1e-6)return false;mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);need(p.interactionManager.getGameType()==GameType.SURVIVAL,"server remains Survival");p.setSneaking(false);p.inventory.currentItem=0;ItemStack stack=new ItemStack(ModItems.REDSTONE_TRANSMITTER,1,3);ItemRedstoneTransmitter.configure(stack,com.vandorlabs.redstone.ChannelList.of(VehicleArmCouplerFixture.CHANNEL));p.inventory.setInventorySlotContents(0,stack);p.inventoryContainer.detectAndSendChanges();}).get();stage=2;frames=0;return false;}
        if(stage==2){if(mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getItem()!=ModItems.REDSTONE_TRANSMITTER||!ItemRedstoneTransmitter.channels(mc.player.getHeldItemMainhand()).equals(com.vandorlabs.redstone.ChannelList.of(VehicleArmCouplerFixture.CHANNEL)))return false;mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);need(p.inventory.currentItem==0&&p.getHeldItemMainhand().getItem()==ModItems.REDSTONE_TRANSMITTER&&ItemRedstoneTransmitter.channels(p.getHeldItemMainhand()).equals(com.vandorlabs.redstone.ChannelList.of(VehicleArmCouplerFixture.CHANNEL)),"exact server mode/slot/channel tool before real cancellation input");}).get();mc.player.setSneaking(false);mc.player.rotationPitch=-90;mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.Rotation(mc.player.rotationYaw,-90,false));mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);clicked=true;stage=3;frames=0;return false;}
        boolean resumed=mc.getIntegratedServer().callFromMainThread(()->{EntityGroundVehicle c=server(mc,id);GearTileEntity gear=(GearTileEntity)c.view.getTileEntity(root);if(VehicleCouplings.preparingArmRelease(c)||!gear.target()||gear.progress()<=frozen)return false;need(!c.transferInProgress&&!gear.motionBlocked()&&VehicleCouplingTransfer.pendingCount((net.minecraft.world.WorldServer)c.world)==0,"cancellation retains usable owner without ownership publication");int owners=0;for(net.minecraft.entity.Entity entity:c.world.loadedEntityList)if(entity instanceof EntityGroundVehicle&&!entity.isDead&&((EntityGroundVehicle)entity).structure!=null)owners++;need(owners==1,"no duplicate owner after changed input");return true;}).get();if(!resumed)return false;EntityGroundVehicle c=client(mc,id);GearTileEntity gear=c==null||c.view==null?null:(GearTileEntity)c.view.getTileEntity(root);if(gear==null||gear.motionBlocked()||gear.progress()<=frozen)return false;need(clicked,"actual cancellation packet was sent");CouplerWireProbe.dump();System.out.println("[vandorlabs][reprolab] carried-arm-coupler-release-live PASS pendingCancellation checks="+checks+" actual16tickPause/clientClock/authorityUntouched/SurvivalTransmitter/changeRefusal/oneOwner/resume");return true;
    }
    private VehicleArmReleasePendingChecks(){}
}
