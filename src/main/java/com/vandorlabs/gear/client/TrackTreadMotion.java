package com.vandorlabs.gear.client;

import com.vandorlabs.vehicle.EntityGroundVehicle;
import com.vandorlabs.vehicle.VehiclePose;
import java.util.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Belt travel follows each visible contact point, including differential travel while steering. */
public final class TrackTreadMotion {
    private static final class Motion {VehiclePose previous;double distance;}
    private static final Map<EntityGroundVehicle,Map<BlockPos,Motion>> MOTION=new WeakHashMap<>();
    public static double sample(EntityGroundVehicle craft,BlockPos track,EnumFacing facing,float partial){return samplePose(craft,track,facing,craft.visualPose(partial));}
    static double samplePose(EntityGroundVehicle craft,BlockPos track,EnumFacing facing,VehiclePose pose){
        if(!craft.structure.isActive(track))return 0;
        Motion motion=MOTION.computeIfAbsent(craft,k->new HashMap<>()).computeIfAbsent(track.toImmutable(),k->new Motion());
        if(motion.previous!=null&&!craft.hover){
            double dx=pose.x-motion.previous.x,dz=pose.z-motion.previous.z;
            if(dx*dx+dz*dz<4&&Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(pose.yaw-motion.previous.yaw))<45){
                Vec3d point=new Vec3d(track).addVector(.5,0,.5),before=motion.previous.world(point,craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2),after=pose.world(point,craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2);
                double yaw=Math.toRadians(pose.yaw),c=Math.cos(yaw),s=Math.sin(yaw);
                motion.distance+=(after.x-before.x)*(facing.getFrontOffsetX()*c-facing.getFrontOffsetZ()*s)+(after.z-before.z)*(facing.getFrontOffsetX()*s+facing.getFrontOffsetZ()*c);
            }
        }
        motion.previous=pose;return motion.distance;
    }
    static void clear(){MOTION.clear();}
    private TrackTreadMotion(){}
}
