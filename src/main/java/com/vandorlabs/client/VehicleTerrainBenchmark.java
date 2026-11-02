package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import net.minecraft.world.World;
import java.io.*;
import java.util.*;

/** Actual copied-world broadphase timings, independent of GPU/frame limiting. */
final class VehicleTerrainBenchmark {
    private static final com.sun.management.ThreadMXBean BEAN=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
    static void run(World world,EntityGroundVehicle craft,File output){
        BEAN.setThreadAllocatedMemoryEnabled(true);BEAN.setThreadCpuTimeEnabled(true);
        double[][] expected={step(world,craft,-25,-1),step(world,craft,-25,1),step(world,craft,0,-1),step(world,craft,0,1),step(world,craft,25,-1),step(world,craft,25,1)};
        int[][] sizes={{3,3},{4,2},{-1,-1},{3,3}};
        try(PrintWriter csv=new PrintWriter(new File(output,"terrain-sections.csv"))){
            csv.println("horizontal,vertical,poses,samples,step_wall_p50_us,step_cpu_p50_us,step_allocated_p50_bytes,queries_per_step");
            for(int[] size:sizes)craft.collision.withTerrainSections(size[0],size[1],()->{
                long[][] times=new long[3][24];long queries=0;
                for(int round=-6;round<24;round++){
                    long cpu=BEAN.getCurrentThreadCpuTime(),bytes=BEAN.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();int pose=0;
                    for(float yaw:new float[]{-25,0,25})for(int direction:new int[]{-1,1}){
                        double[] actual=step(world,craft,yaw,direction);queries+=craft.collision.terrainQueries;
                        for(int axis=0;axis<3;axis++)if(Math.abs(actual[axis]-expected[pose][axis])>1E-9)throw new IllegalStateException("Terrain partition changes movement size="+Arrays.toString(size)+" pose="+pose+" axis="+axis);
                        pose++;
                    }
                    if(round>=0){times[0][round]=(System.nanoTime()-start)/6;times[1][round]=(BEAN.getCurrentThreadCpuTime()-cpu)/6;times[2][round]=(BEAN.getThreadAllocatedBytes(Thread.currentThread().getId())-bytes)/6;}
                }
                for(long[] row:times)Arrays.sort(row);
                csv.printf(Locale.ROOT,"%d,%d,6,24,%.3f,%.3f,%d,%.2f%n",size[0]<0?0:1<<size[0],size[1]<0?0:1<<size[1],times[0][12]/1000D,times[1][12]/1000D,times[2][12],queries/180D);csv.flush();
                System.out.println("[vandorlabs] terrain-sections PASS size="+(size[0]<0?"auto":(1<<size[0])+"x"+(1<<size[1]))+" wall-us="+times[0][12]/1000+" cpu-us="+times[1][12]/1000+" bytes="+times[2][12]+" queries="+queries/180D);
            });
        }catch(IOException failure){throw new IllegalStateException(failure);}
    }
    private static double[] step(World world,EntityGroundVehicle craft,float angle,int direction){
        VehicleCollision collision=craft.collision;float yaw=craft.rotationYaw+angle;double radians=Math.toRadians(yaw),fx=craft.structure.facing.getFrontOffsetX(),fz=craft.structure.facing.getFrontOffsetZ();
        double dx=(fx*Math.cos(radians)-fz*Math.sin(radians))*.28*direction,dz=(fx*Math.sin(radians)+fz*Math.cos(radians))*.28*direction;
        if(!collision.loaded(world,craft.posX,craft.posY,craft.posZ,dx,-.04,dz,yaw))throw new IllegalStateException("Terrain benchmark requires loaded hull and sweep");
        collision.begin(world,craft);
        try{double y=collision.clip(world,craft,craft.posX,craft.posY,craft.posZ,-.04,1,yaw);double x=collision.clip(world,craft,craft.posX,craft.posY+y,craft.posZ,dx,0,yaw);double z=collision.clip(world,craft,craft.posX+x,craft.posY+y,craft.posZ,dz,2,yaw);return new double[]{x,y,z};}
        finally{collision.end();}
    }
    private VehicleTerrainBenchmark(){}
}
