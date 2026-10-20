package com.vandorlabs.client;

import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.network.MessageGearMotion;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class GearMotionClient {
    private GearMotionClient(){}
    private static final Map<UUID,ArrayDeque<MessageGearMotion>> deferred=new HashMap<>();
    public static void clear(){deferred.clear();}
    public static void cleanup(World world){deferred.entrySet().removeIf(e->{net.minecraft.entity.Entity raw=world.getEntityByID(e.getValue().peek().entity);return raw==null||!raw.getUniqueID().equals(e.getKey());});}
    public static void installed(EntityGroundVehicle craft){ArrayDeque<MessageGearMotion> queue=deferred.remove(craft.getUniqueID());if(queue!=null)for(MessageGearMotion packet:queue)receive(packet);}
    public static void receive(MessageGearMotion packet){
        Minecraft mc=Minecraft.getMinecraft();mc.addScheduledTask(()->{
            if(mc.world==null||mc.world.provider.getDimension()!=packet.dimension)return;
            EntityGroundVehicle craft=null;World view=mc.world;
            if(packet.entity>=0){net.minecraft.entity.Entity raw=mc.world.getEntityByID(packet.entity);if(!(raw instanceof EntityGroundVehicle)||!raw.getUniqueID().equals(packet.craft))return;
                craft=(EntityGroundVehicle)raw;if(craft.view==null||VehicleClient.snapshotPending(packet.craft)){if(deferred.size()<16||deferred.containsKey(packet.craft)){ArrayDeque<MessageGearMotion> queue=deferred.computeIfAbsent(packet.craft,k->new ArrayDeque<>());if(queue.size()==32)queue.removeFirst();queue.addLast(packet);}return;}if(craft.transferInProgress)return;view=craft.view;}
            Map<net.minecraft.util.math.BlockPos,Double> desired=new LinkedHashMap<>();
            for(MessageGearMotion.Pose pose:packet.poses){if(!view.isBlockLoaded(pose.pos))continue;TileEntity raw=view.getTileEntity(pose.pos);
                if(raw instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)raw;
                    if(gear.acceptMotion(pose.revision,packet.tick,pose.target,pose.blocked)){
                        if(craft==null)gear.correctProgress(pose.progress);else desired.put(pose.pos,pose.progress);
                    }
                }
            }
            if(craft!=null&&!desired.isEmpty())ModularGearVehicles.correct(craft,desired);
        });
    }
}
