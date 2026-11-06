package com.vandorlabs.segmented;

import com.vandorlabs.physics.RigidTransform;
import com.vandorlabs.render.LegacyBoxTrace;

/** Released panel-selection transform and scalar bounds; contains no world or vertex data. */
public final class SegmentedHitGeometry {
    private SegmentedHitGeometry(){}
    public static RigidTransform transform(int x,int y,int z,int rotation,int row,int height,int column,int width,
                                           double progress,int direction,boolean straight,double depth){
        double[] pose=new double[6];SegmentedDoorGeometry.panel(row,height,column,width,progress,direction,straight,pose);
        pose[2]+=depth;double angle=(Math.atan2(pose[3],pose[4])*180D/Math.PI);
        RigidTransform panel=pose[5]!=0
                ?RigidTransform.translation(0,pose[1],pose[2]).then(rotation(1,0,0,angle))
                :RigidTransform.translation(pose[0],pose[1],pose[2]).then(rotation(0,1,0,-angle)).then(RigidTransform.translation(-.5,0,0));
        return RigidTransform.translation(x+.5,y,z+.5).then(rotation(0,1,0,rotation*90))
                .then(RigidTransform.translation(-.5,0,-.5)).then(panel);
    }
    /** Java 8 degree conversion order is part of the released ray arithmetic. */
    private static RigidTransform rotation(double ax,double ay,double az,double degrees){
        double length=Math.sqrt(ax*ax+ay*ay+az*az);if(length<1e-12)return RigidTransform.IDENTITY;
        ax/=length;ay/=length;az/=length;double a=degrees/180D*Math.PI,c=Math.cos(a),s=Math.sin(a),t=1-c;
        return new RigidTransform(new double[]{c+ax*ax*t,ax*ay*t-az*s,ax*az*t+ay*s,
                ay*ax*t+az*s,c+ay*ay*t,ay*az*t-ax*s,az*ax*t-ay*s,az*ay*t+ax*s,c+az*az*t},0,0,0);
    }
    public static void bounds(RigidTransform t,double[] out){
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(int n=0;n<8;n++){
            double px=(n&1)==0?0:1,py=(n&2)==0?-.5:.5,pz=(n&4)==0?-SegmentedDoorGeometry.HALF_THICKNESS:SegmentedDoorGeometry.HALF_THICKNESS;
            double x=t.x+t.r[0]*px+t.r[1]*py+t.r[2]*pz,y=t.y+t.r[3]*px+t.r[4]*py+t.r[5]*pz,z=t.z+t.r[6]*px+t.r[7]*py+t.r[8]*pz;
            minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);
            maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);
        }
        out[0]=minX;out[1]=minY;out[2]=minZ;out[3]=maxX;out[4]=maxY;out[5]=maxZ;
    }
    public static LegacyBoxTrace.Hit trace(RigidTransform t,double sx,double sy,double sz,double ex,double ey,double ez){
        double ax=sx-t.x,ay=sy-t.y,az=sz-t.z,bx=ex-t.x,by=ey-t.y,bz=ez-t.z;
        LegacyBoxTrace.Hit local=LegacyBoxTrace.clip(0,-.5,-SegmentedDoorGeometry.HALF_THICKNESS,1,.5,SegmentedDoorGeometry.HALF_THICKNESS,
                t.r[0]*ax+t.r[3]*ay+t.r[6]*az,t.r[1]*ax+t.r[4]*ay+t.r[7]*az,t.r[2]*ax+t.r[5]*ay+t.r[8]*az,
                t.r[0]*bx+t.r[3]*by+t.r[6]*bz,t.r[1]*bx+t.r[4]*by+t.r[7]*bz,t.r[2]*bx+t.r[5]*by+t.r[8]*bz);
        if(local==null)return null;
        int dx=local.face==4?-1:local.face==5?1:0,dy=local.face==0?-1:local.face==1?1:0,dz=local.face==2?-1:local.face==3?1:0;
        int face=face((float)(t.r[0]*dx+t.r[1]*dy+t.r[2]*dz),(float)(t.r[3]*dx+t.r[4]*dy+t.r[5]*dz),(float)(t.r[6]*dx+t.r[7]*dy+t.r[8]*dz));
        return new LegacyBoxTrace.Hit(t.x+t.r[0]*local.x+t.r[1]*local.y+t.r[2]*local.z,
                t.y+t.r[3]*local.x+t.r[4]*local.y+t.r[5]*local.z,
                t.z+t.r[6]*local.x+t.r[7]*local.y+t.r[8]*local.z,face);
    }
    private static int face(float x,float y,float z){
        int best=2;float largest=Float.MIN_VALUE;
        for(int face=0;face<6;face++){
            int dx=face==4?-1:face==5?1:0,dy=face==0?-1:face==1?1:0,dz=face==2?-1:face==3?1:0;
            float dot=x*dx+y*dy+z*dz;if(dot>largest){largest=dot;best=face;}
        }
        return best;
    }
}
