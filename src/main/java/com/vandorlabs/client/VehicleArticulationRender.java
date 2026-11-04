package com.vandorlabs.client;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import com.vandorlabs.physics.RigidTransform;
import net.minecraft.util.math.*;
import org.lwjgl.opengl.GL11;
/** Apply section matrices without rebuilding block vertices or rewriting shader payloads. */
final class VehicleArticulationRender {
    private static final java.nio.DoubleBuffer MATRIX=org.lwjgl.BufferUtils.createDoubleBuffer(16);
    static void apply(EntityGroundVehicle craft,int body,float partial){
        if(body==0||!craft.articulation.active())return;RigidTransform t=craft.articulation.body(body,partial);double[] r=t.r;
        MATRIX.clear();MATRIX.put(r[0]).put(r[3]).put(r[6]).put(0).put(r[1]).put(r[4]).put(r[7]).put(0).put(r[2]).put(r[5]).put(r[8]).put(0).put(t.x).put(t.y).put(t.z).put(1).flip();GL11.glMultMatrix(MATRIX);
    }
    static void apply(EntityGroundVehicle craft,BlockPos owner,float partial){if(craft.articulation.active())apply(craft,craft.articulation.bodyAt(owner),partial);}
    private VehicleArticulationRender(){}
}
