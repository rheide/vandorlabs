package com.vandorlabs.physics;
/** Immutable orthonormal transform, composed parent first; no world or client dependencies. */
public final class RigidTransform {
    public static final RigidTransform IDENTITY=new RigidTransform(new double[]{1,0,0,0,1,0,0,0,1},0,0,0);
    public final double[] r;public final double x,y,z;
    public RigidTransform(double[] rotation,double x,double y,double z){if(rotation.length!=9)throw new IllegalArgumentException("3x3 rotation");r=rotation.clone();this.x=x;this.y=y;this.z=z;}
    public double[] point(double px,double py,double pz){return new double[]{x+r[0]*px+r[1]*py+r[2]*pz,y+r[3]*px+r[4]*py+r[5]*pz,z+r[6]*px+r[7]*py+r[8]*pz};}
    public double[] vector(double px,double py,double pz){return new double[]{r[0]*px+r[1]*py+r[2]*pz,r[3]*px+r[4]*py+r[5]*pz,r[6]*px+r[7]*py+r[8]*pz};}
    public double[] inverse(double px,double py,double pz){return inverseVector(px-x,py-y,pz-z);}
    public double[] inverseVector(double px,double py,double pz){return new double[]{r[0]*px+r[3]*py+r[6]*pz,r[1]*px+r[4]*py+r[7]*pz,r[2]*px+r[5]*py+r[8]*pz};}
    public RigidTransform then(RigidTransform child){double[] m=new double[9];for(int row=0;row<3;row++)for(int col=0;col<3;col++)for(int k=0;k<3;k++)m[row*3+col]+=r[row*3+k]*child.r[k*3+col];double[] p=point(child.x,child.y,child.z);return new RigidTransform(m,p[0],p[1],p[2]);}
    public static RigidTransform translation(double x,double y,double z){return new RigidTransform(IDENTITY.r,x,y,z);}
    public static RigidTransform rotation(double ax,double ay,double az,double degrees){
        double length=Math.sqrt(ax*ax+ay*ay+az*az);if(length<1e-12)return IDENTITY;ax/=length;ay/=length;az/=length;double a=Math.toRadians(degrees),c=Math.cos(a),s=Math.sin(a),t=1-c;
        return new RigidTransform(new double[]{c+ax*ax*t,ax*ay*t-az*s,ax*az*t+ay*s,ay*ax*t+az*s,c+ay*ay*t,ay*az*t-ax*s,az*ax*t-ay*s,az*ay*t+ax*s,c+az*az*t},0,0,0);
    }
    public static RigidTransform joint(double px,double py,double pz,double hx,double hz,double yaw,double pitch){return translation(px,py,pz).then(rotation(0,1,0,-yaw)).then(rotation(-hz,0,hx,pitch)).then(translation(-px,-py,-pz));}
    public double angle(){return Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,(r[0]+r[4]+r[8]-1)/2))));}
    /** OpenGL uses column-major matrices; callers receive an independent array. */
    public double[] columnMajor(){return new double[]{r[0],r[3],r[6],0,r[1],r[4],r[7],0,r[2],r[5],r[8],0,x,y,z,1};}
    private static double combinedAngle(double yaw,double pitch){return Math.toDegrees(2*Math.acos(Math.max(-1,Math.min(1,Math.cos(Math.toRadians(yaw)/2)*Math.cos(Math.toRadians(pitch)/2)))));}
    public static double[] limitedAngles(double yaw,double pitch,double maximum){
        yaw=Math.max(-maximum,Math.min(maximum,yaw));pitch=Math.max(-maximum,Math.min(maximum,pitch));
        double angle=combinedAngle(yaw,pitch);
        if(angle<=maximum+1e-9)return new double[]{yaw,pitch};
        double lo=0,hi=1;for(int i=0;i<24;i++){double mid=(lo+hi)/2;if(combinedAngle(yaw*mid,pitch*mid)<=maximum)lo=mid;else hi=mid;}
        return new double[]{yaw*lo,pitch*lo};
    }
}
