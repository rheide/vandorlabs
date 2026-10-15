package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.vehicle.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Replay terrain is reused only inside a synchronous batch, including exceptional exits. */
final class VehicleTerrainReplayRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("vehicle replay terrain: "+message);}
    private static double step(WorldServer world,EntityGroundVehicle craft,BlockPos origin){craft.collision.begin(world,craft);try{return craft.collision.clip(world,craft,origin.getX(),origin.getY(),origin.getZ(),-.3,1,0);}finally{craft.collision.end();}}
    static void run(WorldServer world){
        List<VehicleStructure.Cell> cells=new ArrayList<>();for(int x=0;x<40;x++)cells.add(new VehicleStructure.Cell(new BlockPos(x,0,(x%2)*2),x==0?ModBlocks.PILOT_SEAT.getDefaultState():Blocks.STONE.getDefaultState(),null));
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));BlockPos origin=new BlockPos(340,200,240),floor=origin.add(1,-1,2);craft.setPosition(origin.getX(),origin.getY(),origin.getZ());require(craft.collision.boxes.size()>32,"fixture exercises clustered terrain cache");require(world.isAirBlock(floor),"isolated test floor starts empty");
        try{
            double before=step(world,craft,origin);int uncached=craft.collision.terrainQueries;require(before==-.3&&uncached>0,"ordinary uncached empty-space step");
            final int[] queries={0,0};craft.collision.withTerrainCache(world,craft,()->{require(step(world,craft,origin)==before,"first replay matches ordinary step");queries[0]=craft.collision.terrainQueries;require(step(world,craft,origin)==before,"second replay preserves exact movement");queries[1]=craft.collision.terrainQueries;});require(queries[0]>0&&queries[1]==0,"second replay reuses terrain without dropping physics");
            world.setBlockState(floor,Blocks.STONE.getDefaultState(),2);craft.collision.withTerrainCache(world,craft,()->{require(step(world,craft,origin)==0,"next batch sees newly placed support");require(craft.collision.terrainQueries>0,"new batch never retains prior-world terrain");});
            world.setBlockToAir(floor);RuntimeException marker=new RuntimeException("expected replay fixture exception");try{craft.collision.withTerrainCache(world,craft,()->{require(step(world,craft,origin)==-.3,"exception batch sees removed support");throw marker;});throw new IllegalStateException("exception did not escape replay");}catch(RuntimeException expected){require(expected==marker,"original replay exception propagates");}
            world.setBlockState(floor,Blocks.STONE.getDefaultState(),2);require(step(world,craft,origin)==0&&craft.collision.terrainQueries>0,"exception finally clears replay cache before next ordinary step");
            System.out.println("[vandorlabs][reprolab] vehicle-replay-terrain PASS identical-full-steps reused-sections changed-obstacles-between-batches exception-finally no-leaked-cache queries="+uncached+"/"+queries[0]+"/"+queries[1]);
        }finally{world.setBlockToAir(floor);craft.setDead();}
    }
}
