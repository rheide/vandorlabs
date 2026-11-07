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
        for(Anchor a:anchors)if(a==null||!finite(a.px,a.py,a.pz,a.cx,a.cy,a.cz))return refused(Failure.NONFINITE);
        Anchor first=anchors.get(0),second=null;double length=0;
        for(int i=1;i<anchors.size();i++){Anchor a=anchors.get(i);double d=length(a.px-first.px,a.py-first.py,a.pz-first.pz);if(d>EPSILON){second=a;length=d;break;}}
        if(second==null)return refused(Failure.DEGENERATE);
        double px=(second.px-first.px)/length,py=(second.py-first.py)/length,pz=(second.pz-first.pz)/length;
        double cx=second.cx-first.cx,cy=second.cy-first.cy,cz=second.cz-first.cz,childLength=length(cx,cy,cz);
        if(childLength<=EPSILON)return refused(Failure.DEGENERATE);if(Math.abs(childLength-length)>EPSILON)return refused(Failure.UNEQUAL_SPAN);cx/=childLength;cy/=childLength;cz/=childLength;
        for(Anchor a:anchors){double ax=a.px-first.px,ay=a.py-first.py,az=a.pz-first.pz,bx=a.cx-first.cx,by=a.cy-first.cy,bz=a.cz-first.cz;double along=ax*px+ay*py+az*pz,childAlong=bx*cx+by*cy+bz*cz;
            if(length(ax-px*along,ay-py*along,az-pz*along)>EPSILON||length(bx-cx*childAlong,by-cy*childAlong,bz-cz*childAlong)>EPSILON)return refused(Failure.NONCOLLINEAR);
            if(Math.abs(along-childAlong)>EPSILON)return refused(Failure.UNEQUAL_SPAN);
        }
        double[] source=preferred.vector(cx,cy,cz);double crossX=source[1]*pz-source[2]*py,crossY=source[2]*px-source[0]*pz,crossZ=source[0]*py-source[1]*px,sine=length(crossX,crossY,crossZ),cosine=Math.max(-1,Math.min(1,source[0]*px+source[1]*py+source[2]*pz));
        RigidTransform alignment;
        if(sine>1e-12)alignment=RigidTransform.rotation(crossX,crossY,crossZ,Math.toDegrees(Math.atan2(sine,cosine)));
        else if(cosine>=0)alignment=RigidTransform.IDENTITY;
        else{double bx=Math.abs(source[0])<=Math.abs(source[1])&&Math.abs(source[0])<=Math.abs(source[2])?1:0,by=bx==0&&Math.abs(source[1])<=Math.abs(source[2])?1:0,bz=bx==0&&by==0?1:0;alignment=RigidTransform.rotation(source[1]*bz-source[2]*by,source[2]*bx-source[0]*bz,source[0]*by-source[1]*bx,180);}
        RigidTransform orientation=RigidTransform.rotation(px,py,pz,freeTwist).then(alignment).then(new RigidTransform(preferred.r,0,0,0));double[] at=orientation.point(first.cx,first.cy,first.cz);RigidTransform result=new RigidTransform(orientation.r,first.px-at[0],first.py-at[1],first.pz-at[2]);
        for(Anchor a:anchors){double[] p=result.point(a.cx,a.cy,a.cz);if(length(p[0]-a.px,p[1]-a.py,p[2]-a.pz)>EPSILON)return refused(Failure.UNEQUAL_SPAN);}
        return new Result(result,null);
    }
    private static boolean finite(double... values){for(double v:values)if(!Double.isFinite(v)||Math.abs(v)>1000000)return false;return true;}
    private static double length(double x,double y,double z){return Math.sqrt(x*x+y*y+z*z);}
    private static boolean rotation(RigidTransform frame){if(frame==null)return false;double[] m=frame.r;for(double value:m)if(!Double.isFinite(value))return false;for(int a=0;a<3;a++)for(int b=a;b<3;b++){double dot=m[a]*m[b]+m[3+a]*m[3+b]+m[6+a]*m[6+b];if(Math.abs(dot-(a==b?1:0))>1e-8)return false;}double determinant=m[0]*(m[4]*m[8]-m[5]*m[7])-m[1]*(m[3]*m[8]-m[5]*m[6])+m[2]*(m[3]*m[7]-m[4]*m[6]);return Math.abs(determinant-1)<1e-8;}
    private CouplerLineConstraint(){}
}
