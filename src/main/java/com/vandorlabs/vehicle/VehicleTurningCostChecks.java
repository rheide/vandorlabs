package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Dense native fence geometry exercises steering, not merely straight tiny hulls. */
public final class VehicleTurningCostChecks {
    public static void run(WorldServer world)throws Exception{
        if(!Boolean.getBoolean("vandorlabs.turningBenchmark"))return;
        BlockPos origin=new BlockPos(1400,100,1400);
        for(BlockPos p:BlockPos.getAllInBox(origin.add(-18,-1,-20),origin.add(22,-1,62)))world.setBlockState(p,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
        java.lang.reflect.Method advance=EntityGroundVehicle.class.getDeclaredMethod("advance",boolean.class);advance.setAccessible(true);
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        try{for(int count:new int[]{2,4,8}){
            int[] lift=new int[count];Arrays.fill(lift,3);VehicleStructure seed=VehicleCouplerSectionChecks.train(world,lift,false,false,false,true,true);
            Map<BlockPos,VehicleStructure.Cell> cells=new LinkedHashMap<>();for(VehicleStructure.Cell c:seed.cells)cells.put(c.pos,c);
            for(int body=0;body<count;body++)for(int x=0;x<9;x++)for(int z=0;z<4;z++)for(int y=0;y<9;y++){
                BlockPos p=new BlockPos(x,y,body*6+z);if(!cells.containsKey(p))cells.put(p,new VehicleStructure.Cell(p,(y==0?net.minecraft.init.Blocks.STONE:net.minecraft.init.Blocks.OAK_FENCE).getDefaultState(),null));
            }
            EntityGroundVehicle craft=new EntityGroundVehicle(world);
            try{craft.install(new VehicleStructure(new ArrayList<>(cells.values()),seed.seat,seed.facing,seed.gearCount));craft.setPosition(origin.getX(),origin.getY(),origin.getZ());craft.view.origin=origin;craft.onGround=true;craft.driveSpeed=.23;craft.steering=1;craft.autopilotControls((byte)1,(byte)1,false);
                long[] times=new long[8],bytes=new long[8];System.out.println("[vandorlabs][reprolab] dense-turn START segments="+count+" blocks="+cells.size());
                for(int tick=0;tick<times.length;tick++){long b=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();advance.invoke(craft,true);times[tick]=System.nanoTime()-start;bytes[tick]=bean.getThreadAllocatedBytes(thread)-b;}
                Arrays.sort(times);Arrays.sort(bytes);System.out.println("[vandorlabs][reprolab] dense-turn segments="+count+" blocks="+cells.size()+" median-us="+times[4]/1000+" max-us="+times[7]/1000+" median-bytes="+bytes[4]+" poses="+craft.articulation.poseChecks+" work="+craft.articulation.peakStepWork+" limits="+craft.articulation.budgetStops);
                if(craft.articulation.budgetStops!=0)throw new IllegalStateException("Normal dense train exhausted steering budget");
                if(!craft.articulation.validPose())throw new IllegalStateException("Dense turning penetrated terrain/self");
                NBTTagCompoundCheck.exhaust(craft);
            }finally{craft.setDead();}
        }}finally{for(BlockPos p:BlockPos.getAllInBox(origin.add(-18,-1,-20),origin.add(22,-1,62)))world.setBlockToAir(p);}
    }
    private static final class NBTTagCompoundCheck {
        static void exhaust(EntityGroundVehicle craft)throws Exception{
            net.minecraft.nbt.NBTTagCompound before=new net.minecraft.nbt.NBTTagCompound();craft.articulation.write(before);double x=craft.posX,y=craft.posY,z=craft.posZ;float yaw=craft.rotationYaw;
            craft.articulation.begin();java.lang.reflect.Field work=VehicleArticulation.class.getDeclaredField("stepWork");work.setAccessible(true);work.setInt(craft.articulation,VehicleArticulation.MAX_STEP_WORK);
            craft.rotationYaw=craft.articulation.turn(x,y,z,yaw,3);craft.articulation.applyTurn();craft.articulation.end();net.minecraft.nbt.NBTTagCompound after=new net.minecraft.nbt.NBTTagCompound();craft.articulation.write(after);
            if(craft.articulation.budgetStops!=1||craft.posX!=x||craft.posY!=y||craft.posZ!=z||craft.rotationYaw!=yaw||!before.equals(after)||!craft.articulation.validPose())throw new IllegalStateException("Work limit did not preserve the last safe full pose");
            System.out.println("[vandorlabs][reprolab] steering-budget PASS safe pose retained ownership unchanged");
        }
    }
    private VehicleTurningCostChecks(){}
}
