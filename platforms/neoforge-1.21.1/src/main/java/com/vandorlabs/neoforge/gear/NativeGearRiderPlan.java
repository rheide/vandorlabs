package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.ramp.NativeRampRiders;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.*;

/** Same-tick bounded native actor transport. Actors retain their native identities and inventories. */
final class NativeGearRiderPlan {
    private record Step(Entity actor,Vec3 position,AABB body,Vec3 velocity,Vec3 delta,double supportDy,boolean rider){}
    private record Original(Entity actor,Vec3 position,AABB body,Vec3 velocity,float fall,boolean grounded,boolean impulse){}
    private record Delivery(ServerPlayer actor,com.vandorlabs.neoforge.ramp.NativeRampMotionPayload packet){}
    private final ServerLevel world;private final long tick;private final List<Step> steps;
    private NativeGearRiderPlan(ServerLevel world,List<Step> steps){this.world=world;tick=world.getGameTime();this.steps=List.copyOf(steps);}
    static NativeGearRiderPlan prepare(NativeGearEntity owner,List<AABB> before,List<AABB> after){
        if(!(owner.getLevel() instanceof ServerLevel world)||!world.getServer().isSameThread()||before.size()!=after.size())throw new IllegalArgumentException("Arm rider world/geometry");
        if(before.isEmpty())return new NativeGearRiderPlan(world,List.of());var bounds=before.getFirst().minmax(after.getFirst());for(int n=1;n<before.size();n++)bounds=bounds.minmax(before.get(n)).minmax(after.get(n));bounds=bounds.inflate(.001,.5,.001);
        int x0=((int)Math.floor(bounds.minX))>>4,x1=((int)Math.floor(bounds.maxX))>>4,z0=((int)Math.floor(bounds.minZ))>>4,z1=((int)Math.floor(bounds.maxZ))>>4;if((long)(x1-x0+1)*(z1-z0+1)>256)return null;for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(world.getChunkSource().getChunkNow(x,z)==null)return null;
        var actors=new ArrayList<Entity>();world.getEntities(EntityTypeTest.forClass(Entity.class),bounds,actor->!actor.isSpectator()&&available(actor),actors,257);if(actors.size()>256||(long)actors.size()*before.size()>2_097_152)return null;
        var moves=new IdentityHashMap<Entity,Vec3>();var tops=new IdentityHashMap<Entity,Double>();var support=new IdentityHashMap<Entity,Double>();
        for(int n=0;n<before.size();n++){var old=before.get(n);var next=after.get(n);if(old.equals(next))continue;double dx=(next.minX+next.maxX-old.minX-old.maxX)/2,dz=(next.minZ+next.maxZ-old.minZ-old.maxZ)/2;
            for(var actor:actors){var body=actor.getBoundingBox();boolean standing=Math.abs(body.minY-old.maxY)<(actor instanceof Player?.5:.15)&&body.maxX>old.minX&&body.minX<old.maxX&&body.maxZ>old.minZ&&body.minZ<old.maxZ;
                if(standing){if(!NativeRampRiders.eligible(actor))continue;var previous=tops.get(actor);if(previous!=null&&previous>=old.maxY)continue;tops.put(actor,old.maxY);support.put(actor,next.maxY-old.maxY);moves.put(actor,new Vec3(dx,next.maxY-body.minY,dz));continue;}
                if(tops.containsKey(actor)||!body.intersects(old.minmax(next)))continue;double dy=(next.minY+next.maxY-old.minY-old.maxY)/2;var shifted=body.move(dx,dy,dz);double mx=dx,mz=dz;if(shifted.intersects(next)){if(Math.abs(dy)>=Math.abs(mx)&&Math.abs(dy)>=Math.abs(mz))dy+=dy>=0?next.maxY-shifted.minY+.001:next.minY-shifted.maxY-.001;else if(Math.abs(mx)>=Math.abs(mz))mx+=mx>=0?next.maxX-shifted.minX+.001:next.minX-shifted.maxX-.001;else mz+=mz>=0?next.maxZ-shifted.minZ+.001:next.minZ-shifted.maxZ-.001;}var delta=new Vec3(mx,dy,mz);var previous=moves.get(actor);if(previous==null||delta.lengthSqr()>previous.lengthSqr())moves.put(actor,delta);
            }
        }
        var result=new ArrayList<Step>();for(var actor:actors){var move=moves.get(actor);if(move==null)continue;boolean clear=NativeGearTargets.excluding(owner,Set.of(),()->{var obstacles=new ArrayList<AABB>();var swept=actor.getBoundingBox().expandTowards(move).inflate(1e-7);for(var shape:world.getBlockCollisions(actor,swept))for(var box:shape.toAabbs()){if(obstacles.size()>=131072)return false;obstacles.add(box);}var otherActors=new ArrayList<Entity>();world.getEntities(EntityTypeTest.forClass(Entity.class),swept,entity->entity!=actor&&!moves.containsKey(entity)&&actor.canCollideWith(entity),otherActors,257);if(otherActors.size()>256)return false;for(var other:otherActors)obstacles.add(other.getBoundingBox());return NativeRampRiders.clear(actor,move,obstacles);});if(!clear)return null;result.add(new Step(actor,actor.position(),actor.getBoundingBox(),actor.getDeltaMovement(),move,support.getOrDefault(actor,move.y),tops.containsKey(actor)));}
        return new NativeGearRiderPlan(world,result);
    }
    private static boolean available(Entity actor){return NativeRampRiders.available(actor)&&!(actor instanceof net.minecraft.world.entity.item.ItemEntity item&&NativeGearDropEscrow.held(item));}
    boolean commit(){if(tick!=world.getGameTime())return false;for(var step:steps)if(step.actor.level()!=world||!available(step.actor)||!step.actor.position().equals(step.position)||!step.actor.getBoundingBox().equals(step.body)||!step.actor.getDeltaMovement().equals(step.velocity))return false;
        var original=new ArrayList<Original>();var deliveries=new ArrayList<Delivery>();for(var step:steps){var actor=step.actor;original.add(new Original(actor,actor.position(),actor.getBoundingBox(),actor.getDeltaMovement(),actor.fallDistance,actor.onGround(),actor.hasImpulse));}
        try{for(var step:steps){var actor=step.actor;if(step.rider)NativeRampRiders.move(actor,step.delta.x,step.delta.y,step.delta.z,step.supportDy,(player,packet)->deliveries.add(new Delivery(player,packet)));else{actor.setPos(actor.position().add(step.delta));actor.fallDistance=0;actor.hasImpulse=true;}}}catch(RuntimeException failure){for(var old:original){old.actor.setPosRaw(old.position.x,old.position.y,old.position.z);old.actor.setBoundingBox(old.body);old.actor.setDeltaMovement(old.velocity);old.actor.fallDistance=old.fall;old.actor.setOnGround(old.grounded);old.actor.hasImpulse=old.impulse;NativeRampRiders.supported(old.actor);}throw failure;}
        for(var delivery:deliveries)try{net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(delivery.actor,delivery.packet);}catch(RuntimeException failure){if(delivery.actor.connection!=null)delivery.actor.connection.teleport(delivery.actor.getX(),delivery.actor.getY(),delivery.actor.getZ(),delivery.actor.getYRot(),delivery.actor.getXRot());}for(var step:steps)if(!step.rider&&step.actor instanceof ServerPlayer player&&player.connection!=null)player.connection.teleport(player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());return true;
    }
}
