package com.vandorlabs.blocks;

import com.vandorlabs.render.DiagonalScreenHull;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Native AABBs and hit results over the shared exact diagonal screen hull. */
public final class DiagonalScreenShape {
    private static final DiagonalScreenShape[][] SHAPES=new DiagonalScreenShape[6][2];
    static {for(int face=2;face<6;face++)for(int upper=0;upper<2;upper++)SHAPES[face][upper]=new DiagonalScreenShape(face,upper!=0);}
    public final AxisAlignedBB[] collision;
    public final double[][] outline;
    private final DiagonalScreenHull hull;
    public static DiagonalScreenShape of(EnumFacing facing,boolean upper){return SHAPES[facing.getIndex()][upper?1:0];}
    private DiagonalScreenShape(int face,boolean upper){
        hull=DiagonalScreenHull.of(face,upper);collision=new AxisAlignedBB[hull.collision.length];
        for(int i=0;i<collision.length;i++){DiagonalScreenHull.Bounds b=hull.collision[i];collision[i]=new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}outline=hull.outline;
    }
    public static Vec3d world(double x,double y,double z,EnumFacing facing){double[] p=DiagonalScreenHull.worldPoint(x,y,z,facing.getIndex());return new Vec3d(p[0],p[1],p[2]);}
    public RayTraceResult trace(BlockPos pos,Vec3d start,Vec3d end){
        DiagonalScreenHull.Hit hit=hull.trace(start.x-pos.getX(),start.y-pos.getY(),start.z-pos.getZ(),end.x-pos.getX(),end.y-pos.getY(),end.z-pos.getZ());
        return hit==null?null:new RayTraceResult(new Vec3d(pos.getX()+hit.x,pos.getY()+hit.y,pos.getZ()+hit.z),EnumFacing.getFacingFromVector((float)hit.nx,(float)hit.ny,(float)hit.nz),pos);
    }
}
