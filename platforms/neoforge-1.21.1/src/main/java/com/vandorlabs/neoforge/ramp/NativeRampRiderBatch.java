package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;

/** All authoritative moves precede every external network callback. No block ownership authority. */
final class NativeRampRiderBatch {
    record Step(Entity entity,Vec3 delta,boolean beltItem,double supportDy){Step(Entity entity,Vec3 delta,boolean beltItem){this(entity,delta,beltItem,delta.y);}}
    private record Before(Entity entity,Vec3 position,AABB body,Vec3 velocity,float fall,boolean grounded,boolean noPhysics,boolean impulse,NativeRampRiders.SupportState support){}
    private record Delivery(ServerPlayer player,NativeRampMotionPayload packet){}
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    static int commit(ServerLevel level,List<Step> steps,NativeRampRiders.Sync sync){
        NativeRampRiders.requireThread(level);if(steps.size()>8192)throw new IllegalArgumentException("Ramp rider batch count");var unique=Collections.newSetFromMap(new IdentityHashMap<Entity,Boolean>());var before=new ArrayList<Before>(steps.size());var packets=new ArrayList<Delivery>();
        for(var step:steps){var entity=step.entity;if(entity==null||entity.level()!=level||!unique.add(entity)||!Double.isFinite(step.delta.x)||!Double.isFinite(step.delta.y)||!Double.isFinite(step.delta.z)||!Double.isFinite(step.supportDy))throw new IllegalArgumentException("Ramp rider batch identity or delta");if(entity instanceof ServerPlayer)Objects.requireNonNull(sync,"Ramp player correction sender");before.add(new Before(entity,entity.position(),entity.getBoundingBox(),entity.getDeltaMovement(),entity.fallDistance,entity.onGround(),entity.noPhysics,entity.hasImpulse,NativeRampRiders.captureSupport(entity)));}
        NativeRampRiders.Sync collect=(player,packet)->packets.add(new Delivery(player,packet));
        try{for(var step:steps){var delta=step.delta;NativeRampRiders.move(step.entity,delta.x,delta.y,delta.z,step.supportDy,collect);if(step.beltItem)NativeRampRiders.beltItemState(step.entity);}}
        catch(RuntimeException failure){
            // No player correction has been dispatched yet. Restore the common authoritative state.
            for(var old:before)try{var entity=old.entity;if(entity.level()!=level)throw new IllegalStateException("Rider changed world during physical transport");entity.setPosRaw(old.position.x,old.position.y,old.position.z);entity.setBoundingBox(old.body);entity.setDeltaMovement(old.velocity);entity.fallDistance=old.fall;entity.setOnGround(old.grounded);entity.noPhysics=old.noPhysics;entity.hasImpulse=old.impulse;NativeRampRiders.restoreSupport(entity,old.support);}catch(RuntimeException rollback){failure.addSuppressed(rollback);}
            throw failure;
        }
        int failures=0;
        for(var delivery:packets)try{sync.send(delivery.player,delivery.packet);}catch(RuntimeException failure){
            failures++;LOG.warn("Ramp rider correction failed for {}; authoritative endpoint retained",delivery.player.getUUID(),failure);
            // Socket delivery is not a world transaction. Attempt all remaining players, and use
            // vanilla's acknowledged absolute correction for this connection without reverting blocks.
            try{var player=delivery.player;if(player.connection!=null)player.connection.teleport(player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());}catch(RuntimeException resync){LOG.warn("Ramp rider absolute resynchronization failed for {}",delivery.player.getUUID(),resync);}
        }
        for(var step:steps)if(step.beltItem)try{NativeRampRiders.beltItemTracking(step.entity);}catch(RuntimeException failure){failures++;LOG.warn("Ramp belt tracking failed for {}; authoritative endpoint retained",step.entity.getUUID(),failure);}
        return failures;
    }
    private NativeRampRiderBatch(){}
}
