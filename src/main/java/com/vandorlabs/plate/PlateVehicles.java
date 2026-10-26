package com.vandorlabs.plate;

import com.vandorlabs.vehicle.*;
import net.minecraft.util.math.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.Entity;

/** Authoritative carried plates sense the physical parent world, never the read-only view. */
public final class PlateVehicles {
    private PlateVehicles(){}
    private static final java.util.Map<EntityGroundVehicle,Plates> CACHE=new java.util.WeakHashMap<>();
    private static final class Plates {
        final java.lang.ref.WeakReference<VehicleWorld> world;final int revision;final java.util.List<BlockPos> positions=new java.util.ArrayList<>();
        Plates(VehicleWorld view){world=new java.lang.ref.WeakReference<>(view);revision=view.tileRevision;for(TileEntity tile:view.tiles.values())if(tile instanceof PlateTile)positions.add(tile.getPos());}
    }
    public static void tick(EntityGroundVehicle craft){tick(craft,craft.world.getTotalWorldTime());}
    public static void tick(EntityGroundVehicle craft,long sensingTick){
        if(craft.world.isRemote||craft.view==null||craft.ownershipBlocked||craft.transferInProgress)return;
        VehiclePose pose=new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw);double px=craft.structure.bounds.maxX/2,pz=craft.structure.bounds.maxZ/2;
        Plates plates=CACHE.get(craft);if(plates==null||plates.world.get()!=craft.view||plates.revision!=craft.view.tileRevision){plates=new Plates(craft.view);CACHE.put(craft,plates);}
        for(BlockPos pos:plates.positions){
            TileEntity raw=craft.view.getTileEntity(pos);if(!(raw instanceof PlateTile))continue;PlateTile tile=(PlateTile)raw;
            if(!tile.carriedSampleDue(sensingTick))continue;AxisAlignedBB local=new AxisAlignedBB(pos.getX()+.125,pos.getY(),pos.getZ()+.125,pos.getX()+.875,pos.getY()+.25,pos.getZ()+.875);java.util.List<Entity> sensed=new java.util.ArrayList<>();
            for(Entity entity:craft.world.getEntitiesWithinAABBExcludingEntity(craft,pose.world(local,px,pz))){
                AxisAlignedBB body=entity.getEntityBoundingBox();Vec3d center=pose.local(new Vec3d((body.minX+body.maxX)/2,(body.minY+body.maxY)/2,(body.minZ+body.maxZ)/2),px,pz);
                double angle=Math.toRadians(pose.yaw),c=Math.abs(Math.cos(angle)),s=Math.abs(Math.sin(angle)),hx=(body.maxX-body.minX)/2,hz=(body.maxZ-body.minZ)/2;
                // Parent AABB supplies the world-axis tests; these are the two rotated plate axes.
                if(Math.abs(center.x-(pos.getX()+.5))>.375+c*hx+s*hz||Math.abs(center.z-(pos.getZ()+.5))>.375+s*hx+c*hz)continue;
                sensed.add(entity);}
            craft.signals.pressurePlate(tile,tile.carriedMeasurement(PlateTile.measure(tile.sensitivity(),sensed),sensingTick));
        }
    }
}
