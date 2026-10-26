package com.vandorlabs.motion;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.*;
import com.vandorlabs.network.*;

/** Shared authoritative support transport for ramps, belts and arm platforms. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs")
public final class PlatformRiders {
    private static final java.util.Map<Entity,Support> RECENT_SERVER=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    // Entity.equals uses its numeric ID, shared by the logical client and server.
    private static final java.util.Map<Entity,Support> RECENT_CLIENT=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static java.util.Map<Entity,Support> recent(Entity e){return e.world.isRemote?RECENT_CLIENT:RECENT_SERVER;}
    private static final class Support {final long tick;final int dimension;final double x,z,height;Support(Entity e){tick=e.world.getTotalWorldTime();dimension=e.dimension;x=e.posX;z=e.posZ;height=e.getEntityBoundingBox().minY;}}
    private static final ThreadLocal<java.util.Set<Entity>> CO_MOVING=new ThreadLocal<>();
    public static final class CollisionScope implements AutoCloseable {
        private final java.util.Set<Entity> previous;
        public CollisionScope(java.util.Set<Entity> members){previous=CO_MOVING.get();CO_MOVING.set(members);}
        public void close(){if(previous==null)CO_MOVING.remove();else CO_MOVING.set(previous);}
    }
    public static boolean coMoving(Entity entity){java.util.Set<Entity> group=CO_MOVING.get();return group!=null&&group.contains(entity);}
    public static void supported(Entity entity){recent(entity).put(entity,new Support(entity));}
    public static String diagnostic(Entity e){Support s=recent(e).get(e);return s==null?"none":"height="+s.height+" age="+(e.world.getTotalWorldTime()-s.tick)+" eligible="+eligible(e);}
    public static boolean vehicle(Entity entity){return entity instanceof com.vandorlabs.vehicle.EntityGroundVehicle||entity instanceof net.minecraft.entity.item.EntityBoat||entity instanceof net.minecraft.entity.item.EntityMinecart;}
    public static boolean eligible(Entity entity){return !entity.isDead&&!entity.noClip&&!entity.isRiding()&&entity.motionY<=.1&&(!(entity instanceof EntityPlayer)||!((EntityPlayer)entity).capabilities.isFlying);}
    public static void move(Entity entity,double dx,double dy,double dz){
        double feet=entity.getEntityBoundingBox().minY;
        MessagePlatformMotion packet=entity instanceof EntityPlayerMP?new MessagePlatformMotion((EntityPlayer)entity,feet,feet+dy).withHorizontal(dx,dz):null;
        entity.setPosition(entity.posX+dx,entity.posY+dy,entity.posZ+dz);entity.onGround=true;entity.fallDistance=0;entity.motionY=0;supported(entity);
        if(packet!=null)PacketHandler.INSTANCE.sendTo(packet,(EntityPlayerMP)entity);
    }
    /** A rising support may be slightly ahead of a rider's last movement packet. */
    public static net.minecraft.util.math.AxisAlignedBB supportCollision(net.minecraft.util.math.AxisAlignedBB box,Entity entity,boolean moving){
        if(!moving||!(entity instanceof EntityPlayer||entity instanceof net.minecraft.entity.item.EntityItem)||!eligible(entity))return box;
        double feet=entity.getEntityBoundingBox().minY;
        if(box.maxY>feet&&box.maxY-feet<=.5){if(box.minY>=feet)return null;return new net.minecraft.util.math.AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,feet,box.maxZ);}
        return box;
    }
    public static boolean clear(Entity entity,net.minecraft.util.math.Vec3d delta,java.util.List<net.minecraft.util.math.AxisAlignedBB> obstacles){
        net.minecraft.util.math.AxisAlignedBB body=entity.getEntityBoundingBox();double x=delta.x,y=delta.y,z=delta.z;
        for(net.minecraft.util.math.AxisAlignedBB obstacle:obstacles)y=obstacle.calculateYOffset(body,y);body=body.offset(0,y,0);
        for(net.minecraft.util.math.AxisAlignedBB obstacle:obstacles)x=obstacle.calculateXOffset(body,x);body=body.offset(x,0,0);
        for(net.minecraft.util.math.AxisAlignedBB obstacle:obstacles)z=obstacle.calculateZOffset(body,z);
        return Math.abs(x-delta.x)<1e-6&&Math.abs(y-delta.y)<1e-6&&Math.abs(z-delta.z)<1e-6;
    }
    /** Keep the final support correction valid when its cells return to ordinary block mode. */
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public static void recentCollision(net.minecraftforge.event.world.GetCollisionBoxesEvent event){
        Entity entity=event.getEntity();if(entity==null||!eligible(entity))return;Support support=recent(entity).get(entity);if(support==null||support.dimension!=entity.dimension)return;
        long age=entity.world.getTotalWorldTime()-support.tick;if(age<0||age>20||Math.abs(entity.posX-support.x)>.75||Math.abs(entity.posZ-support.z)>.75)return;
        java.util.ListIterator<net.minecraft.util.math.AxisAlignedBB> boxes=event.getCollisionBoxesList().listIterator();
        while(boxes.hasNext()){net.minecraft.util.math.AxisAlignedBB box=boxes.next();if(Math.abs(box.maxY-support.height)>1e-7||support.x<box.minX-.3||support.x>box.maxX+.3||support.z<box.minZ-.3||support.z>box.maxZ+.3)continue;net.minecraft.util.math.AxisAlignedBB clipped=supportCollision(box,entity,true);if(clipped==null)boxes.remove();else if(clipped!=box)boxes.set(clipped);}
    }
    private PlatformRiders(){}
}
