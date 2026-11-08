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
    private static final int MAX_VEHICLES=16,MAX_PER_VEHICLE=32,MAX_AGE=200;
    private static final long MAX_BYTES=2L*1024*1024;
    private static final class Pending {
        final MessageGearMotion packet;final long since,bytes;
        Pending(MessageGearMotion packet,long since){this.packet=packet.copyForClientQueue();this.since=since;bytes=1024L+packet.poses.size()*160L;}
    }
    private static final Map<UUID,ArrayDeque<Pending>> deferred=new LinkedHashMap<>();
    private static long deferredBytes,queueTicks;
    public static void clear(){deferred.clear();deferredBytes=0;queueTicks=0;}
    private static boolean expired(Pending item){return queueTicks-item.since>MAX_AGE;}
    private static com.vandorlabs.gear.core.GearMotionScope.Decision decision(EntityGroundVehicle craft,MessageGearMotion packet){
        return com.vandorlabs.gear.core.GearMotionScope.classify(packet.associationGeneration,packet.tick,craft.couplerAssociationGeneration(),craft.couplerAssociationScopeTick(),craft.installedCouplerSourceTick(),craft.couplerScopeReady(),VehicleClient.snapshotPending(packet.craft));
    }
    private static void enqueue(World world,MessageGearMotion packet){
        if(deferred.size()>=MAX_VEHICLES&&!deferred.containsKey(packet.craft))return;
        ArrayDeque<Pending> queue=deferred.computeIfAbsent(packet.craft,k->new ArrayDeque<>());
        if(queue.size()==MAX_PER_VEHICLE)deferredBytes-=queue.removeFirst().bytes;
        Pending next=new Pending(packet,queueTicks);
        while(deferredBytes+next.bytes>MAX_BYTES&&!deferred.isEmpty()){
            Iterator<Map.Entry<UUID,ArrayDeque<Pending>>> iterator=deferred.entrySet().iterator();Map.Entry<UUID,ArrayDeque<Pending>> oldest=iterator.next();ArrayDeque<Pending> old=oldest.getValue();
            if(!old.isEmpty())deferredBytes-=old.removeFirst().bytes;
            if(old.isEmpty())iterator.remove();
        }
        queue=deferred.computeIfAbsent(packet.craft,k->new ArrayDeque<>());queue.addLast(next);deferredBytes+=next.bytes;
    }
    public static void cleanup(World world){
        if(world==null){clear();return;}
        if(deferred.isEmpty())return;
        if(!Minecraft.getMinecraft().isGamePaused())queueTicks++;
        Iterator<Map.Entry<UUID,ArrayDeque<Pending>>> entries=deferred.entrySet().iterator();
        while(entries.hasNext()){
            Map.Entry<UUID,ArrayDeque<Pending>> entry=entries.next();ArrayDeque<Pending> queue=entry.getValue();
            net.minecraft.entity.Entity raw=queue.isEmpty()?null:world.getEntityByID(queue.peekFirst().packet.entity);
            if(!(raw instanceof EntityGroundVehicle)||!raw.getUniqueID().equals(entry.getKey())){for(Pending item:queue)deferredBytes-=item.bytes;entries.remove();continue;}
            drain(world,(EntityGroundVehicle)raw,queue);if(queue.isEmpty())entries.remove();
        }
    }
    private static void drain(World world,EntityGroundVehicle craft,ArrayDeque<Pending> queue){
        Iterator<Pending> iterator=queue.iterator();
        while(iterator.hasNext()){
            Pending item=iterator.next();com.vandorlabs.gear.core.GearMotionScope.Decision state=item.packet.dimension==world.provider.getDimension()?decision(craft,item.packet):com.vandorlabs.gear.core.GearMotionScope.Decision.DROP;
            boolean old=expired(item)&&!Objects.equals(item.packet.associationGeneration,craft.couplerAssociationGeneration())
                    &&!VehicleClient.pendingSourceScope(item.packet.craft,item.packet.associationGeneration);
            if(!old&&state==com.vandorlabs.gear.core.GearMotionScope.Decision.WAIT)continue;
            iterator.remove();deferredBytes-=item.bytes;
            if(!old&&state==com.vandorlabs.gear.core.GearMotionScope.Decision.APPLY&&!craft.transferInProgress&&craft.acceptsCouplerMotion(item.packet.associationGeneration,item.packet.tick))applyPacket(item.packet,craft,craft.view);
        }
    }
    /** Repeated installation does not reset queue age or grant scope to a queued packet. */
    public static void installed(EntityGroundVehicle craft){ArrayDeque<Pending> queue=deferred.get(craft.getUniqueID());if(queue!=null){drain(craft.world,craft,queue);if(queue.isEmpty())deferred.remove(craft.getUniqueID());}}
    public static void receive(MessageGearMotion packet){
        Minecraft mc=Minecraft.getMinecraft();mc.addScheduledTask(()->{
            if(mc.world==null||mc.world.provider.getDimension()!=packet.dimension||packet.poses.isEmpty()||packet.poses.size()>64)return;
            EntityGroundVehicle craft=null;World view=mc.world;
            if(packet.entity>=0){net.minecraft.entity.Entity raw=mc.world.getEntityByID(packet.entity);if(!(raw instanceof EntityGroundVehicle)||!raw.getUniqueID().equals(packet.craft))return;
                craft=(EntityGroundVehicle)raw;com.vandorlabs.gear.core.GearMotionScope.Decision state=decision(craft,packet);
                if(state==com.vandorlabs.gear.core.GearMotionScope.Decision.WAIT){enqueue(mc.world,packet);return;}
                if(state!=com.vandorlabs.gear.core.GearMotionScope.Decision.APPLY||craft.transferInProgress||!craft.acceptsCouplerMotion(packet.associationGeneration,packet.tick))return;view=craft.view;
            }else if(packet.associationGeneration!=null)return;
            applyPacket(packet,craft,view);
        });
    }
    private static void applyPacket(MessageGearMotion packet,EntityGroundVehicle craft,World view){
            Map<net.minecraft.util.math.BlockPos,Double> desired=new LinkedHashMap<>();
            for(MessageGearMotion.Pose pose:packet.poses){if(!view.isBlockLoaded(pose.pos))continue;TileEntity raw=view.getTileEntity(pose.pos);
                if(raw instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)raw;
                    if(pose.taxiPhase>=0&&(gear.assembly()==null||gear.assembly().columnHeight()!=pose.taxiHeight))continue;
                    if(gear.acceptMotion(pose.revision,packet.tick,pose.target,pose.blocked)){
                        if(pose.taxiPhase==-1)gear.taxiState(null);
                        else if(pose.taxiPhase>=0){net.minecraft.nbt.NBTTagCompound state=new net.minecraft.nbt.NBTTagCompound();state.setInteger("Schema",1);state.setInteger("Phase",pose.taxiPhase);state.setInteger("Height",pose.taxiHeight);state.setInteger("Amount",pose.taxiAmount);state.setDouble("Level",pose.taxiLevel);state.setBoolean("Expand",pose.target);try{gear.taxiState(state);}catch(IllegalArgumentException invalid){continue;}}

                        if(craft==null)gear.correctProgress(pose.progress);else desired.put(pose.pos,pose.progress);
                    }
                }
            }
            if(craft!=null&&!desired.isEmpty())ModularGearVehicles.correct(craft,desired,packet.associationGeneration,packet.tick);
    }
}
