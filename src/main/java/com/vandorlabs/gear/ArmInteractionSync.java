package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.world.GameType;

/** A matching old client position alone cannot acknowledge a newly repeated teleport. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
final class ArmInteractionSync {
    private static final java.lang.reflect.Field TELEPORT=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(NetHandlerPlayServer.class,"targetPos","field_184362_y");
    private static volatile String status="awaiting server state";
    static String status(){return status;}
    static boolean acknowledged(Minecraft mc)throws Exception{
        GameType mode=mc.playerController.getCurrentGameType();int slot=mc.player.inventory.currentItem,count=mc.player.getHeldItemMainhand().getCount();Item item=mc.player.getHeldItemMainhand().getItem();double x=mc.player.posX,y=mc.player.posY,z=mc.player.posZ;
        return mc.getIntegratedServer().callFromMainThread(()->{EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);boolean pending=TELEPORT.get(actor.connection)!=null;status="teleportPending="+pending+" mode="+actor.interactionManager.getGameType()+" slot="+actor.inventory.currentItem+" held="+actor.getHeldItemMainhand()+" position="+actor.getPositionVector();return !pending&&actor.interactionManager.getGameType()==mode&&actor.inventory.currentItem==slot&&actor.getHeldItemMainhand().getItem()==item&&actor.getHeldItemMainhand().getCount()==count&&actor.getDistanceSq(x,y,z)<.0001;}).get();
    }
    private ArmInteractionSync(){}
}
