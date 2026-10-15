package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.ramp.RampGeometry;
import com.vandorlabs.tiles.TileEntityControlledRamp;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.lang.reflect.Method;
import java.util.*;

/** Real-world settled geometry reuse, direct edits and paired geometry-only CPU timing. */
final class RampGeometryCacheRuntimeChecks {
    private static volatile int sink;
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("ramp geometry cache: "+message);}
    private static String shape(List<RampGeometry.Box> boxes){StringBuilder text=new StringBuilder();for(RampGeometry.Box box:boxes)text.append(box.minX).append(',').append(box.minY).append(',').append(box.minZ).append(',').append(box.maxX).append(',').append(box.maxY).append(',').append(box.maxZ).append(';');return text.toString();}
    private static void freshMatches(TileEntityControlledRamp tile){
        TileEntityControlledRamp fresh=new TileEntityControlledRamp();fresh.setWorld(tile.getWorld());fresh.setPos(tile.getPos());fresh.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
        require(shape(tile.geometry(.5)).equals(shape(fresh.geometry(.5))),"cached geometry differs from a fresh saved cell");
    }
    static void run(World world){
        BlockPos pos=new BlockPos(20,80,20);IBlockState previous=world.getBlockState(pos);
        try{
            world.setBlockState(pos,ModBlocks.CONTROLLED_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),2);
            TileEntityControlledRamp tile=new TileEntityControlledRamp();tile.setWorld(world);tile.setPos(pos);
            tile.sourceY=pos.getY();tile.row=2;tile.length=16;tile.extendSegments=true;tile.treadPixels=2;tile.setHalfOffsets(0,6);
            for(int row=0;row<16;row++)for(int lane=0;lane<2;lane++)tile.origins.add(pos.add(lane,0,row-2));
            tile.move(true,1,world.getTotalWorldTime(),60,false);
            List<RampGeometry.Box> settled=tile.geometry(0);require(!settled.isEmpty(),"fixture is empty");
            require(settled==tile.geometry(.9),"settled rendering rebuilt geometry");
            require(tile.boxes(0)==tile.boxes(.9),"settled collision rebuilt boxes");freshMatches(tile);
            tile.interpolation=1;require(settled!=tile.geometry(0),"curve edit did not invalidate");freshMatches(tile);
            tile.setHalfOffsets(-1,5);freshMatches(tile);tile.low=.25;tile.high=.75;freshMatches(tile);
            tile.treadPixels=4;tile.travelAxis=RampGeometry.LEFT;freshMatches(tile);
            tile.extendSegments=false;tile.elevator=true;tile.speed=0;freshMatches(tile);
            tile.origins.set(0,tile.origins.get(0).east());freshMatches(tile);
            tile.origins.add(pos.west());freshMatches(tile);tile.origins.remove(1);freshMatches(tile);
            List<RampGeometry.Box> ordinary=tile.geometry(0);tile.moat=true;
            require(ordinary!=tile.geometry(0),"Moat mode edit did not invalidate geometry");freshMatches(tile);
            require(tile.geometry(0)==tile.geometry(.9),"settled Moat geometry rebuilt");tile.moat=false;
            world.setBlockState(pos,ModBlocks.CONTROLLED_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.WEST),2);freshMatches(tile);
            tile.move(false,1,world.getTotalWorldTime(),60,true);
            require(tile.geometry(.1)!=tile.geometry(.9),"moving geometry reused a settled pose");freshMatches(tile);
            tile.move(false,0,world.getTotalWorldTime(),60,false);freshMatches(tile);
            require(tile.geometry(0)==tile.geometry(.9),"settled retracted geometry was not cached");
            world.setBlockState(pos,ModBlocks.CONTROLLED_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),2);
            tile.origins.clear();for(int row=0;row<16;row++)for(int lane=0;lane<2;lane++)tile.origins.add(pos.add(lane,0,row-2));
            tile.low=0;tile.high=1;tile.elevator=false;tile.extendSegments=true;tile.travelAxis=RampGeometry.VERTICAL;tile.interpolation=0;tile.treadPixels=2;tile.speed=1;tile.setHalfOffsets(0,6);tile.move(true,1,world.getTotalWorldTime(),60,false);
            benchmark(tile);
            System.out.println("[vandorlabs][reprolab] ramp-geometry-cache PASS settled-open-closed collision-reuse direct-settings origins facing reload moving-poses");
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        finally{world.setBlockState(pos,previous,2);}
    }
    @SuppressWarnings("unchecked") private static void benchmark(TileEntityControlledRamp tile)throws ReflectiveOperationException{
        Method baseline=TileEntityControlledRamp.class.getDeclaredMethod("geometryUncached",EnumFacing.class,double.class);baseline.setAccessible(true);
        double[][] times=new double[2][21];long[][] allocations=new long[2][21];
        tile.geometry(0);
        for(int sample=-10;sample<21;sample++)for(int order=0;order<2;order++){
            int algorithm=(sample+order)&1,sum=0;long bytes=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
            for(int query=0;query<512;query++)sum+=(algorithm==0?(List<RampGeometry.Box>)baseline.invoke(tile,EnumFacing.SOUTH,1D):tile.geometry(.5)).size();
            long elapsed=System.nanoTime()-start,allocated=BenchmarkAllocations.currentThreadBytes()-bytes;sink=sum;
            if(sample>=0){times[algorithm][sample]=elapsed/1E6;allocations[algorithm][sample]=allocated;}
        }
        for(int algorithm=0;algorithm<2;algorithm++){Arrays.sort(times[algorithm]);Arrays.sort(allocations[algorithm]);System.out.printf(Locale.ROOT,"[vandorlabs][reprolab] ramp-settled-geometry-benchmark algorithm=%s queries=512 origins=32 median-ms=%.6f allocated-bytes=%d%n",algorithm==0?"uncached":"cached",times[algorithm][10],allocations[algorithm][10]);}
    }
}
