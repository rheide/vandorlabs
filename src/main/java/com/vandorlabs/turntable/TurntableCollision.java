package com.vandorlabs.turntable;

import net.minecraft.util.math.*;
import java.util.*;

/** Narrow strips preserve rotated footprints within 1/32 block in Minecraft's AABB collision API. */
public final class TurntableCollision {
    private static final double STEP=1.0/32;
    private TurntableCollision(){}
    /** Exact separating-axis test for a cardinal-axis rotated box against terrain. */
    public static boolean intersects(AxisAlignedBB source,Vec3d pivot,TurntableGeometry.Face face,double angle,AxisAlignedBB obstacle){
        Vec3d center=new Vec3d((source.minX+source.maxX)*.5,(source.minY+source.maxY)*.5,(source.minZ+source.maxZ)*.5),target=new Vec3d((obstacle.minX+obstacle.maxX)*.5,(obstacle.minY+obstacle.maxY)*.5,(obstacle.minZ+obstacle.maxZ)*.5);double[] rotated=TurntableGeometry.rotate(center.x-pivot.x,center.y-pivot.y,center.z-pivot.z,face,angle);
        double[] delta={rotated[0]+pivot.x-target.x,rotated[1]+pivot.y-target.y,rotated[2]+pivot.z-target.z};
        double[] half={(source.maxX-source.minX)/2,(source.maxY-source.minY)/2,(source.maxZ-source.minZ)/2},other={(obstacle.maxX-obstacle.minX)/2,(obstacle.maxY-obstacle.minY)/2,(obstacle.maxZ-obstacle.minZ)/2};
        double[][] axes=new double[3][];for(int i=0;i<3;i++)axes[i]=TurntableGeometry.rotate(i==0?1:0,i==1?1:0,i==2?1:0,face,angle);
        for(int i=0;i<6;i++){double[] axis=i<3?new double[]{i==0?1:0,i==1?1:0,i==2?1:0}:axes[i-3];double distance=0,radius=0;
            for(int j=0;j<3;j++){distance+=delta[j]*axis[j];radius+=other[j]*Math.abs(axis[j]);double dot=0;for(int k=0;k<3;k++)dot+=axes[j][k]*axis[k];radius+=half[j]*Math.abs(dot);}
            if(Math.abs(distance)>=radius-1e-7)return false;
        }return true;
    }
    public static List<AxisAlignedBB> boxes(AxisAlignedBB source,Vec3d pivot,TurntableGeometry.Face face,double angle,AxisAlignedBB query){
        double[] lo={source.minX,source.minY,source.minZ},hi={source.maxX,source.maxY,source.maxZ},p={pivot.x,pivot.y,pivot.z};
        double[] transformed=TurntableGeometry.bounds(new double[]{lo[0]-p[0],lo[1]-p[1],lo[2]-p[2],hi[0]-p[0],hi[1]-p[1],hi[2]-p[2]},face,angle);
        AxisAlignedBB broad=new AxisAlignedBB(transformed[0]+p[0],transformed[1]+p[1],transformed[2]+p[2],transformed[3]+p[0],transformed[4]+p[1],transformed[5]+p[2]);
        if(!broad.intersects(query))return Collections.emptyList();
        if(Math.abs(angle/90-Math.rint(angle/90))<1e-8)return Collections.singletonList(broad);
        int normal=face.axis(),a=(normal+1)%3,b=(normal+2)%3;
        double[] min={broad.minX,broad.minY,broad.minZ},max={broad.maxX,broad.maxY,broad.maxZ};
        if(max[a]-min[a]>max[b]-min[b]){int swap=a;a=b;b=swap;}
        List<double[]> polygon=new ArrayList<>();
        for(int n:new int[]{0,1,3,2}){double[] v=lo.clone();v[a]=(n&1)==0?lo[a]:hi[a];v[b]=(n&2)==0?lo[b]:hi[b];double[] r=TurntableGeometry.rotate(v[0]-p[0],v[1]-p[1],v[2]-p[2],face,angle);polygon.add(new double[]{r[a]+p[a],r[b]+p[b]});}
        double[] qMin={query.minX,query.minY,query.minZ},qMax={query.maxX,query.maxY,query.maxZ};List<AxisAlignedBB> result=new ArrayList<>();
        double first=Math.floor(Math.max(min[a],qMin[a])/STEP)*STEP,last=Math.min(max[a],qMax[a]);
        for(double start=first;start<last;start+=STEP){double low=Math.max(start,min[a]),high=Math.min(start+STEP,max[a]);List<double[]> clipped=clip(clip(polygon,low,true),high,false);if(clipped.isEmpty()||high-low<1e-10)continue;
            double bottom=Double.POSITIVE_INFINITY,top=Double.NEGATIVE_INFINITY;for(double[] v:clipped){bottom=Math.min(bottom,v[1]);top=Math.max(top,v[1]);}
            double[] l=lo.clone(),h=hi.clone();l[a]=low;h[a]=high;l[b]=bottom;h[b]=top;AxisAlignedBB strip=new AxisAlignedBB(l[0],l[1],l[2],h[0],h[1],h[2]);if(strip.intersects(query))result.add(strip);
        }
        return result;
    }
    private static List<double[]> clip(List<double[]> polygon,double boundary,boolean greater){List<double[]> out=new ArrayList<>();if(polygon.isEmpty())return out;double[] previous=polygon.get(polygon.size()-1);boolean wasInside=greater?previous[0]>=boundary:previous[0]<=boundary;
        for(double[] next:polygon){boolean inside=greater?next[0]>=boundary:next[0]<=boundary;if(inside!=wasInside){double t=(boundary-previous[0])/(next[0]-previous[0]);out.add(new double[]{boundary,previous[1]+t*(next[1]-previous[1])});}if(inside)out.add(next);previous=next;wasInside=inside;}return out;
    }
}
