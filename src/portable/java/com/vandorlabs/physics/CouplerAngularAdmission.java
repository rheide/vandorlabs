package com.vandorlabs.physics;

/** Directed joint-angle admission only; no contact, topology, terrain or ownership authority. */
public final class CouplerAngularAdmission {
    public static final double MAX_DEGREES=75;
    public static final double TOTAL_EPS_DEGREES=1e-6;
    public static final double COMPONENT_EPS_DEGREES=1e-9;
    private static final double PROPER_EPS=1e-7,MANIFOLD_EPS=1e-9,LINE_EPS=1e-7,MIN_LINE=1e-9;
    public enum Result { ADMITTED, INVALID, LEGACY_DIRECTION, ANGLE, LINE }

    /** Relative rotation in the directed legacy parent frame; lever is child bottom-centre minus pivot. */
    public static Result ordinary(double[] r,double leverX,double leverZ){
        if(!proper(r)||!finite(leverX)||!finite(leverZ))return Result.INVALID;
        double length=Math.hypot(leverX,leverZ);if(!finite(length))return Result.INVALID;
        double hx=length>.01?leverX/length:0,hz=length>.01?leverZ/length:1;
        double pitch=Math.atan2(r[3]*hx+r[5]*hz,r[4]),c=Math.cos(pitch),s=Math.sin(pitch),t=1-c;
        double p00=c+hz*hz*t,p01=-hx*s,p02=-hx*hz*t,p10=hx*s,p11=c,p12=hz*s,p20=-hx*hz*t,p21=-hz*s,p22=c+hx*hx*t;
        double q00=r[0]*p00+r[1]*p01+r[2]*p02,q20=r[6]*p00+r[7]*p01+r[8]*p02,yaw=Math.atan2(q20,q00),cy=Math.cos(yaw),sy=Math.sin(yaw);
        if(Math.abs(r[0]-(cy*p00-sy*p20))>MANIFOLD_EPS||Math.abs(r[1]-(cy*p01-sy*p21))>MANIFOLD_EPS||Math.abs(r[2]-(cy*p02-sy*p22))>MANIFOLD_EPS
                ||Math.abs(r[3]-p10)>MANIFOLD_EPS||Math.abs(r[4]-p11)>MANIFOLD_EPS||Math.abs(r[5]-p12)>MANIFOLD_EPS
                ||Math.abs(r[6]-(sy*p00+cy*p20))>MANIFOLD_EPS||Math.abs(r[7]-(sy*p01+cy*p21))>MANIFOLD_EPS||Math.abs(r[8]-(sy*p02+cy*p22))>MANIFOLD_EPS)return Result.LEGACY_DIRECTION;
        if(Math.abs(Math.toDegrees(yaw))>MAX_DEGREES+COMPONENT_EPS_DEGREES||Math.abs(Math.toDegrees(pitch))>MAX_DEGREES+COMPONENT_EPS_DEGREES||!angle(r))return Result.ANGLE;
        return Result.ADMITTED;
    }

    /** Caller-proven multi-pair line; vectors are ordered corresponding directions in construction-body frames. */
    public static Result line(double[] r,double px,double py,double pz,double cx,double cy,double cz){
        if(!proper(r)||!finite(px)||!finite(py)||!finite(pz)||!finite(cx)||!finite(cy)||!finite(cz))return Result.INVALID;
        double pn=Math.hypot(Math.hypot(px,py),pz),cn=Math.hypot(Math.hypot(cx,cy),cz);
        if(!finite(pn)||!finite(cn)||pn<=MIN_LINE||cn<=MIN_LINE)return Result.INVALID;
        px/=pn;py/=pn;pz/=pn;cx/=cn;cy/=cn;cz/=cn;
        if(Math.abs(r[0]*cx+r[1]*cy+r[2]*cz-px)>LINE_EPS||Math.abs(r[3]*cx+r[4]*cy+r[5]*cz-py)>LINE_EPS||Math.abs(r[6]*cx+r[7]*cy+r[8]*cz-pz)>LINE_EPS)return Result.LINE;
        return angle(r)?Result.ADMITTED:Result.ANGLE;
    }
    private static boolean angle(double[] r){double cosine=(r[0]+r[4]+r[8]-1)*.5;return Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,cosine))))<=MAX_DEGREES+TOTAL_EPS_DEGREES;}
    private static boolean finite(double x){return !Double.isNaN(x)&&!Double.isInfinite(x);}
    private static boolean proper(double[] r){
        if(r==null||r.length!=9)return false;for(int i=0;i<9;i++)if(!finite(r[i]))return false;
        for(int row=0;row<3;row++){int i=row*3;double norm=r[i]*r[i]+r[i+1]*r[i+1]+r[i+2]*r[i+2];if(Math.abs(norm-1)>PROPER_EPS)return false;for(int other=0;other<row;other++){int j=other*3;if(Math.abs(r[i]*r[j]+r[i+1]*r[j+1]+r[i+2]*r[j+2])>PROPER_EPS)return false;}}
        double determinant=r[0]*(r[4]*r[8]-r[5]*r[7])-r[1]*(r[3]*r[8]-r[5]*r[6])+r[2]*(r[3]*r[7]-r[4]*r[6]);return finite(determinant)&&Math.abs(determinant-1)<=PROPER_EPS;
    }
    private CouplerAngularAdmission(){}
}
