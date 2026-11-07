package com.vandorlabs.programmer;

import java.io.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Shared cached authored planes: client selection and server socket clicks use identical rays. */
public final class ProgrammerGeometry {
    private static final ProgrammerPicking.Mesh MESH=load();
    private static ProgrammerPicking.Mesh load(){
        try{return ProgrammerPicking.read(ProgrammerGeometry.class.getResourceAsStream("/assets/vandorlabs/models/block/data_chip_programmer/ready/parts/static.obj"),ProgrammerGeometry.class.getResourceAsStream("/assets/vandorlabs/models/block/data_chip_programmer/ready/parts/screen.obj"));}
        catch(IOException|RuntimeException failure){throw new IllegalStateException("Cannot load programmer selection geometry",failure);}
    }
    public static ProgrammerTransform.Affine transform(EnumFacing facing){return ProgrammerTransform.of(ProgrammerTransform.Facing.valueOf(facing.name()),2,1);}
    public static Vec3d local(Vec3d point,BlockPos origin,ProgrammerTransform.Affine a){double x=point.x-origin.getX()-a.offsetX,z=point.z-origin.getZ()-a.offsetZ;return new Vec3d(a.xx*x+a.zx*z,point.y-origin.getY(),a.xz*x+a.zz*z);}
    public static Vec3d[] screen(){double[][] points=MESH.screen();Vec3d[] result=new Vec3d[points.length];for(int i=0;i<points.length;i++)result[i]=new Vec3d(points[i][0],points[i][1],points[i][2]);return result;}
    public static RayTraceResult trace(BlockPos cell,BlockPos origin,EnumFacing facing,Vec3d start,Vec3d end){
        ProgrammerPicking.Hit hit=MESH.trace(cell.getX(),cell.getY(),cell.getZ(),origin.getX(),origin.getY(),origin.getZ(),ProgrammerTransform.Facing.valueOf(facing.name()),start.x,start.y,start.z,end.x,end.y,end.z);
        if(hit==null)return null;
        RayTraceResult result=new RayTraceResult(new Vec3d(hit.x,hit.y,hit.z),EnumFacing.getFront(hit.side),cell);result.subHit=hit.slot;result.hitInfo=hit.group;return result;
    }
    private ProgrammerGeometry(){}
}
