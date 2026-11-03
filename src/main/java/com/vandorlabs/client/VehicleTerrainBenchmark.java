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
        int[][] sizes={{3,3},{4,2},{4,1},{5,1},{5,2},{4,0},{5,0},{-1,-1},{4,2}};
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
        collections(world,craft,output,expected);
        offsets(world,craft,output,expected);
    }
    private static void collections(World world,EntityGroundVehicle craft,File output,double[][] expected){
        boolean previous=VehicleCollision.legacyTerrainCollections;
        try(PrintWriter csv=new PrintWriter(new File(output,"terrain-collections.csv"))){
            csv.println("legacy,poses,samples,cpu_p50_us,allocated_p50_bytes,queries_per_step");
            for(boolean legacy:new boolean[]{true,false}){
                VehicleCollision.legacyTerrainCollections=legacy;long[] times=new long[24],allocations=new long[24];long queries=0;
                for(int round=-8;round<24;round++){
                    long cpu=BEAN.getCurrentThreadCpuTime(),bytes=BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());int pose=0;
                    for(float yaw:new float[]{-25,0,25})for(int direction:new int[]{-1,1}){double[] actual=step(world,craft,yaw,direction);queries+=craft.collision.terrainQueries;for(int axis=0;axis<3;axis++)if(Math.abs(actual[axis]-expected[pose][axis])>1E-9)throw new IllegalStateException("Terrain collections change movement legacy="+legacy+" pose="+pose);pose++;}
                    if(round>=0){times[round]=(BEAN.getCurrentThreadCpuTime()-cpu)/6;allocations[round]=(BEAN.getThreadAllocatedBytes(Thread.currentThread().getId())-bytes)/6;}
                }
                Arrays.sort(times);Arrays.sort(allocations);csv.printf(Locale.ROOT,"%s,6,24,%.3f,%d,%.2f%n",legacy,times[12]/1000D,allocations[12],queries/192D);csv.flush();
                System.out.println("[vandorlabs] terrain-collections PASS legacy="+legacy+" cpu-us="+times[12]/1000D+" allocated="+allocations[12]+" queries="+queries/192D);
            }
        }catch(IOException failure){throw new IllegalStateException(failure);}finally{VehicleCollision.legacyTerrainCollections=previous;}

    }
    private static void offsets(World world,EntityGroundVehicle craft,File output,double[][] expected){
        boolean previous=VehicleCollision.legacyObstacleOffsets;
        try(PrintWriter csv=new PrintWriter(new File(output,"terrain-offsets.csv"))){
            csv.println("legacy,poses,samples,cpu_p50_us,allocated_p50_bytes,queries_per_step");
            for(boolean legacy:new boolean[]{true,false}){
                VehicleCollision.legacyObstacleOffsets=legacy;long[] times=new long[24],allocations=new long[24];long queries=0;
                for(int round=-8;round<24;round++){
                    long cpu=BEAN.getCurrentThreadCpuTime(),bytes=BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());int pose=0;
                    for(float yaw:new float[]{-25,0,25})for(int direction:new int[]{-1,1}){double[] actual=step(world,craft,yaw,direction);queries+=craft.collision.terrainQueries;for(int axis=0;axis<3;axis++)if(Math.abs(actual[axis]-expected[pose][axis])>1E-9)throw new IllegalStateException("Terrain offsets change movement legacy="+legacy+" pose="+pose);pose++;}
                    if(round>=0){times[round]=(BEAN.getCurrentThreadCpuTime()-cpu)/6;allocations[round]=(BEAN.getThreadAllocatedBytes(Thread.currentThread().getId())-bytes)/6;}
                }
                Arrays.sort(times);Arrays.sort(allocations);csv.printf(Locale.ROOT,"%s,6,24,%.3f,%d,%.2f%n",legacy,times[12]/1000D,allocations[12],queries/192D);csv.flush();
                System.out.println("[vandorlabs] terrain-offsets PASS legacy="+legacy+" cpu-us="+times[12]/1000D+" allocated="+allocations[12]+" queries="+queries/192D);
            }
        }catch(IOException failure){throw new IllegalStateException(failure);}finally{VehicleCollision.legacyObstacleOffsets=previous;}

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
