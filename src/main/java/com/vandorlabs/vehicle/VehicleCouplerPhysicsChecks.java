package com.vandorlabs.vehicle;
import com.vandorlabs.physics.RigidTransform;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
/** Native movement and persistence for a multi-trailer owner; no synthetic transfer of inventories. */
public final class VehicleCouplerPhysicsChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("coupler physics: "+why);}
    public static void run(WorldServer world){
        BlockPos origin=new BlockPos(1000,100,1000);for(BlockPos p:BlockPos.getAllInBox(origin.add(-48,-1,-48),origin.add(64,-1,64)))world.setBlockState(p,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
        EntityGroundVehicle craft=new EntityGroundVehicle(world);
        try{
            VehicleStructure structure=VehicleCouplerSectionChecks.train(world,new int[]{3,3,3,3},false,false,false,true,true);craft.install(structure);craft.setPosition(origin.getX(),origin.getY(),origin.getZ());craft.view.origin=origin;
            require(craft.articulation.jointCount()==3,"three independent native joints");require(craft.articulation.validPose(),"neutral train must be collision free");
            java.lang.reflect.Method advance=EntityGroundVehicle.class.getDeclaredMethod("advance",boolean.class);advance.setAccessible(true);craft.autopilotControls((byte)1,(byte)1,false);
            long[] timings=new long[60];
            for(int i=0;i<60;i++){long started=System.nanoTime();advance.invoke(craft,true);timings[i]=System.nanoTime()-started;if(i%10==0)require(craft.articulation.validPose(),"turning train penetrated terrain or itself at step "+i);}
            require(Math.abs(craft.rotationYaw)>5,"tractor turns while towing yaw="+craft.rotationYaw+" speed="+craft.driveSpeed+" poseChecks="+craft.articulation.poseChecks);
            require(Math.abs(craft.articulation.jointYaw(0))>.1,"first trailer must articulate");require(Math.abs(craft.articulation.jointYaw(0)-craft.articulation.jointYaw(1))>.01,"trailers need independent angles");
            for(int i=0;i<3;i++){double[] capped=RigidTransform.limitedAngles(craft.articulation.jointYaw(i),craft.articulation.jointPitch(i),75);require(Math.abs(capped[0]-craft.articulation.jointYaw(i))<1e-6&&Math.abs(capped[1]-craft.articulation.jointPitch(i))<1e-6,"75-degree combined limit");}
            NBTTagCompound saved=new NBTTagCompound();craft.writeToNBT(saved);require(saved.hasKey("CouplerJointPose",10),"bent pose must persist outside portable block inventory data");EntityGroundVehicle loaded=new EntityGroundVehicle(world);
            try{loaded.readFromNBT(saved);require(loaded.structure!=null&&loaded.articulation.jointCount()==3,"saved train restores its joints");for(int i=0;i<3;i++)require(Math.abs(loaded.articulation.jointYaw(i)-craft.articulation.jointYaw(i))<1e-8,"saved joint angle changed");}finally{loaded.setDead();}
            NBTTagCompound invalid=saved.copy();invalid.getCompoundTag("CouplerJointPose").getTagList("Joints",10).getCompoundTagAt(0).setDouble("Yaw",76);EntityGroundVehicle unavailable=new EntityGroundVehicle(world);
            try{unavailable.readFromNBT(invalid);require(unavailable.structure==null,"invalid joint pose must remain inert");NBTTagCompound retained=new NBTTagCompound();unavailable.writeToNBT(retained);require(invalid.getCompoundTag("Craft").equals(retained.getCompoundTag("Craft"))&&invalid.getCompoundTag("CouplerJointPose").equals(retained.getCompoundTag("CouplerJointPose")),"failed load must retain original craft and pose");}finally{unavailable.setDead();}
            require(!craft.articulation.aligned(),"curved train must require alignment before block parking");
            BlockPos trailer=new BlockPos(1,0,19);net.minecraft.util.math.Vec3d center=new net.minecraft.util.math.Vec3d(trailer).addVector(.5,.5,.5),absolute=craft.toWorld(center,trailer);
            require(craft.toLocal(absolute,trailer).distanceTo(center)<1e-8,"articulated interaction inverse");
            net.minecraft.util.math.Vec3d top=craft.toWorld(center.addVector(0,5,0),trailer),bottom=craft.toWorld(center.addVector(0,-2,0),trailer);
            VehicleSelection.Hit selected=VehicleSelection.trace(craft,top,bottom,100,false);require(selected!=null&&craft.articulation.bodyAt(selected.cell.pos)==3,"ray selects actual trailing section");
            require(craft.articulation.collisionBoxes()==craft.articulation.collisionBoxes(),"collision query reuses immutable posed boxes");
            checkNetwork(craft);
            EntityGroundVehicle lifted=new EntityGroundVehicle(world);try{lifted.install(structure);lifted.setPosition(origin.getX(),origin.getY()+1,origin.getZ());lifted.articulation.begin();lifted.setPosition(lifted.posX,lifted.posY+.25,lifted.posZ);lifted.articulation.end();require(Math.abs(lifted.articulation.jointPitch(0))>.1&&lifted.articulation.validPose(),"Taxi hitch follows vertical motion with underbody clearance pitch="+lifted.articulation.jointPitch(0)+" y="+lifted.posY);}finally{lifted.setDead();}
            EntityGroundVehicle rigid=new EntityGroundVehicle(world);try{rigid.install(VehicleCouplerSectionChecks.train(world,new int[]{3,3,3},true,false,false,true,true));require(!rigid.articulation.active(),"parallel native heads stay rigid");rigid.install(VehicleCouplerSectionChecks.train(world,new int[]{3,3,3},false,false,false,true,false));long builds=rigid.articulation.shapeBuilds;require(!rigid.articulation.active()&&rigid.articulation.shapeBuilds==builds,"legacy default bypasses articulated shape collection");}finally{rigid.setDead();}

            EntityGroundVehicle flying=new EntityGroundVehicle(world);try{
                flying.install(structure);flying.setPosition(origin.getX(),origin.getY()+8,origin.getZ());flying.hover=true;flying.hoverTarget=flying.posY+4;flying.autopilotControls((byte)1,(byte)1,false);
                double maxPitch=0;for(int n=0;n<45;n++){advance.invoke(flying,true);for(int i=0;i<3;i++)maxPitch=Math.max(maxPitch,Math.abs(flying.articulation.jointPitch(i)));if(n%5==0)require(flying.articulation.validPose(),"Hover pose terrain/self collision");}
                require(maxPitch>.1,"Hover climb must articulate vertically: "+maxPitch);require(flying.posY>origin.getY()+9,"Hover climb must move root");
                System.out.println("[vandorlabs][reprolab] coupler-hover-physics PASS vertical-pitch="+maxPitch+" root-y="+flying.posY);
            }finally{flying.setDead();}

            Arrays.sort(timings);System.out.println("[vandorlabs][reprolab] coupler-physics-native PASS joints=3 independent-yaw="+craft.articulation.jointYaw(0)+","+craft.articulation.jointYaw(1)+","+craft.articulation.jointYaw(2)+" median-us="+timings[30]/1000+" p95-us="+timings[57]/1000+" pose-checks="+craft.articulation.poseChecks+" terrain-queries="+craft.articulation.terrainQueries+" exact-reload invalid-pose-retention");
        }catch(Exception failure){throw new IllegalStateException(failure);}finally{craft.setDead();for(BlockPos p:BlockPos.getAllInBox(origin.add(-48,-1,-48),origin.add(64,-1,64)))world.setBlockToAir(p);}
    }
    private static void checkNetwork(EntityGroundVehicle craft){
        VehicleNetwork.Packet legacy=new VehicleNetwork.Packet(VehicleNetwork.STATE);io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();legacy.toBytes(bytes);require(bytes.readableBytes()==82,"unchanged legacy STATE bytes");VehicleNetwork.Packet decoded=new VehicleNetwork.Packet();decoded.fromBytes(bytes.duplicate());require(decoded.valid&&decoded.jointAngles.length==0,"legacy STATE roundtrip");bytes.release();
        VehicleNetwork.Packet source=new VehicleNetwork.Packet(VehicleNetwork.STATE);source.jointTopology=craft.articulation.topologyId();source.jointAngles=craft.articulation.network();bytes=io.netty.buffer.Unpooled.buffer();source.toBytes(bytes);int size=bytes.readableBytes();decoded=new VehicleNetwork.Packet();decoded.fromBytes(bytes.duplicate());require(decoded.valid&&decoded.jointTopology==source.jointTopology&&Arrays.equals(decoded.jointAngles,source.jointAngles),"joint STATE roundtrip");require(size==82+14+source.jointAngles.length*2,"STATE measured overhead");
        io.netty.buffer.ByteBuf malformed=bytes.copy();malformed.setShort(94,32767);VehicleNetwork.Packet rejected=new VehicleNetwork.Packet();rejected.fromBytes(malformed);require(!rejected.valid&&rejected.jointAngles.length==0,"oversized count rejected before allocation");malformed.release();
        malformed=bytes.copy();malformed.writeByte(0);rejected=new VehicleNetwork.Packet();rejected.fromBytes(malformed);require(!rejected.valid,"trailing state bytes rejected");malformed.release();bytes.release();
        System.out.println("[vandorlabs][reprolab] coupler-network PASS legacy-bytes=82 three-joint-bytes="+size+" roundtrip bounded-before-allocation malformed-trailing");
    }
    private VehicleCouplerPhysicsChecks(){}
}
