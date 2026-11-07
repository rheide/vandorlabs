package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Prepared XYZ/UV/normal records and packed ABGR colors; the consumer owns its live vertex format. */
public final class NativePreparedVertices {
    private static final class Scratch {final org.joml.Vector3f point=new org.joml.Vector3f(),normal=new org.joml.Vector3f();}
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    public static void emit(float[] data,int[] colors,int count,PoseStack.Pose pose,VertexConsumer target,int light,int overlay){
        var scratch=SCRATCH.get();for(int i=0;i<count;i++){int at=i*8,color=colors[i];pose.pose().transformPosition(data[at],data[at+1],data[at+2],scratch.point);pose.transformNormal(data[at+5],data[at+6],data[at+7],scratch.normal);target.addVertex(scratch.point.x,scratch.point.y,scratch.point.z).setColor(color&255,(color>>>8)&255,(color>>>16)&255,color>>>24).setUv(data[at+3],data[at+4]).setOverlay(overlay).setLight(light).setNormal(scratch.normal.x,scratch.normal.y,scratch.normal.z);}
    }
    private NativePreparedVertices(){}
}
