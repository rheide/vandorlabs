package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Reproduce acceleration from a slight trailer angle, with per-segment CPU/allocation evidence. */
public final class VehicleTrainDriveChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("train drive: "+why);}
    public static void run(WorldServer world)throws Exception{
        BlockPos origin=new BlockPos(1100,100,1100);
        for(BlockPos p:BlockPos.getAllInBox(origin.add(-12,-1,-55),origin.add(12,-1,60)))world.setBlockState(p,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
        java.lang.reflect.Method advance=EntityGroundVehicle.class.getDeclaredMethod("advance",boolean.class);advance.setAccessible(true);
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        try{for(int count:new int[]{1,2,4,8}){int[] lift=new int[count];Arrays.fill(lift,3);EntityGroundVehicle craft=new EntityGroundVehicle(world);
            try{craft.install(VehicleCouplerSectionChecks.train(world,lift,false,false,false,true,true));craft.setPosition(origin.getX(),origin.getY(),origin.getZ());craft.view.origin=origin;
                if(count>1){short[] angles=new short[(count-1)*2];angles[0]=200;craft.articulation.receive(craft.articulation.topologyId(),angles,true);require(craft.articulation.validPose(),"slightly bent initial train");}
                VehicleArticulation.requireAssemblyLimit(craft.structure);
                if(count==4){require(craft.articulation.parkable()&&!craft.articulation.aligned(),"nearly straight train may park");VehicleRotation.plan(craft);require(Math.abs(craft.articulation.jointYaw(0)-2)<1e-8,"parking preflight never changes live joint ownership/pose");recoverPose(world,craft,origin);short[] bent=new short[6];bent[0]=600;craft.articulation.receive(craft.articulation.topologyId(),bent,true);require(!craft.articulation.parkable(),"visibly bent train refuses snapping");bent[0]=200;craft.articulation.receive(craft.articulation.topologyId(),bent,true);}
                craft.autopilotControls((byte)1,(byte)0,false);long[] time=new long[100],allocation=new long[100];double minimum=Double.POSITIVE_INFINITY;int stops=0;
                for(int tick=0;tick<100;tick++){long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();advance.invoke(craft,true);time[tick]=System.nanoTime()-start;allocation[tick]=bean.getThreadAllocatedBytes(thread)-bytes;if(tick>=20){minimum=Math.min(minimum,craft.driveSpeed);if(craft.driveSpeed<.05){stops++;if(stops<=4)System.out.println("[vandorlabs] train-stop tick="+tick+" y="+craft.posY+" yaw="+craft.rotationYaw+" joint="+(count>1?craft.articulation.jointYaw(0):0)+" pitch="+(count>1?craft.articulation.jointPitch(0):0)+" mx="+craft.motionX+" my="+craft.motionY+" mz="+craft.motionZ);}}if(tick%10==0)require(craft.articulation.validPose(),"native terrain penetration");}
                Arrays.sort(time);Arrays.sort(allocation);double distance=Math.hypot(craft.posX-origin.getX(),craft.posZ-origin.getZ());System.out.println("[vandorlabs][reprolab] train-drive segments="+count+" distance="+distance+" min-speed="+minimum+" final-speed="+craft.driveSpeed+" stops="+stops+" median-us="+time[50]/1000+" p95-us="+time[95]/1000+" median-bytes="+allocation[50]+" pose-checks="+craft.articulation.poseChecks);
                require(distance>15&&stops==0,"slight trailer angle must retain ordinary acceleration, segments="+count+" distance="+distance+" stops="+stops);
            }finally{craft.setDead();}}
            int[] excessive=new int[9];Arrays.fill(excessive,3);VehicleStructure tooMany=VehicleCouplerSectionChecks.train(world,excessive,false,false,false,true,true);boolean denied=false;try{VehicleArticulation.requireAssemblyLimit(tooMany);}catch(IllegalArgumentException expected){denied=true;}require(denied,"new nine-segment assembly must refuse before transfer");EntityGroundVehicle legacy=new EntityGroundVehicle(world);try{legacy.install(tooMany);require(legacy.articulation.jointCount()==8,"existing saved longer trains still load for recovery/parking");}finally{legacy.setDead();}
        }finally{for(BlockPos p:BlockPos.getAllInBox(origin.add(-12,-1,-55),origin.add(12,-1,60)))world.setBlockToAir(p);}
        System.out.println("[vandorlabs][reprolab] train-drive PASS one/two/four/eight segments slight-angle acceleration terrain checks");
    }
    private static void recoverPose(WorldServer world,EntityGroundVehicle craft,BlockPos origin)throws Exception{
        java.lang.reflect.Method record=VehicleTransfer.class.getDeclaredMethod("record",VehicleStructure.class,BlockPos.class,EntityGroundVehicle.class,boolean.class),write=VehicleTransfer.class.getDeclaredMethod("write",WorldServer.class,net.minecraft.nbt.NBTTagCompound.class);record.setAccessible(true);write.setAccessible(true);
        net.minecraft.nbt.NBTTagCompound journal=(net.minecraft.nbt.NBTTagCompound)record.invoke(null,craft.structure,origin,craft,true);require(journal.hasKey("CouplerJointPose",10),"durable parking record retains original trailer pose");journal.setTag("ParkedCraft",craft.structure.write());
        VehicleStructure.Cell first=craft.structure.cells.get(0);world.setBlockState(origin.add(first.pos),first.state,2);write.invoke(null,world,journal);require(VehicleTransfer.pending(world),"real durable parking journal");VehicleTransfer.recover(world);
        EntityGroundVehicle restored=null;int owners=0;for(net.minecraft.entity.Entity entity:new ArrayList<>(world.loadedEntityList))if(entity instanceof EntityGroundVehicle&&entity.getUniqueID().equals(craft.getUniqueID())&&!entity.isDead){restored=(EntityGroundVehicle)entity;owners++;}
        require(owners==1&&restored!=null&&!VehicleTransfer.pending(world),"recovery restores exactly one world vehicle and retires journal");require(Math.abs(restored.articulation.jointYaw(0)-2)<1e-8,"partial native parking recovery retains bent trailer pose");for(VehicleStructure.Cell cell:craft.structure.cells)require(world.isAirBlock(origin.add(cell.pos)),"recovery removes partial native ownership");restored.signals.close();world.removeEntityDangerously(restored);restored.setDead();
        System.out.println("[vandorlabs][reprolab] articulated-parking-recovery PASS real durable journal partial native publication exactly-one-owner original joint pose retained");
    }
    /** Exact saved-world fixture uses the same queued validation/parking path as the controller. */
    public static void parkRecovered(WorldServer world,EntityGroundVehicle craft,net.minecraft.entity.player.EntityPlayerMP actor,Runnable complete,java.util.function.Consumer<Exception> failure){
        VehicleRotation.Plan plan=VehicleRotation.plan(craft);VehicleTransfer.submit(world,null,plan.origin,()->{VehicleService.validateParking(actor,craft,plan.origin,true,false);VehicleTransfer.park(world,craft,plan.origin,actor);require(craft.isDead,"parking removes carried owner");for(VehicleStructure.Cell cell:plan.structure.cells)require(world.getBlockState(plan.origin.add(cell.pos))==cell.state,"native blocks restored after leveling");complete.run();},failure);
    }
    private VehicleTrainDriveChecks(){}
}
