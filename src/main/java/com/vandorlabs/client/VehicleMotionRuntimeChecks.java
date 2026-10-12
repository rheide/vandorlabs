package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.io.File;
import java.util.*;

/** Delayed STATE delivery through production reconciliation and observer interpolation. */
public final class VehicleMotionRuntimeChecks {
    private static boolean done;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("vehicle motion: "+message);}
    public static boolean tick(Minecraft mc,File output) throws Exception {
        if(done)return true;
        VehiclePerformanceRuntimeChecks.run(mc);
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),null));
        cells.add(new VehicleStructure.Cell(new BlockPos(31,0,0),Blocks.STONE.getDefaultState(),null));
        VehicleStructure hull=new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1);
        EntityGroundVehicle observer=new EntityGroundVehicle(mc.world);observer.install(hull);observer.setPosition(0,110,0);
        VehicleNetwork.Packet state=new VehicleNetwork.Packet(VehicleNetwork.STATE);state.serverTick=10;state.yaw=179;state.x=1;state.y=110;
        observer.authoritative(state);
        for(int n=0;n<3;n++)observer.onUpdate();
        state.serverTick=12;state.yaw=-179;state.x=2;observer.authoritative(state);
        observer.setPositionAndRotationDirect(500,500,500,0,0,1,false);
        float before=observer.visualPose(1).yaw;
        for(int n=0;n<3;n++) {
            observer.onUpdate();float after=observer.visualPose(1).yaw;
            require(Math.abs(MathHelper.wrapDegrees(after-before))<1,"observer wraps without yaw reversal or vanilla interpolation conflict");before=after;
        }
        require(Math.abs(observer.posX-2)<1e-6,"custom STATE is the sole observer pose source");
        state.serverTick=11;state.x=900;observer.authoritative(state);observer.onUpdate();
        require(Math.abs(observer.posX-2)<1e-6,"out-of-order state refused even with unchanged input acknowledgment");
        EntityGroundVehicle driver=new EntityGroundVehicle(mc.world);driver.install(hull);driver.setPosition(0,110,0);driver.setLocalDriver(true);
        state.serverTick=20;state.sequence=0;state.x=0;state.y=110;state.yaw=0;state.dy=0;
        driver.authoritative(state);driver.onUpdate();
        float visual=driver.visualPose(1).yaw;
        state.serverTick=21;state.sequence=0;state.yaw=8;driver.authoritative(state);
        require(driver.rotationYaw==8 && driver.visualPose(1).yaw==visual,"authoritative yaw changes without an immediate visual snap");
        driver.onUpdate();require(Math.abs(MathHelper.wrapDegrees(driver.visualPose(1).yaw-visual))<4,"yaw error eases independently of collision pose");
        Vec3d local=new Vec3d(31.5,2,.5);VehiclePose pose=driver.visualPose(.5F);
        require(pose.local(pose.world(local,16,.5),16,.5).distanceTo(local)<1e-7,"hull/camera far-seat transform agrees at intermediate frame");
        require(!driver.isAddedToWorld() && !observer.isAddedToWorld(),"validation craft are never registered as live entities");
        System.out.println("[vandorlabs][reprolab] vehicle-motion PASS observer-stream stale-state yaw-wrap angular-easing shared-pose");
        done=true;return true;
    }
}
