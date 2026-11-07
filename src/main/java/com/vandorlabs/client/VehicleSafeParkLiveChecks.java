package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.io.File;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Exact-JAR Configurizer/GUI/network and asynchronous durable parking fixture. */
public final class VehicleSafeParkLiveChecks {
    private static final BlockPos BASE=new BlockPos(200,101,200);
    private static int stage,ticks,checks;private static UUID id;private static EntityGroundVehicle original;private static boolean diskWait;
    private static java.util.concurrent.CountDownLatch diskRelease;private static java.util.concurrent.Future<?> diskBlocker;private static int heldTicks;
    private static float lookYaw;
    private static boolean done,retry;private static NBTTagCompound before;private static final Audit AUDIT=new Audit();
    public static final class Audit {int cancellations;@net.minecraftforge.fml.common.eventhandler.SubscribeEvent public void place(net.minecraftforge.event.world.BlockEvent.MultiPlaceEvent event){if(cancellations==0&&original!=null&&original.transferInProgress){event.setCanceled(true);cancellations++;}}}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static void need(boolean ok,String message){checks++;if(!ok)throw new IllegalStateException("Safe Park live: "+message+" stage="+stage+" ticks="+ticks);}
    private static Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static VehicleNetwork.Packet header(Minecraft mc)throws Exception{return (VehicleNetwork.Packet)field(field(mc.currentScreen,"data"),"header");}
    private static List<GuiButton> buttons(Minecraft mc){return net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,mc.currentScreen,"buttonList","field_146292_n");}
    private static EntityGroundVehicle craft(Minecraft mc){return (EntityGroundVehicle)world(mc).getEntityFromUuid(id);}
    private static void setup(Minecraft mc)throws Exception {
        WorldServer world=world(mc);VehicleSafeParkAlignmentChecks.run(world);EntityPlayerMP player=actor(mc);
        original=VehicleSafeParkFixture.create(world,player,BASE);id=original.getUniqueID();
        aim(mc,original.structure.controller().pos);
        VehicleNetwork.state(original);
    }
    private static void aim(Minecraft mc,BlockPos selected){
        Vec3d target=original.toWorld(new Vec3d(selected).addVector(.5,.5,.5)),eye=original.toWorld(new Vec3d(selected).addVector(.5,.5,-2.5));
        lookYaw=(float)Math.toDegrees(Math.atan2(eye.x-target.x,target.z-eye.z));EntityPlayerMP player=actor(mc);player.setPositionAndRotation(eye.x,eye.y-player.getEyeHeight(),eye.z,lookYaw,0);player.setPositionAndUpdate(player.posX,player.posY,player.posZ);
    }
    public static boolean tick(Minecraft mc,File output)throws Exception {
        if(done)return true;mc.gameSettings.pauseOnLostFocus=false;need(++ticks<1600,"timeout");
        if(stage==0){mc.displayGuiScreen(null);mc.getIntegratedServer().addScheduledTask(()->{try{setup(mc);}catch(Exception e){throw new IllegalStateException(e);}}).get();stage=1;ticks=0;return false;}
        if(stage==1){
            if(ticks<30||mc.player.getHeldItemMainhand().getItem()!=ModItems.CONFIGURIZER)return false;
            mc.playerController.setGameType(GameType.CREATIVE);mc.player.inventory.currentItem=0;mc.player.setSneaking(false);mc.player.rotationYaw=lookYaw;mc.player.rotationPitch=0;
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketHeldItemChange(0));
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.PositionRotation(mc.player.posX,mc.player.posY,mc.player.posZ,lookYaw,0,false));
            mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);stage=2;ticks=0;return false;
        }
        if(stage==2){
            if(mc.currentScreen==null){need(ticks<100,"actual Configurizer opens vehicle dialog");return false;}
            need(mc.currentScreen.getClass().getName().endsWith("VehicleClient$Preview"),"vehicle dialog");need(buttons(mc).stream().anyMatch(b->b.id==33&&b.visible&&b.enabled),"Creative Configurizer Safe Park button");
            VehicleNetwork.Packet h=header(mc);need(h.forward==(retry?0:1),"real Configurizer opens selected controller/seat configuration path");
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=actor(mc);VehicleNetwork.Packet p=new VehicleNetwork.Packet(VehicleNetwork.SAFE_PARK);p.entity=h.entity;p.token=UUID.randomUUID();p.origin=h.forward==1?original.structure.controller().pos:BlockPos.ORIGIN;VehicleService.safePark(player,p);need(!original.transferInProgress&&!original.isDead,"forged token leaves craft untouched");
                p.token=h.token;player.setGameType(GameType.SURVIVAL);try{VehicleService.safePark(player,p);}finally{player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();}need(!original.transferInProgress&&!original.isDead,"Survival packet cannot use Creative recovery");
            }).get();stage=3;ticks=0;return false;
        }
        if(stage==3&&ticks>=15){
            VehicleTaxiRuntimeChecks.screenshot(mc,output,retry?"shot_vehicle_safe_park_retry_dialog.png":"shot_vehicle_safe_park_dialog.png");
            if(!retry)mc.getIntegratedServer().addScheduledTask(()->{before=original.structure.write();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(AUDIT);}).get();
            java.lang.reflect.Field ioField=VehicleTransfer.class.getDeclaredField("IO");ioField.setAccessible(true);java.util.concurrent.ExecutorService io=(java.util.concurrent.ExecutorService)ioField.get(null);
            java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1);diskRelease=new java.util.concurrent.CountDownLatch(1);
            diskBlocker=io.submit(()->{entered.countDown();try{diskRelease.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            need(entered.await(5,java.util.concurrent.TimeUnit.SECONDS),"existing disk worker accepts blocked-disk fixture");
            VehicleTaxiRuntimeChecks.button(mc,33);stage=4;ticks=0;return false;
        }
        if(stage==4){
            boolean finished=mc.getIntegratedServer().callFromMainThread(()->{
                try{java.lang.reflect.Method wait=VehicleTransfer.class.getDeclaredMethod("waitingForPersistence",WorldServer.class);wait.setAccessible(true);if((Boolean)wait.invoke(null,world(mc))){diskWait=true;need(!original.isDead&&original.transferInProgress,"pending disk operation retains held craft");need(original.rotationYaw==37,"pending disk operation retains heading");if(++heldTicks>=5)diskRelease.countDown();}
                    java.lang.reflect.Method busy=VehicleTransfer.class.getDeclaredMethod("busy",WorldServer.class);busy.setAccessible(true);
                    boolean active=(Boolean)busy.invoke(null,world(mc));
                    if(!original.isDead){need(original.rotationYaw==37,"search preserves original heading");
                        if(!retry&&AUDIT.cancellations==1&&!active){need(!original.transferInProgress&&!VehicleTransfer.pending(world(mc)),"refused publication retires rollback checkpoint and releases craft");need(original.structure.write().equals(before),"refused publication restores exact original snapshot");need(world(mc).getEntityFromUuid(id)==original,"refused publication retains exactly one live owner");return true;}
                        return false;
                    }
                    if(active)return false;
                    need(!VehicleTransfer.pending(world(mc)),"successful durable checkpoint retires journal");need(world(mc).getEntityFromUuid(id)==null,"moving owner retired");
                    int chests=0;for(BlockPos p:BlockPos.getAllInBox(BASE.add(-20,0,-20),BASE.add(25,15,20)))if(world(mc).getTileEntity(p) instanceof TileEntityChest){TileEntityChest chest=(TileEntityChest)world(mc).getTileEntity(p);need(chest.getStackInSlot(7).getItem()==Items.DIAMOND&&chest.getStackInSlot(7).getCount()==11,"cargo exact");need(chest.getName().equals("Safe Park cargo"),"cargo name exact");chests++;}
                    need(chests==1,"exactly one native cargo owner");need(diskWait,"real asynchronous journal preparation observed");return true;
                }catch(Exception e){throw new IllegalStateException(e);}
            }).get();if(!finished)return false;
            diskRelease.countDown();diskBlocker.get();
            if(!retry){mc.getIntegratedServer().addScheduledTask(()->{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(AUDIT);aim(mc,original.structure.seat);}).get();retry=true;heldTicks=0;mc.displayGuiScreen(null);stage=1;ticks=0;return false;}
            System.out.println("[vandorlabs][reprolab] vehicle-safe-park PASS checks="+checks+" actual-configurizer/controller+seat/gui/network forged/survival refusal original-heading blocked-disk async-journal canceled-publication exact-rollback retry cargo-single-owner; arm resolver pending, cold recovery is a separate focus");done=true;return true;
        }
        return false;
    }
}
