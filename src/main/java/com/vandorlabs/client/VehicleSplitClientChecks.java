package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

/** A real client interaction packet must produce two independently usable snapshots. */
final class VehicleSplitClientChecks {
    private static UUID parent;private static int stage,ticks,total;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("split client stage="+stage+" ticks="+ticks+": "+why);}
    private static EntityGroundVehicle entity(Minecraft mc,UUID id){for(Entity e:mc.world.loadedEntityList)if(e instanceof EntityGroundVehicle&&!e.isDead&&e.getUniqueID().equals(id))return (EntityGroundVehicle)e;return null;}
    static boolean tick(Minecraft mc)throws Exception{
        mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;require(++ticks<1200,"client split timed out: mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" flying="+mc.player.capabilities.isFlying+" status="+mc.getIntegratedServer().callFromMainThread(VehicleSplitChecks::liveStatus).get());
        EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        if(stage==0){parent=mc.getIntegratedServer().callFromMainThread(()->VehicleSplitChecks.beginLive(actor.getServerWorld(),actor)).get();stage=1;ticks=0;return false;}
        if(stage==1){EntityGroundVehicle craft=entity(mc,parent);if(craft==null||craft.structure==null||!craft.hover||mc.player.inventory.currentItem!=0||!mc.player.getHeldItemMainhand().isEmpty()||mc.playerController.getCurrentGameType()!=net.minecraft.world.GameType.SURVIVAL||!mc.player.capabilities.isFlying)return false;
            VehicleInteraction.Target hit=VehicleInteraction.pointed(mc.player);if(hit==null||hit.craft!=craft||hit.hit.action!=VehicleSelection.Action.COUPLER)return false;total=craft.structure.cells.size();VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.INTERACT);packet.entity=craft.getEntityId();packet.token=craft.getUniqueID();packet.origin=hit.hit.cell.pos;com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(packet);stage=2;ticks=0;return false;
        }
        List<UUID> ids=mc.getIntegratedServer().callFromMainThread(VehicleSplitChecks::liveOutputs).get();if(ids.size()!=2)return false;int cells=0;for(UUID id:ids){EntityGroundVehicle craft=entity(mc,id);if(craft==null||craft.structure==null||!craft.hover)return false;cells+=craft.structure.cells.size();}if(cells!=total)return false;for(UUID id:ids){EntityGroundVehicle craft=entity(mc,id);require(craft.structure.hasDedicatedController()&&craft.articulation.jointCount()==0,"each client snapshot is a complete independent vehicle");}
        mc.getIntegratedServer().addScheduledTask(VehicleSplitChecks::endLive).get();System.out.println("[vandorlabs][reprolab] vehicle-split-client PASS real Survival client packet fresh UUID two Hover snapshots exact total blocks independent controls");return true;
    }
    private VehicleSplitClientChecks(){}
}
