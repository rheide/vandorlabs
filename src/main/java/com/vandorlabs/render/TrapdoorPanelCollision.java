package com.vandorlabs.render;

import com.vandorlabs.tiles.TileEntityProgrammableTrapdoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Endpoint collision slices and exact convex-face selection for moving cut panels. */
public final class TrapdoorPanelCollision {
    private TrapdoorPanelCollision(){}
    public static AxisAlignedBB bounds(TileEntityProgrammableTrapdoor tile,IBlockState state,double pose) {
        AxisAlignedBB result=null;
        for(List<double[][]> faces:tile.panelFaces(state,pose))if(!faces.isEmpty()) {
            double[] b=PanelPolyhedron.bounds(faces);AxisAlignedBB box=new AxisAlignedBB(b[0],b[1],b[2],b[3],b[4],b[5]);result=result==null?box:result.union(box);
        }
        return result==null?new AxisAlignedBB(0,0,0,0,0,0):result;
    }
    public static void add(TileEntityProgrammableTrapdoor tile,IBlockState state,double pose,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> boxes) {
        for(AxisAlignedBB box:tile.panelCollisionBoxes(state,pose))if(box.offset(pos).intersects(query))boxes.add(box.offset(pos));
    }
    public static List<AxisAlignedBB> build(List<List<double[][]>> panels,PanelMotion motion) {
        List<AxisAlignedBB> result=new ArrayList<>();
        for(List<double[][]> faces:panels) {
            if(faces.isEmpty())continue;
            double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            for(double[][] face:faces)for(double[] point:face){double v=PanelPolyhedron.dot(point,motion.v);low=Math.min(low,v);high=Math.max(high,v);}
            for(int slice=0;slice<32;slice++) {
                double a=low+(high-low)*slice/32,b=low+(high-low)*(slice+1)/32;
                List<double[][]> clipped=PanelPolyhedron.clip(PanelPolyhedron.clip(faces,new double[]{motion.v[0],motion.v[1],motion.v[2],-a}),new double[]{-motion.v[0],-motion.v[1],-motion.v[2],b});
                if(clipped.isEmpty())continue;double[] bounds=PanelPolyhedron.bounds(clipped);
                if(bounds[3]-bounds[0]>1e-8 && bounds[4]-bounds[1]>1e-8 && bounds[5]-bounds[2]>1e-8)
                    result.add(new AxisAlignedBB(bounds[0],bounds[1],bounds[2],bounds[3],bounds[4],bounds[5]));
            }
        }
        return Collections.unmodifiableList(result);
    }
    public static RayTraceResult trace(TileEntityProgrammableTrapdoor tile,IBlockState state,double pose,BlockPos pos,Vec3d start,Vec3d end) {
        double[] origin={start.x-pos.getX(),start.y-pos.getY(),start.z-pos.getZ()},ray={end.x-start.x,end.y-start.y,end.z-start.z};
        double nearest=Double.POSITIVE_INFINITY;double[] normal=null;
        for(List<double[][]> faces:tile.panelFaces(state,pose))for(double[][] face:faces)for(int i=1;i+1<face.length;i++) {
            double[] a=face[0],b=face[i],c=face[i+1],u=delta(b,a),v=delta(c,a),n=PanelPolyhedron.cross(u,v);
            double denominator=PanelPolyhedron.dot(n,ray);if(Math.abs(denominator)<1e-10)continue;
            double t=PanelPolyhedron.dot(n,delta(a,origin))/denominator;if(t<0 || t>1 || t>=nearest)continue;
            double[] hit={origin[0]+t*ray[0]-a[0],origin[1]+t*ray[1]-a[1],origin[2]+t*ray[2]-a[2]};
            double uu=PanelPolyhedron.dot(u,u),vv=PanelPolyhedron.dot(v,v),uv=PanelPolyhedron.dot(u,v),hu=PanelPolyhedron.dot(hit,u),hv=PanelPolyhedron.dot(hit,v),det=uu*vv-uv*uv;
            if(det<1e-18)continue;double s=(hu*vv-hv*uv)/det,r=(hv*uu-hu*uv)/det;
            if(s>=-1e-8 && r>=-1e-8 && s+r<=1+1e-8){nearest=t;normal=n;}
        }
        return normal==null?null:new RayTraceResult(start.addVector(nearest*ray[0],nearest*ray[1],nearest*ray[2]),EnumFacing.getFacingFromVector((float)normal[0],(float)normal[1],(float)normal[2]),pos);
    }
    private static double[] delta(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
}
