package com.vandorlabs.client;

import com.vandorlabs.render.DiagonalTrapdoorGeometry;
import net.minecraft.util.math.*;
import java.util.Random;

/** Mesh hits, misses, edge contacts and equal-distance face precedence. */
final class DiagonalRayTraceChecks {
    static void run() {
        Random random=new Random(0x524159);int checks=0;
        for(int mode=0;mode<3;mode++)for(int turns=0;turns<4;turns++)for(boolean inverted:new boolean[]{false,true})
            for(boolean sliding:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true})
                for(double pose:new double[]{0,.125,.5,.875,1}) {
                    double[][] corners=DiagonalTrapdoorGeometry.corners(mode,inverted,turns,sliding,reverse,pose,
                            reverse?1.5:-.5,2.25,inverted?-1:1,false);
                    for(BlockPos pos:new BlockPos[]{BlockPos.ORIGIN,new BlockPos(-17,64,16),new BlockPos(29999990,250,-29999990)}) {
                        double[] bounds=DiagonalTrapdoorGeometry.bounds(corners);
                        Vec3d center=new Vec3d(pos.getX()+(bounds[0]+bounds[3])*.5,pos.getY()+(bounds[1]+bounds[4])*.5,pos.getZ()+(bounds[2]+bounds[5])*.5);
                        for(int ray=0;ray<12;ray++) {
                            Vec3d offset=new Vec3d(random.nextDouble()*6-3,random.nextDouble()*6-3,random.nextDouble()*6-3);
                            Vec3d start=center.add(offset),end=center.subtract(offset);
                            if((ray&1)!=0)start=start.addVector(3,2,-2);
                            same(corners,pos,start,end);checks++;
                        }
                        for(double[] vertex:corners) {
                            Vec3d at=new Vec3d(pos.getX()+vertex[0],pos.getY()+vertex[1],pos.getZ()+vertex[2]);
                            same(corners,pos,at,at);same(corners,pos,center,at);same(corners,pos,at,center);checks+=3;
                        }
                    }
                }
        System.out.println("PASS: "+checks+" diagonal ray intersections preserve exact hits, misses, edge contacts and face precedence");
    }
    private static void same(double[][] corners,BlockPos pos,Vec3d start,Vec3d end) {
        RayTraceResult expected=ReferenceDiagonalRayTrace.trace(corners,pos,start,end);
        RayTraceResult actual=com.vandorlabs.render.DiagonalTrapdoorRayTrace.trace(corners,pos,start,end);
        if(expected==null?actual!=null:actual==null || expected.sideHit!=actual.sideHit
                || !expected.getBlockPos().equals(actual.getBlockPos()) || !expected.hitVec.equals(actual.hitVec))
            throw new AssertionError("diagonal ray hit differs for "+start+" -> "+end);
    }
}
