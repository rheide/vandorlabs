package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import net.minecraft.util.math.Vec3d;

final class VehicleMovementChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void run() {
        VehicleControls controls=new VehicleControls();
        for(float steering:new float[]{-1,-.4F,.4F,1})for(boolean hover:new boolean[]{false,true}){
            float forwardTurn=VehicleControls.yawChange(steering,.2,hover,false),reverseTurn=VehicleControls.yawChange(steering,-.2,hover,false);
            require(forwardTurn==-reverseTurn,"reverse steering is the opposite yaw response in both modes");
            require(Math.signum(forwardTurn)==Math.signum(steering),"forward steering retains its existing direction");
        }
        require(VehicleControls.yawChange(1,0,true,false)==1.7F,"stationary Hover yaw remains available");
        require(VehicleControls.yawChange(1,0,false,false)==0,"stationary Taxi cannot yaw");
        require(VehicleControls.yawChange(1,-.2,true,true)==0,"Autopilot retains its own Hover turns");
        for(int n=0;n<12;n++)require(controls.offer(n,(byte)1,(byte)-1,false),"batched commands accepted");
        require(controls.acknowledged()==-1,"receiving packets does not acknowledge unsimulated inputs");
        for(int n=0;n<12;n++) {
            require(controls.poll().sequence==n && controls.acknowledged()==n,"one controller step acknowledges exactly one command");
            require(!controls.offer(n,(byte)1,(byte)0,false),"duplicate/stale commands rejected");
        }
        require(controls.poll()==null && controls.acknowledged()==11,"packet gaps do not fabricate acknowledgments");
        for(int n=12;n<12+VehicleControls.LIMIT;n++)require(controls.offer(n,(byte)0,(byte)0,true),"bounded queue fills");
        require(!controls.offer(1000,(byte)1,(byte)0,false) && controls.size()==VehicleControls.LIMIT,"queue flood bounded");
        require(!controls.offer(1001,(byte)2,(byte)0,false),"invalid intent refused");
        controls.clear();require(controls.acknowledged()==-1 && controls.size()==0 && controls.offer(1,(byte)0,(byte)0,false),"boarding resets command history");
        VehiclePose from=new VehiclePose(-64,100,16,179),to=new VehiclePose(-63,101,18,-179);
        VehiclePose middle=from.interpolate(to,.5F);
        require(Math.abs(middle.yaw-180)<.001,"yaw wrap takes shortest arc");
        Vec3d farSeat=new Vec3d(31.5,2,.5),absolute=middle.world(farSeat,16,4);
        require(middle.local(absolute,16,4).distanceTo(farSeat)<1e-8,"far seat and camera share invertible visual transform");
        net.minecraft.util.math.AxisAlignedBB localBox=new net.minecraft.util.math.AxisAlignedBB(-2,-1,3,32,8,9);
        for(float yaw:new float[]{0,45,90,179,-179,270}) {
            VehiclePose pose=new VehiclePose(-64,100,16,yaw);
            net.minecraft.util.math.AxisAlignedBB box=pose.world(localBox,16,4);
            for(double x:new double[]{localBox.minX,localBox.maxX})for(double y:new double[]{localBox.minY,localBox.maxY})for(double z:new double[]{localBox.minZ,localBox.maxZ}) {
                Vec3d point=pose.world(new Vec3d(x,y,z),16,4);
                require(point.x>=box.minX-1e-8 && point.x<=box.maxX+1e-8 && point.y>=box.minY-1e-8 && point.y<=box.maxY+1e-8 && point.z>=box.minZ-1e-8 && point.z<=box.maxZ+1e-8,"tile visibility bounds contain every transformed corner");
            }
        }
        VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.STATE);packet.serverTick=123456789;packet.sequence=17;packet.yaw=-179;
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();packet.toBytes(bytes);
        VehicleNetwork.Packet decoded=new VehicleNetwork.Packet();decoded.fromBytes(bytes);bytes.release();
        require(decoded.valid && decoded.serverTick==packet.serverTick && decoded.sequence==17 && decoded.yaw==-179,"state tick/ack codec");
        System.out.println("PASS: simulated-input acknowledgments, batched/duplicate/missing commands, bounded queue, yaw wrap and shared far-seat pose");
    }
}
