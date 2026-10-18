package com.vandorlabs.turntable;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.*;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.List;

@Mod.EventBusSubscriber(modid="vandorlabs")
public final class TurntableEvents {
    private static final ThreadLocal<EntityTurntable> EXCLUDED=new ThreadLocal<>();
    public static List<AxisAlignedBB> foreignObstacles(World world,EntityTurntable display,Entity rider,AxisAlignedBB box){EXCLUDED.set(display);try{return world.getCollisionBoxes(rider,box);}finally{EXCLUDED.remove();}}
    @SubscribeEvent public static void collisions(GetCollisionBoxesEvent event){
        if(event.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        for(EntityTurntable entity:event.getWorld().getEntitiesWithinAABB(EntityTurntable.class,event.getAabb().grow(32)))if(!entity.isDead&&entity!=event.getEntity()&&entity!=EXCLUDED.get())for(AxisAlignedBB box:entity.collisionBoxes())if(box.intersects(event.getAabb()))event.getCollisionBoxesList().add(box);
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event){if(!event.world.isRemote&&event.phase==TickEvent.Phase.START&&event.world.getTotalWorldTime()%20==0)TurntableTransfer.recover((WorldServer)event.world);}
}
