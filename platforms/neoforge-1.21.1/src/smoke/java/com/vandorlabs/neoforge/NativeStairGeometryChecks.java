package com.vandorlabs.neoforge;

import com.vandorlabs.render.StairGeometry;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Actual native boxes against the frozen pre-extraction Forge scalar fixture. */
public final class NativeStairGeometryChecks {
    private static MessageDigest digest;private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void put(double value){long bits=Double.doubleToRawLongBits(value);for(int shift=56;shift>=0;shift-=8)digest.update((byte)(bits>>>shift));}
    private static void put(int value){for(int shift=24;shift>=0;shift-=8)digest.update((byte)(value>>>shift));}
    private static double volume(AABB b){return (b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ);}
    private static void union(List<AABB> boxes,VoxelShape shape){
        double expected=0,actual=0;for(AABB b:boxes)expected+=volume(b);var merged=shape.toAabbs();for(AABB b:merged)actual+=volume(b);need(Double.doubleToLongBits(actual)==Double.doubleToLongBits(expected),"exact dyadic union volume");
        for(AABB b:boxes)need(merged.stream().anyMatch(other->other.contains(b.getCenter())),"every component occupied");
    }
    private static volatile Object consumed;
    private static void warm(){
        var form=StairGeometry.Shape.INNER_LEFT;for(int i=0;i<10000;i++)consumed=NativeStairGeometry.boxes(Direction.NORTH,true,form,65);
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(id);
        for(int i=0;i<20000;i++)consumed=NativeStairGeometry.boxes(Direction.NORTH,true,form,65);long bytes=bean.getThreadAllocatedBytes(id)-before;need(bytes<20000,"cached boxes rebuild allocations "+bytes);System.out.println("Native stair warmed boxes="+(bytes/20000D)+" bytes/query");
    }
    public static void main(String[] ignored)throws Exception{
        digest=MessageDigest.getInstance("SHA-256");int poses=0,boxCount=0;Direction[] horizontal={Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST};
        for(Direction face:horizontal)for(boolean upper:new boolean[]{true,false})for(var form:StairGeometry.Shape.values())for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++){
            int settings=tread|(roll<<5);put(face.get3DDataValue());put(upper?0:1);put(form.ordinal());put(tread);put(roll);var boxes=NativeStairGeometry.boxes(face,upper,form,settings);need(boxes==NativeStairGeometry.boxes(face,upper,form,settings),"stable cached list");
            for(AABB b:boxes){put(b.minX);put(b.minY);put(b.minZ);put(b.maxX);put(b.maxY);put(b.maxZ);boxCount++;}for(Direction side:Direction.values()){put(NativeStairGeometry.unrolledFace(face,upper,roll,side).get3DDataValue());put(NativeStairGeometry.solidFace(face,upper,form,settings,side)?1:0);}
            var shape=NativeStairGeometry.shape(face,upper,form,settings);union(boxes,shape);need(shape.bounds().equals(NativeStairGeometry.bounds(face,upper,form,settings)),"union bounds");poses++;
        }
        double[] values={-0D,0D,1e-7,.1,.5,.9,1D};for(Direction face:horizontal)for(boolean upper:new boolean[]{true,false})for(int roll=0;roll<3;roll++)for(double x:values)for(double y:values)for(double z:values){var p=NativeStairGeometry.transform(new Vec3(x,y,z),face,upper,roll);put(p.x);put(p.y);put(p.z);}
        for(var form:StairGeometry.Shape.values())for(int count:new int[]{1,2,4,8,16})for(int x=0;x<count;x++)for(int z=0;z<count;z++)put(NativeStairGeometry.height(x,z,count,form));
        String sha=HexFormat.of().formatHex(digest.digest());need(sha.equals("cd4b14c2348647ba3595a5541fa2ec30506ef359f91a90ef3660979662025b31"),"Forge raw golden "+sha);need(poses==600&&boxCount==40920,"pose/box coverage");long cost=NativeStairGeometry.cacheCost(),retained=NativeStairGeometry.cachedPoses();need(cost<=NativeStairGeometry.CACHE_BUDGET,"weighted cache bound");need(retained<600,"variable cost eviction exercised");
        try{NativeStairGeometry.boxes(Direction.NORTH,false,StairGeometry.Shape.STRAIGHT,8).clear();throw new AssertionError("mutable list");}catch(UnsupportedOperationException expected){}
        for(int invalid:new int[]{0,3,17,31,96,128,-1})try{NativeStairGeometry.shape(Direction.NORTH,false,StairGeometry.Shape.STRAIGHT,invalid);throw new AssertionError("invalid settings "+invalid);}catch(IllegalArgumentException expected){}
        warm();exactCoordinates();concurrent();NativeStairGeometry.clearCache();need(NativeStairGeometry.cachedPoses()==0&&NativeStairGeometry.cacheCost()==NativeStairGeometry.CACHE_OVERHEAD,"clear releases cache");System.out.println("[vandorlabs][stair-geometry] PASS poses="+poses+" boxes="+boxCount+" SHA256="+sha+" checks="+checks+" cacheBytes="+cost+" retainedPoses="+retained);
    }
    private static void concurrent()throws Exception{
        var workers=java.util.concurrent.Executors.newFixedThreadPool(4);
        try{
            var jobs=new ArrayList<java.util.concurrent.Callable<Void>>();
            for(int worker=0;worker<4;worker++){final int offset=worker;jobs.add(()->{
                Direction[] faces={Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
                for(int i=0;i<600;i++){int tread=1<<((i+offset)%5),roll=i%3;var face=faces[(i+offset)%4];var form=StairGeometry.Shape.values()[i%5];boolean upper=(i&1)==0;var boxes=NativeStairGeometry.boxes(face,upper,form,tread|(roll<<5));var shape=NativeStairGeometry.shape(face,upper,form,tread|(roll<<5));if(boxes.isEmpty()||shape.isEmpty()||NativeStairGeometry.cacheCost()>NativeStairGeometry.CACHE_BUDGET)throw new AssertionError("concurrent bounded publication");}
                return null;
            });}
            for(var result:workers.invokeAll(jobs))result.get();
        }finally{workers.shutdownNow();}
        need(NativeStairGeometry.cacheCost()<=NativeStairGeometry.CACHE_BUDGET,"concurrent cache limit");
    }
    private static void exactCoordinates(){
        double split=.5+1e-9;var boxes=List.of(new AABB(0,0,0,.5,1,1),new AABB(split,0,0,1,1,1));var exact=NativeVoxelShapes.union(boxes,1024*1024);need(exact.getCoords(Direction.Axis.X).contains(.5)&&exact.getCoords(Direction.Axis.X).contains(split),"close coordinates retained");
        need(NativeVoxelShapes.union(List.of(new AABB(0,0,0,1e-8,1,1)),1024*1024).isEmpty(),"existing sub1e-7 component filter");try{NativeVoxelShapes.union(boxes,1);throw new AssertionError("grid budget not checked");}catch(IllegalStateException expected){}
    }
}
