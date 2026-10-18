package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import net.minecraft.util.EnumFacing;
import java.util.*;

/** Client-only wheel travel follows visible displacement, never throttle or wall-clock time. */
public final class VehicleWheelMotion {
    private static final class Motion { VehiclePose previous; double distance; }
    private static final Map<EntityGroundVehicle,Motion> motions=new WeakHashMap<>();
    public static double sample(EntityGroundVehicle craft,float partial){return samplePose(craft,craft.visualPose(partial));}
    static double samplePose(EntityGroundVehicle craft,VehiclePose pose){
        Motion motion=motions.computeIfAbsent(craft,k->new Motion());
        if(motion.previous!=null && !craft.hover){
            double dx=pose.x-motion.previous.x,dz=pose.z-motion.previous.z;
            // Reconciliation and teleports must not produce a burst of spinning.
            if(dx*dx+dz*dz<4){
                EnumFacing front=craft.structure.facing;double angle=Math.toRadians(pose.yaw),c=Math.cos(angle),s=Math.sin(angle);
                motion.distance+=dx*(front.getFrontOffsetX()*c-front.getFrontOffsetZ()*s)
                        +dz*(front.getFrontOffsetX()*s+front.getFrontOffsetZ()*c);
            }
        }
        motion.previous=pose;return motion.distance;
    }
    public static float roll(double distance,double radius,double alignment){return (float)(-distance*alignment/radius*180/Math.PI%360);}
    public static float steer(EntityGroundVehicle craft){return craft.hover?0:-18*Math.max(-1,Math.min(1,craft.steering));}
    public static int alignment(EnumFacing front,EnumFacing tangent){return front.getFrontOffsetX()*tangent.getFrontOffsetX()+front.getFrontOffsetZ()*tangent.getFrontOffsetZ();}
    static void clear(){motions.clear();}
    private VehicleWheelMotion(){}
}
