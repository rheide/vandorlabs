package com.vandorlabs.client;

import com.vandorlabs.tiles.TrapdoorRayCandidates;
import net.minecraft.util.math.*;
import java.util.*;

/** Geometry-only caching preserves candidate coverage and exact hit precedence. */
final class TrapdoorRayCandidateChecks {
    static void run() {
        Random random=new Random(812381);int cases=0;
        for(int i=0;i<1500;i++) {
            double x=i%5==0?29999980:random.nextDouble()*80-40;
            Vec3d start=new Vec3d(x,random.nextDouble()*270-5,random.nextDouble()*80-40);
            Vec3d end=start.addVector(random.nextDouble()*12-6,random.nextDouble()*12-6,random.nextDouble()*12-6);
            compare(start,end);cases++;
        }
        for(double coordinate:new double[]{-1,-.0000001,0,1,16,29999998})for(int axis=0;axis<3;axis++)
            for(double length:new double[]{-7,-1,0,1,7}) {
                Vec3d start=new Vec3d(coordinate,coordinate,coordinate);
                compare(start,start.addVector(axis==0?length:0,axis==1?length:0,axis==2?length:0));cases++;
            }
        try {
            java.lang.reflect.Field field=TrapdoorRayCandidates.class.getDeclaredField("PATHS");field.setAccessible(true);
            com.google.common.cache.Cache<?,List<BlockPos>> cache=(com.google.common.cache.Cache<?,List<BlockPos>>)field.get(null);
            cache.cleanUp();int positions=0;for(List<BlockPos> path:cache.asMap().values())positions+=path.size();
            if(positions>32768)throw new AssertionError("unbounded ray candidate positions");
        } catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        System.out.println("PASS: "+cases+" exact ray candidate lists, cold/warm queries, immutable storage and bounded position count");
    }
    private static void compare(Vec3d start,Vec3d end) {
        List<BlockPos> expected=ReferenceTrapdoorRayCandidates.positions(start,end);
        for(int repeat=0;repeat<2;repeat++) {
            List<BlockPos> actual=TrapdoorRayCandidates.positions(start,end);
            if(!expected.equals(actual))throw new AssertionError("ray coverage/order changed for "+start+" to "+end);
            try{actual.add(BlockPos.ORIGIN);throw new AssertionError("mutable cached positions");}
            catch(UnsupportedOperationException expectedFailure){}
        }
    }
}
