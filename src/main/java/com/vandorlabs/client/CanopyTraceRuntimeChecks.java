package com.vandorlabs.client;

import com.vandorlabs.canopy.CanopyMesh.Face;
import static com.vandorlabs.canopy.CanopyMesh.*;
import java.util.List;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Independent vector-based reference retained only for opt-in ray parity checks. */
final class CanopyTraceRuntimeChecks {
    static RayTraceResult reference(List<Face> faces,String kind,int width,double progress,EnumFacing facing,BlockPos pos,Vec3d start,Vec3d end) {
        boolean posed="posed".equals(kind);
        if(posed){EnumFacing inverse=facing==EnumFacing.EAST?EnumFacing.WEST:facing==EnumFacing.WEST?EnumFacing.EAST:facing;start=rotate(start.subtract(new Vec3d(pos)),inverse);end=rotate(end.subtract(new Vec3d(pos)),inverse);}
        Vec3d direction=end.subtract(start);double nearest=Double.POSITIVE_INFINITY;RayTraceResult result=null;
        for(Face face:faces) {
            Vec3d[] vertices=posed?face.vertices:new Vec3d[face.vertices.length];
            if(!posed)for(int i=0;i<vertices.length;i++)vertices[i]=rotate(pose(face.vertices[i],kind,width,progress,face.group.equals("shell")),facing).add(new Vec3d(pos));
            for(int i=1;i<vertices.length-1;i++) {
                Vec3d a=vertices[0],edge1=vertices[i].subtract(a),edge2=vertices[i+1].subtract(a),cross=direction.crossProduct(edge2);
                double determinant=edge1.dotProduct(cross);if(Math.abs(determinant)<1e-10)continue;
                double inverse=1/determinant;Vec3d relative=start.subtract(a);
                double u=relative.dotProduct(cross)*inverse;if(u< -1e-8 || u>1+1e-8)continue;
                Vec3d q=relative.crossProduct(edge1);double v=direction.dotProduct(q)*inverse;
                if(v< -1e-8 || u+v>1+1e-8)continue;
                double distance=edge2.dotProduct(q)*inverse;
                if(distance<0 || distance>1 || distance>=nearest)continue;
                nearest=distance;Vec3d normal=edge1.crossProduct(edge2);
                if(normal.dotProduct(direction)>0)normal=normal.scale(-1);
                Vec3d hit=start.add(direction.scale(distance));
                if(posed){hit=rotate(hit,facing).add(new Vec3d(pos));normal=rotate(normal.addVector(.5,0,.5),facing).subtract(.5,0,.5);}
                result=new RayTraceResult(hit,EnumFacing.getFacingFromVector((float)normal.x,(float)normal.y,(float)normal.z),pos);
            }
        }
        return result;
    }

    static void allocations(List<Face> faces,int width,EnumFacing facing,BlockPos pos){
        Object bean=java.lang.management.ManagementFactory.getThreadMXBean();if(!(bean instanceof com.sun.management.ThreadMXBean))return;
        com.sun.management.ThreadMXBean memory=(com.sun.management.ThreadMXBean)bean;if(!memory.isThreadAllocatedMemorySupported())return;
        if(!memory.isThreadAllocatedMemoryEnabled())memory.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().getId();Vec3d start=new Vec3d(pos).addVector(-2,1,-2),end=start.addVector(12,0,12);
        for(int i=0;i<12;i++)trace(faces,"posed",width,0,facing,pos,start,end);
        long before=memory.getThreadAllocatedBytes(thread);for(int i=0;i<16;i++)trace(faces,"posed",width,0,facing,pos,start,end);long optimized=memory.getThreadAllocatedBytes(thread)-before;
        before=memory.getThreadAllocatedBytes(thread);for(int i=0;i<16;i++)reference(faces,"posed",width,0,facing,pos,start,end);long original=memory.getThreadAllocatedBytes(thread)-before;
        // HotSpot can scalar-replace the vector reference after compilation.
        // Require bounded per-ray allocation even when that reference is already optimized.
        if(optimized>16*2048L||original>16*2048L&&optimized*4>=original)throw new IllegalStateException("canopy trace allocation regression: "+optimized+" / "+original);
        System.out.println("[vandorlabs][reprolab] canopy-trace-allocation PASS rays=16 bytes="+optimized+" vector-reference="+original);
    }
    private CanopyTraceRuntimeChecks(){}
}
