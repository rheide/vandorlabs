package com.vandorlabs.turntable;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.*;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

@Mod.EventBusSubscriber(modid="vandorlabs")
public final class TurntableEvents {
    private static final ThreadLocal<EntityTurntable> EXCLUDED=new ThreadLocal<>();
    private static final Map<World,Set<EntityTurntable>> RETURNS=new IdentityHashMap<>();
    /** Raw transfers must not remove entities from the list World is currently ticking. */
    public static void restoreAfterTick(EntityTurntable entity){if(!entity.world.isRemote)RETURNS.computeIfAbsent(entity.world,w->Collections.newSetFromMap(new IdentityHashMap<>())).add(entity);}
    public static void finishTransfers(WorldServer world){
        Set<EntityTurntable> pending=RETURNS.get(world);if(pending==null||!com.vandorlabs.vehicle.TransferCoordinator.acquire(world,pending))return;
        RETURNS.remove(world);
        try{for(EntityTurntable entity:pending)if(!entity.isDead)try{
            // A chunk unload is not controller removal. Defer until ownership can be checked.
            if(!world.isBlockLoaded(entity.controller)||entity.owner()!=null)continue;
            TurntableTransfer.restore(world,entity,null);
        }catch(Exception blocked){
            try{TurntableTransfer.deferOrphan(world,entity);}catch(Exception checkpoint){com.vandorlabs.VandorLabs.logger.error("Cannot checkpoint orphaned turntable contents",checkpoint);}
        }}finally{com.vandorlabs.vehicle.TransferCoordinator.release(world,pending);}
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){RETURNS.remove(event.getWorld());if(event.getWorld() instanceof WorldServer)TurntableTransactions.unload((WorldServer)event.getWorld());}
    public static List<AxisAlignedBB> foreignObstacles(World world,EntityTurntable display,Entity rider,AxisAlignedBB box){EXCLUDED.set(display);try{return world.getCollisionBoxes(rider,box);}finally{EXCLUDED.remove();}}
    @SubscribeEvent public static void collisions(GetCollisionBoxesEvent event){
        if(event.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        for(EntityTurntable entity:event.getWorld().getEntitiesWithinAABB(EntityTurntable.class,event.getAabb().grow(32)))if(!entity.isDead&&entity!=event.getEntity()&&entity!=EXCLUDED.get())for(AxisAlignedBB box:entity.collisionBoxes(event.getAabb()))if(box.intersects(event.getAabb()))event.getCollisionBoxesList().add(box);
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event){if(!event.world.isRemote){if(event.phase==TickEvent.Phase.END){TurntableTransactions.tick((WorldServer)event.world);finishTransfers((WorldServer)event.world);}else if(event.world.getTotalWorldTime()%20==0)TurntableTransfer.recover((WorldServer)event.world);}}
}
