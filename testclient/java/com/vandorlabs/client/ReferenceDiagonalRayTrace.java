package com.vandorlabs.client;

import com.vandorlabs.render.TrapdoorGeometry;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Released face intersection and tie order, retained as an independent oracle. */
final class ReferenceDiagonalRayTrace {
    private static Vec3d vector(double[] v){return new Vec3d(v[0],v[1],v[2]);}
    static RayTraceResult trace(double[][] v,BlockPos pos,Vec3d start,Vec3d end) {
        Vec3d origin=start.subtract(new Vec3d(pos)),direction=end.subtract(start);double nearest=Double.POSITIVE_INFINITY;EnumFacing hitFace=null;
        for(int[] face:TrapdoorGeometry.FACES) {
            Vec3d a=vector(v[face[0]]),u=vector(v[face[1]]).subtract(a),w=vector(v[face[3]]).subtract(a),n=u.crossProduct(w);
            double denominator=n.dotProduct(direction);if(Math.abs(denominator)<1e-9)continue;
            double t=n.dotProduct(a.subtract(origin))/denominator;if(t<0 || t>1 || t>=nearest)continue;
            Vec3d hit=origin.add(direction.scale(t)).subtract(a);double uu=u.dotProduct(u),ww=w.dotProduct(w),uw=u.dotProduct(w),hu=hit.dotProduct(u),hw=hit.dotProduct(w),det=uu*ww-uw*uw;
            double s=(hu*ww-hw*uw)/det,r=(hw*uu-hu*uw)/det;
            if(s>=-1e-8 && s<=1+1e-8 && r>=-1e-8 && r<=1+1e-8){nearest=t;hitFace=EnumFacing.getFacingFromVector((float)n.x,(float)n.y,(float)n.z);}
        }
        return hitFace==null?null:new RayTraceResult(start.add(direction.scale(nearest)),hitFace,pos);
    }
}
