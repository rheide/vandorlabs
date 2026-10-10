package com.vandorlabs.vehicle;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Craft extents can cross many sections; vanilla entity queries only index their origin. */
public final class VehicleLookup {
    private VehicleLookup(){}
    public static List<EntityGroundVehicle> loaded(World world) {
        List<EntityGroundVehicle> result=new ArrayList<>();
        for(Entity entity:world.loadedEntityList)
            if(entity instanceof EntityGroundVehicle && !entity.isDead && ((EntityGroundVehicle)entity).structure!=null)
                result.add((EntityGroundVehicle)entity);
        return result;
    }
    public static EntityGroundVehicle pointed(EntityPlayer player) {
        Vec3d start=player.getPositionEyes(1),end=start.add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(6));
        RayTraceResult terrain=player.world.rayTraceBlocks(start,end,false,true,false);
        double nearest=terrain==null?36:start.squareDistanceTo(terrain.hitVec);
        EntityGroundVehicle selected=null;
        for(EntityGroundVehicle craft:loaded(player.world)) {
            Vec3d a=craft.toLocal(start),b=craft.toLocal(end);
            for(VehicleStructure.Cell cell:craft.structure.cells) {
                RayTraceResult hit=new AxisAlignedBB(cell.pos).calculateIntercept(a,b);
                if(hit==null)continue;
                double distance=start.squareDistanceTo(craft.toWorld(hit.hitVec));
                if(distance<=nearest){nearest=distance;selected=craft;}
            }
        }
        return selected;
    }
}
