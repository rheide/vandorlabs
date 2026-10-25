package com.vandorlabs.programmer;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Authored screen plane and square-pixel controls, independent of rendering APIs. */
public final class ProgrammerJournalSurface {
    public static final int WIDTH=320,HEIGHT=136,BUTTON_TOP=116,BUTTON_BOTTOM=132;
    public static final int PREVIOUS_LEFT=6,PREVIOUS_RIGHT=76,NEXT_LEFT=244,NEXT_RIGHT=314;
    private static final Vec3d[] NATIVE=ProgrammerGeometry.screen();
    public static final Vec3d ORIGIN=NATIVE[1],ACROSS=NATIVE[0].subtract(NATIVE[1]),DOWN=NATIVE[2].subtract(NATIVE[1]);
    public static final Vec3d NORMAL=DOWN.crossProduct(ACROSS).normalize();
    public static Vec3d point(double u,double v){return ORIGIN.add(ACROSS.scale(u/WIDTH)).add(DOWN.scale(v/HEIGHT));}
    public static Vec3d world(ProgrammerTile tile,double u,double v){Vec3d p=point(u,v);ProgrammerTransform.Affine a=ProgrammerGeometry.transform(tile.getProgrammerFacing());BlockPos origin=tile.getProgrammerOrigin();return new Vec3d(origin.getX()+a.x(p.x,p.z),origin.getY()+p.y,origin.getZ()+a.z(p.x,p.z));}
    public static double[] hit(BlockPos origin,EnumFacing facing,Vec3d start,Vec3d end){
        ProgrammerTransform.Affine a=ProgrammerGeometry.transform(facing);Vec3d from=ProgrammerGeometry.local(start,origin,a),to=ProgrammerGeometry.local(end,origin,a),ray=to.subtract(from);double denominator=ray.dotProduct(NORMAL);
        if(denominator>=-1e-9)return null;double t=ORIGIN.subtract(from).dotProduct(NORMAL)/denominator;if(t<0||t>1)return null;
        Vec3d relative=from.add(ray.scale(t)).subtract(ORIGIN);double u=relative.dotProduct(ACROSS)/ACROSS.lengthSquared()*WIDTH,v=relative.dotProduct(DOWN)/DOWN.lengthSquared()*HEIGHT;
        return u>=0&&u<=WIDTH&&v>=0&&v<=HEIGHT?new double[]{u,v}:null;
    }
    public static int action(double u,double v){if(v<BUTTON_TOP||v>=BUTTON_BOTTOM)return 0;return u>=PREVIOUS_LEFT&&u<PREVIOUS_RIGHT?-1:u>=NEXT_LEFT&&u<NEXT_RIGHT?1:0;}
    private ProgrammerJournalSurface(){}
}
