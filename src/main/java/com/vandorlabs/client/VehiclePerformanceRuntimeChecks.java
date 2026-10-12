package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Four bounded workloads, maximum 2048 cells; no fleet stress benchmark. */
final class VehiclePerformanceRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("vehicle performance: "+message);}
    static void run(Minecraft mc)throws Exception {
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);
            for(int x=-3;x<=2;x++)for(int z=-3;z<=2;z++)world.getChunkFromChunkCoords(x,z);
            for(BlockPos at:BlockPos.getAllInBox(new BlockPos(-24,100,-24),new BlockPos(24,100,24)))world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
            for(int kind=0;kind<4;kind++) {
                List<VehicleStructure.Cell> cells=new ArrayList<>();int width=kind==0?8:16,height=kind==1?8:kind==2?16:1,depth=kind==0?4:16;
                for(int x=0;x<width;x++)for(int y=0;y<height;y++)for(int z=0;z<depth;z++) {
                    if(kind==2 && x>0 && x<15 && y>0 && y<15 && z>0 && z<15)continue;
                    cells.add(new VehicleStructure.Cell(new BlockPos(x,y,z),x==0 && y==0 && z==0?ModBlocks.PILOT_SEAT.getDefaultState():kind==3 && (x+z)%2==0?Blocks.STONE_SLAB.getDefaultState():Blocks.STONE.getDefaultState(),null));
                }
                VehicleStructure hull=new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1);
                EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(hull);craft.setPosition(-8,101,-8);
                long[] samples=new long[24];int queries=0;
                for(int n=0;n<samples.length;n++) {
                    long start=System.nanoTime();craft.collision.begin(world,craft);
                    try {
                        float yaw=craft.collision.turn(world,craft,-8,101,-8,n%2==0?0:45,1);
                        double down=craft.collision.clip(world,craft,-8,101,-8,-.04,1,yaw);
                        require(Math.abs(down)<1e-6,"floor support retained at both headings");
                        craft.collision.clip(world,craft,-8,101,-8,.28,0,yaw);
                        queries+=craft.collision.terrainQueries;
                    }finally{craft.collision.end();}
                    samples[n]=System.nanoTime()-start;
                }
                Arrays.sort(samples);
                System.out.println("[vandorlabs][reprolab] vehicle-profile cells="+cells.size()+" boxes="+craft.collision.boxes.size()+" samples=24 median-us="+samples[12]/1000+" p95-us="+samples[22]/1000+" p99-us="+samples[23]/1000+" terrain-queries/step="+queries/24);
            }
            EntityGroundVehicle indexed=new EntityGroundVehicle(world);
            List<VehicleStructure.Cell> cells=Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null),new VehicleStructure.Cell(new BlockPos(40,20,40),Blocks.STONE.getDefaultState(),null));
            indexed.install(new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));indexed.setPosition(-64,110,-64);
            require(VehicleLookup.loaded(world).isEmpty(),"temporary validation craft excluded from registry");
            world.getChunkFromChunkCoords(-4,-4);world.getChunkFromChunkCoords(4,4);
            require(world.spawnEntity(indexed),"index fixture spawn");
            require(VehicleLookup.near(world,new AxisAlignedBB(-24,130,-24,-23,131,-23)).contains(indexed),"negative coordinates and distant hull sections indexed");
            indexed.setPosition(64,110,64);
            require(!VehicleLookup.near(world,new AxisAlignedBB(-24,130,-24,-23,131,-23)).contains(indexed),"moving craft removes old buckets");
            require(VehicleLookup.near(world,new AxisAlignedBB(104,130,104,105,131,105)).contains(indexed),"moving craft updates far buckets");
            indexed.onRemovedFromWorld();require(!VehicleLookup.loaded(world).contains(indexed),"chunk removal unregisters craft");indexed.onAddedToWorld();require(VehicleLookup.loaded(world).contains(indexed),"chunk re-add registers craft");
            indexed.setDead();require(!VehicleLookup.loaded(world).contains(indexed),"death unregisters craft");
            VehicleDoorMatrixChecks.run(world);
            System.out.println("[vandorlabs][reprolab] vehicle-performance PASS bounded-2048-workloads registry-lifecycle negative-coordinates far-hull support");
        }).get();
    }
}
