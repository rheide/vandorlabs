package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.util.math.*;

/** A broad vehicle pick box must not hide terrain visible through its empty space. */
final class VehicleTerrainSelection {
    private static RayTraceResult restoredBlock;
    static boolean restored(RayTraceResult selected){return selected==restoredBlock;}
    static RayTraceResult restore(Minecraft mc,Entity camera,Vec3d start,Vec3d end,RayTraceResult vanilla){
        if(vanilla==restoredBlock)return vanilla;
        restoredBlock=null;
        if(vanilla==null||vanilla.typeOfHit==RayTraceResult.Type.BLOCK||vanilla.typeOfHit==RayTraceResult.Type.ENTITY&&!(vanilla.entityHit instanceof EntityGroundVehicle))return vanilla;
        AxisAlignedBB ray=new AxisAlignedBB(start.x,start.y,start.z,end.x,end.y,end.z).grow(.001);
        java.util.List<EntityGroundVehicle> crafts=VehicleLookup.near(mc.world,ray);if(crafts.isEmpty())return vanilla;
        RayTraceResult nearest=mc.world.rayTraceBlocks(start,end,false,false,true);double distance=nearest!=null&&nearest.typeOfHit==RayTraceResult.Type.BLOCK?start.squareDistanceTo(nearest.hitVec):start.squareDistanceTo(end);
        for(EntityGroundVehicle craft:crafts){
            if(!craft.getEntityBoundingBox().contains(start)&&craft.getEntityBoundingBox().calculateIntercept(start,end)==null)continue;
            VehicleSelection.Hit hit=VehicleSelection.trace(craft,start,end,distance,mc.player.getRidingEntity()==craft?craft.seatForPassenger(mc.player):null);
            if(hit!=null){distance=hit.distanceSq;nearest=new RayTraceResult(craft,craft.toWorld(hit.local.hitVec,hit.cell.pos));}
        }
        for(Entity entity:mc.world.getEntitiesInAABBexcluding(camera,ray.grow(1),e->e.canBeCollidedWith())){
            if(entity instanceof EntityGroundVehicle||entity==camera.getRidingEntity())continue;
            AxisAlignedBB box=entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());RayTraceResult hit=box.calculateIntercept(start,end);Vec3d point=box.contains(start)?start:hit==null?null:hit.hitVec;
            if(point!=null&&start.squareDistanceTo(point)<distance){distance=start.squareDistanceTo(point);nearest=new RayTraceResult(entity,point);}
        }
        if(nearest!=null&&nearest.typeOfHit==RayTraceResult.Type.ENTITY){double reach=mc.playerController.extendedReach()?6:3;if(distance>reach*reach)nearest=new RayTraceResult(RayTraceResult.Type.MISS,nearest.hitVec,null,new BlockPos(nearest.hitVec));}
        mc.pointedEntity=nearest!=null&&(nearest.entityHit instanceof EntityLivingBase||nearest.entityHit instanceof EntityItemFrame)?nearest.entityHit:null;
        if(nearest!=null&&nearest.typeOfHit==RayTraceResult.Type.BLOCK)restoredBlock=nearest;
        return nearest;
    }
    private VehicleTerrainSelection(){}
}
