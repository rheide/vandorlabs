package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Explicit disposable-save acceptance of manual closure while a shared Moat is still deployed. */
final class ExistingSharedCanopyChecks {
    private static BlockPos owner,rampPos,source;private static int stage,ticks;private static long since;private static Watch watch;
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("existing shared canopy: "+text);}
    private static BlockPos coordinate(String key){String[] parts=System.getProperty("vandorlabs."+key,"").split(",");require(parts.length==3,"set "+key+"=x,y,z");return new BlockPos(Integer.parseInt(parts[0].trim()),Integer.parseInt(parts[1].trim()),Integer.parseInt(parts[2].trim()));}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static TileEntityCanopy canopy(WorldServer world){return TileEntityCanopy.at(world,owner);}
    private static TileEntityRampController ramp(WorldServer world){return (TileEntityRampController)world.getTileEntity(rampPos);}
    public static final class Watch {final WorldServer world;boolean armed;int moving;String failure;Watch(WorldServer world){this.world=world;}
        @SubscribeEvent public void tick(TickEvent.WorldTickEvent event){if(!armed||event.world!=world||event.phase!=TickEvent.Phase.END)return;TileEntityRampController ramp=ramp(world);TileEntityCanopy canopy=canopy(world);if(ramp.isMoving())moving++;if((ramp.isMoving()||ramp.isOpen())&&canopy.progress!=1)failure="canopy left open endpoint before ramp retraction completed: "+canopy.progress+" moving="+ramp.isMoving()+" open="+ramp.isOpen();}}
    private static void click(Minecraft mc){mc.player.setSneaking(false);mc.player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);require(mc.playerController.processRightClickBlock(mc.player,mc.world,owner,EnumFacing.UP,new Vec3d(owner).addVector(.5,.5,.5),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"real native canopy click accepted");}
    static boolean tick(Minecraft mc,int hold)throws Exception{if(stage==7)return true;require(++ticks<1200,"timeout stage="+stage);if(watch!=null)require(watch.failure==null,watch.failure);
        if(stage==0){if(hold<40)return false;owner=coordinate("sharedCanopyPos");rampPos=coordinate("sharedRampPos");source=coordinate("sharedSourcePos");mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);world.getChunkFromBlockCoords(owner);world.getChunkFromBlockCoords(rampPos);world.getChunkFromBlockCoords(source);require(canopy(world)!=null&&ramp(world)!=null,"selected real pair loaded");require(world.getTileEntity(source) instanceof TileEntityRedstoneChannel,"selected real latch loaded");require(((TileEntityRedstoneChannel)world.getTileEntity(source)).isLocalOn(),"real shared latch initially HIGH");EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());actor.dismountRidingEntity();actor.setSneaking(false);actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.setPositionAndUpdate(owner.getX()+.5,owner.getY()+1,owner.getZ()-3);watch=new Watch(world);net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(watch);System.out.println("[vandorlabs][reprolab] existing-shared-canopy BEFORE "+canopy(world).writeToNBT(new NBTTagCompound()));// Reproduce the old unsafe saved endpoint only in this explicitly enabled disposable copy.
                TileEntityCanopy loaded=canopy(world);NBTTagCompound unsafe=loaded.writeToNBT(new NBTTagCompound());unsafe.setBoolean("Open",false);unsafe.setFloat("Progress",0);unsafe.setBoolean("CanopyWaitingForReservations",false);loaded.readFromNBT(unsafe);loaded.onLoad();loaded.sync();
                System.out.println("[vandorlabs][reprolab] existing-shared-canopy unsafe-closed-reload imposed; actual ramp/source geometry unchanged");}).get();stage=1;return false;}
        TileEntityCanopy client=TileEntityCanopy.at(mc.world,owner);if(client==null)return false;
        if(stage==1&&client.progress==1&&client.waitingForReservations&&!client.targetOpen){click(mc);stage=2;return false;}
        if(stage==2&&client.progress==1&&client.targetOpen){click(mc);mc.getIntegratedServer().addScheduledTask(()->{watch.armed=true;since=world(mc).getTotalWorldTime();}).get();stage=3;return false;}
        if(stage==3){boolean[] ready={false};mc.getIntegratedServer().addScheduledTask(()->{ready[0]=world(mc).getTotalWorldTime()-since>=30;}).get();if(!ready[0])return false;mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);require(canopy(world).progress==1&&canopy(world).waitingForReservations&&!canopy(world).targetOpen,"manual HIGH close waits for deployed Moat");TileEntityCanopy canopy=canopy(world);canopy.readFromNBT(canopy.writeToNBT(new NBTTagCompound()));canopy.onLoad();((TileEntityRedstoneChannel)world.getTileEntity(source)).setLocalOn(false);}).get();stage=4;return false;}
        if(stage==4&&client.progress==0&&!client.targetOpen&&!client.waitingForReservations){mc.getIntegratedServer().addScheduledTask(()->{require(!ramp(world(mc)).isMoving()&&!ramp(world(mc)).isOpen(),"real Moat reached retract endpoint before canopy closed");require(watch.moving>0,"normal world observed actual retraction");require(watch.failure==null,watch.failure);since=world(mc).getTotalWorldTime();}).get();stage=5;return false;}
        if(stage==5){boolean[] ready={false};mc.getIntegratedServer().addScheduledTask(()->{ready[0]=world(mc).getTotalWorldTime()-since>=30;}).get();if(!ready[0])return false;mc.getIntegratedServer().addScheduledTask(()->{require(canopy(world(mc)).progress==0&&!canopy(world(mc)).waitingForReservations,"closed endpoint stays settled");require(watch.failure==null,watch.failure);watch.armed=false;net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().unregister(watch);System.out.println("[vandorlabs][reprolab] existing-shared-canopy AFTER "+canopy(world(mc)).writeToNBT(new NBTTagCompound()));}).get();stage=7;System.out.println("[vandorlabs][reprolab] existing-shared-canopy PASS exact-copy unsafe-closed-load-reopens manual-click-HIGH pending-reload real-source-OFF every-normal-server-tick waits-full-retraction stable-close");return true;}
        return false;
    }
}
