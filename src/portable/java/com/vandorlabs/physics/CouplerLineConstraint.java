package com.vandorlabs.physics;

import java.util.List;

/** Two-body collinear point constraints. Translation is derived from CURRENT arm anchors. */
public final class CouplerLineConstraint {
    public static final int MAX_ANCHORS=64;
    public static final double EPSILON=1e-7;
    public static final class Anchor {
        public final double px,py,pz,cx,cy,cz;
        public Anchor(double px,double py,double pz,double cx,double cy,double cz){this.px=px;this.py=py;this.pz=pz;this.cx=cx;this.cy=cy;this.cz=cz;}
    }
    public enum Failure {COUNT,NONFINITE,DEGENERATE,NONCOLLINEAR,UNEQUAL_SPAN,FRAME,TWIST}
    public static final class Result {
        public final RigidTransform transform;public final Failure failure;
        private Result(RigidTransform transform,Failure failure){this.transform=transform;this.failure=failure;}
        public boolean accepted(){return transform!=null;}
    }
    private static Result refused(Failure failure){return new Result(null,failure);}
    /** Preferred orientation contributes the neutral twist; freeTwist is relative to that frame. */
    public static Result solve(List<Anchor> anchors,RigidTransform preferred,double freeTwist,double maximumTwist){
        if(anchors==null||anchors.size()<2||anchors.size()>MAX_ANCHORS)return refused(Failure.COUNT);
        if(!Double.isFinite(freeTwist)||!Double.isFinite(maximumTwist)||maximumTwist<0||maximumTwist>180||Math.abs(freeTwist)>maximumTwist)return refused(Failure.TWIST);
        if(!rotation(preferred))return refused(Failure.FRAME);
        for(int i=0;i<anchors.size();i++){Anchor a=anchors.get(i);if(a==null||!finite(a.px)||!finite(a.py)||!finite(a.pz)||!finite(a.cx)||!finite(a.cy)||!finite(a.cz))return refused(Failure.NONFINITE);}
        Anchor first=anchors.get(0),second=null;double length=0;
        for(int i=1;i<anchors.size();i++){Anchor a=anchors.get(i);double d=length(a.px-first.px,a.py-first.py,a.pz-first.pz);if(d>EPSILON){second=a;length=d;break;}}
        if(second==null)return refused(Failure.DEGENERATE);
        double px=(second.px-first.px)/length,py=(second.py-first.py)/length,pz=(second.pz-first.pz)/length;
        double cx=second.cx-first.cx,cy=second.cy-first.cy,cz=second.cz-first.cz,childLength=length(cx,cy,cz);
        if(childLength<=EPSILON)return refused(Failure.DEGENERATE);if(Math.abs(childLength-length)>EPSILON)return refused(Failure.UNEQUAL_SPAN);cx/=childLength;cy/=childLength;cz/=childLength;
        for(int i=0;i<anchors.size();i++){Anchor a=anchors.get(i);double ax=a.px-first.px,ay=a.py-first.py,az=a.pz-first.pz,bx=a.cx-first.cx,by=a.cy-first.cy,bz=a.cz-first.cz;double along=ax*px+ay*py+az*pz,childAlong=bx*cx+by*cy+bz*cz;
            if(length(ax-px*along,ay-py*along,az-pz*along)>EPSILON||length(bx-cx*childAlong,by-cy*childAlong,bz-cz*childAlong)>EPSILON)return refused(Failure.NONCOLLINEAR);
            if(Math.abs(along-childAlong)>EPSILON)return refused(Failure.UNEQUAL_SPAN);
        }
        double[] r=preferred.r;double sx=r[0]*cx+r[1]*cy+r[2]*cz,sy=r[3]*cx+r[4]*cy+r[5]*cz,sz=r[6]*cx+r[7]*cy+r[8]*cz;double crossX=sy*pz-sz*py,crossY=sz*px-sx*pz,crossZ=sx*py-sy*px,sine=length(crossX,crossY,crossZ),cosine=Math.max(-1,Math.min(1,sx*px+sy*py+sz*pz));
        RigidTransform alignment;
        if(sine>1e-12)alignment=RigidTransform.rotation(crossX,crossY,crossZ,Math.toDegrees(Math.atan2(sine,cosine)));
        else if(cosine>=0)alignment=RigidTransform.IDENTITY;
        else{double bx=Math.abs(sx)<=Math.abs(sy)&&Math.abs(sx)<=Math.abs(sz)?1:0,by=bx==0&&Math.abs(sy)<=Math.abs(sz)?1:0,bz=bx==0&&by==0?1:0;alignment=RigidTransform.rotation(sy*bz-sz*by,sz*bx-sx*bz,sx*by-sy*bx,180);}
        RigidTransform orientation=RigidTransform.rotation(px,py,pz,freeTwist).then(alignment).then(new RigidTransform(preferred.r,0,0,0));double[] m=orientation.r;RigidTransform result=new RigidTransform(m,first.px-(m[0]*first.cx+m[1]*first.cy+m[2]*first.cz),first.py-(m[3]*first.cx+m[4]*first.cy+m[5]*first.cz),first.pz-(m[6]*first.cx+m[7]*first.cy+m[8]*first.cz));
        for(int i=0;i<anchors.size();i++){Anchor a=anchors.get(i);if(length(result.x+m[0]*a.cx+m[1]*a.cy+m[2]*a.cz-a.px,result.y+m[3]*a.cx+m[4]*a.cy+m[5]*a.cz-a.py,result.z+m[6]*a.cx+m[7]*a.cy+m[8]*a.cz-a.pz)>EPSILON)return refused(Failure.UNEQUAL_SPAN);}
        return new Result(result,null);
    }
    private static boolean finite(double value){return Double.isFinite(value)&&Math.abs(value)<=1000000;}
    private static double length(double x,double y,double z){return Math.sqrt(x*x+y*y+z*z);}
    private static boolean rotation(RigidTransform frame){if(frame==null)return false;double[] m=frame.r;for(double value:m)if(!Double.isFinite(value))return false;for(int a=0;a<3;a++)for(int b=a;b<3;b++){double dot=m[a]*m[b]+m[3+a]*m[3+b]+m[6+a]*m[6+b];if(Math.abs(dot-(a==b?1:0))>1e-8)return false;}double determinant=m[0]*(m[4]*m[8]-m[5]*m[7])-m[1]*(m[3]*m[8]-m[5]*m[6])+m[2]*(m[3]*m[7]-m[4]*m[6]);return Math.abs(determinant-1)<1e-8;}
    private CouplerLineConstraint(){}
}
