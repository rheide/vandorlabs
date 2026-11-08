package com.vandorlabs.segmented;

import com.vandorlabs.physics.RigidTransform;
import com.vandorlabs.render.LegacyBoxTrace;

/** Released panel-selection transform and scalar bounds; contains no world or vertex data. */
public final class SegmentedHitGeometry {
    private SegmentedHitGeometry(){}
    public static RigidTransform transform(int x,int y,int z,int rotation,int row,int height,int column,int width,
                                           double progress,int direction,boolean straight,double depth){
        double[] pose=new double[6];SegmentedDoorGeometry.panel(row,height,column,width,progress,direction,straight,pose);
        pose[2]+=depth;return poseTransform(x,y,z,rotation,pose);
    }
    public static RigidTransform treadTransform(int x,int y,int z,int rotation,int row,int height,int column,int width,double progress,int direction,int pixels,double depth,int strip){
        double[] descriptor=new double[14],pose=new double[6];SegmentedTreads.describe(descriptor,row,height,column,width,progress,direction,pixels,depth);SegmentedTreads.pose(descriptor,strip,pose);return poseTransform(x,y,z,rotation,pose);
    }
    private static RigidTransform poseTransform(int x,int y,int z,int rotation,double[] pose){double angle=(Math.atan2(pose[3],pose[4])*180D/Math.PI);
        RigidTransform panel=pose[5]!=0
                ?RigidTransform.translation(0,pose[1],pose[2]).then(rotation(1,0,0,angle))
                :RigidTransform.translation(pose[0],pose[1],pose[2]).then(rotation(0,1,0,-angle)).then(RigidTransform.translation(-.5,0,0));
        return RigidTransform.translation(x+.5,y,z+.5).then(rotation(0,1,0,rotation*90))
                .then(RigidTransform.translation(-.5,0,-.5)).then(panel);
    }
    /** Restrict a ray to one articulated strip before querying its authored bars. */
    public static LegacyBoxTrace.Hit traceStrip(RigidTransform t,int direction,int pixels,int strip,SegmentedGateShape.Model model,java.util.List<SegmentedGateShape.Connector> below,java.util.List<SegmentedGateShape.Connector> above,SegmentedGateShape.Hit scratch,double sx,double sy,double sz,double ex,double ey,double ez){
        double[] a=t.inverse(sx,sy,sz),b=t.inverse(ex,ey,ez);double lo=strip*pixels/16D,hi=(strip+1)*pixels/16D;
        double[] low={direction<2?0:lo,direction<2?lo-.5:-.5,-SegmentedDoorGeometry.HALF_THICKNESS},high={direction<2?1:hi,direction<2?hi-.5:.5,SegmentedDoorGeometry.HALF_THICKNESS};
        if(model==null){LegacyBoxTrace.Hit local=LegacyBoxTrace.clip(low[0],low[1],low[2],high[0],high[1],high[2],a[0],a[1],a[2],b[0],b[1],b[2]);return local==null?null:world(t,local);}
        double enter=0,exit=1;for(int axis=0;axis<3;axis++){double delta=b[axis]-a[axis];if(Math.abs(delta)<1e-12){if(a[axis]<low[axis]||a[axis]>high[axis])return null;}else{double first=(low[axis]-a[axis])/delta,last=(high[axis]-a[axis])/delta;enter=Math.max(enter,Math.min(first,last));exit=Math.min(exit,Math.max(first,last));}}if(enter>exit)return null;
        enter=Math.max(0,enter-1e-7);exit=Math.min(1,exit+1e-7);
        return traceGate(t,model,below,above,scratch,sx+(ex-sx)*enter,sy+(ey-sy)*enter,sz+(ez-sz)*enter,sx+(ex-sx)*exit,sy+(ey-sy)*exit,sz+(ez-sz)*exit);
    }
    private static LegacyBoxTrace.Hit world(RigidTransform t,LegacyBoxTrace.Hit local){
        int dx=local.face==4?-1:local.face==5?1:0,dy=local.face==0?-1:local.face==1?1:0,dz=local.face==2?-1:local.face==3?1:0;
        return new LegacyBoxTrace.Hit(t.x+t.r[0]*local.x+t.r[1]*local.y+t.r[2]*local.z,t.y+t.r[3]*local.x+t.r[4]*local.y+t.r[5]*local.z,t.z+t.r[6]*local.x+t.r[7]*local.y+t.r[8]*local.z,face((float)(t.r[0]*dx+t.r[1]*dy+t.r[2]*dz),(float)(t.r[3]*dx+t.r[4]*dy+t.r[5]*dz),(float)(t.r[6]*dx+t.r[7]*dy+t.r[8]*dz)));
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
    /** Authored model coordinates are centered on the same two-pixel section midplane. */
    public static LegacyBoxTrace.Hit traceGate(RigidTransform t,SegmentedGateShape.Model model,
            java.util.List<SegmentedGateShape.Connector> below,java.util.List<SegmentedGateShape.Connector> above,
            SegmentedGateShape.Hit scratch,double sx,double sy,double sz,double ex,double ey,double ez){
        double ax=sx-t.x,ay=sy-t.y,az=sz-t.z,bx=ex-t.x,by=ey-t.y,bz=ez-t.z;
        if(!model.ray(t.r[0]*ax+t.r[3]*ay+t.r[6]*az,t.r[1]*ax+t.r[4]*ay+t.r[7]*az+.5,t.r[2]*ax+t.r[5]*ay+t.r[8]*az+.5,
                t.r[0]*bx+t.r[3]*by+t.r[6]*bz,t.r[1]*bx+t.r[4]*by+t.r[7]*bz+.5,t.r[2]*bx+t.r[5]*by+t.r[8]*bz+.5,below,above,scratch))return null;
        double x=scratch.x,y=scratch.y-.5,z=scratch.z-.5;
        int face=face((float)(t.r[0]*scratch.nx+t.r[1]*scratch.ny+t.r[2]*scratch.nz),
                (float)(t.r[3]*scratch.nx+t.r[4]*scratch.ny+t.r[5]*scratch.nz),
                (float)(t.r[6]*scratch.nx+t.r[7]*scratch.ny+t.r[8]*scratch.nz));
        return new LegacyBoxTrace.Hit(t.x+t.r[0]*x+t.r[1]*y+t.r[2]*z,t.y+t.r[3]*x+t.r[4]*y+t.r[5]*z,t.z+t.r[6]*x+t.r[7]*y+t.r[8]*z,face);
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
