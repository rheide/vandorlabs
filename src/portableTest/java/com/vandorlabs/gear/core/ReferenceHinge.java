package com.vandorlabs.gear.core;

/** Independent composition oracle for the closed-form shared Double hinge. */
final class ReferenceHinge {
    public static final class Frame {
        public final double xx,xy,xz,yx,yy,yz,zx,zy,zz,x,y,z;
        private Frame(double xx,double xy,double xz,double yx,double yy,double yz,double zx,double zy,double zz,double x,double y,double z){this.xx=xx;this.xy=xy;this.xz=xz;this.yx=yx;this.yy=yy;this.yz=yz;this.zx=zx;this.zy=zy;this.zz=zz;this.x=x;this.y=y;this.z=z;}
        public GearMotion.Vec point(double px,double py,double pz){return new GearMotion.Vec(xx*px+xy*py+xz*pz+x,yx*px+yy*py+yz*pz+y,zx*px+zy*py+zz*pz+z);}
        public GearMotion.Vec vector(double px,double py,double pz){return new GearMotion.Vec(xx*px+xy*py+xz*pz,yx*px+yy*py+yz*pz,zx*px+zy*py+zz*pz);}
        /** Apply the inner frame first, then this frame. */
        public Frame compose(Frame b){return new Frame(xx*b.xx+xy*b.yx+xz*b.zx,xx*b.xy+xy*b.yy+xz*b.zy,xx*b.xz+xy*b.yz+xz*b.zz,
            yx*b.xx+yy*b.yx+yz*b.zx,yx*b.xy+yy*b.yy+yz*b.zy,yx*b.xz+yy*b.yz+yz*b.zz,
            zx*b.xx+zy*b.yx+zz*b.zx,zx*b.xy+zy*b.yy+zz*b.zy,zx*b.xz+zy*b.yz+zz*b.zz,
            xx*b.x+xy*b.y+xz*b.z+x,yx*b.x+yy*b.y+yz*b.z+y,zx*b.x+zy*b.y+zz*b.z+z);}
    }
    public static final class Pose {
        public final Frame upper,lower;
        public final GearMotion.Vec elbow;
        private final ArmHinge.Mode mode;private final GearMotion.Direction fold;
        private Pose(Frame upper,Frame lower,GearMotion.Vec elbow,ArmHinge.Mode mode,GearMotion.Direction fold){this.upper=upper;this.lower=lower;this.elbow=elbow;this.mode=mode;this.fold=fold;}
        private double radial(double x,double y,double z){return fold.axisX()!=0?Math.hypot(y,z):Math.hypot(x,y);}
        /** Bound on the lower point's second derivative with respect to radians. */
        public double curvatureBound(double x,double y,double z){
            if(mode==ArmHinge.Mode.SINGLE)return radial(x-GearMotion.PIVOT_X,y-GearMotion.PIVOT_Y,z-GearMotion.PIVOT_Z);
            double lower=radial(x-elbow.x,y-elbow.y,z-elbow.z);
            return mode==ArmHinge.Mode.LEGACY_CENTER?lower:lower+.25*radial(elbow.x-GearMotion.PIVOT_X,elbow.y-GearMotion.PIVOT_Y,elbow.z-GearMotion.PIVOT_Z);
        }
        /** Conservative deviation from the endpoint chord; apply only perpendicular to the hinge axis. */
        public double chordPadding(double x,double y,double z,double deltaDegrees){double radians=Math.toRadians(deltaDegrees);return curvatureBound(x,y,z)*radians*radians/8;}

    }
    private static final Frame IDENTITY=new Frame(1,0,0,0,1,0,0,0,1,0,0,0);
    private static double sine(double degrees){if(degrees==0||Math.abs(degrees)==180)return 0;if(degrees==90)return 1;if(degrees==-90)return -1;return Math.sin(Math.toRadians(degrees));}
    private static double cosine(double degrees){if(degrees==0)return 1;if(Math.abs(degrees)==180)return -1;if(Math.abs(degrees)==90)return 0;return Math.cos(Math.toRadians(degrees));}
    private static Frame around(GearMotion.Direction axis,double angle,double px,double py,double pz){
        double a=axis.axisX(),c=axis.axisZ(),s=sine(angle),co=cosine(angle),d=1-co;
        double xx=co+a*a*d,xy=-c*s,xz=a*c*d,yx=c*s,yy=co,yz=-a*s,zx=a*c*d,zy=a*s,zz=co+c*c*d;
        return new Frame(xx,xy,xz,yx,yy,yz,zx,zy,zz,px-xx*px-xy*py-xz*pz,py-yx*px-yy*py-yz*pz,pz-zx*px-zy*py-zz*pz);
    }
    public static Pose pose(ArmHinge.Mode mode,GearMotion.Direction fold,GearMotion.Direction kink,int height,double socketOffset,int width,double angle){
        if(mode==null||fold==null||kink==null||height<1||height>GearTopology.MAX_COLUMN_HEIGHT||width<1||width>2||!Double.isFinite(socketOffset)||!Double.isFinite(angle)||Math.abs(angle)>180)throw new IllegalArgumentException("Invalid arm hinge pose");
        double px=GearMotion.PIVOT_X,py=GearMotion.PIVOT_Y,pz=GearMotion.PIVOT_Z;
        double ex=px+kink.axisZ()*GearMotion.MIDDLE_HINGE_OFFSET*(fold.axisX()!=0?width:1);
        double ey=GearMotion.middlePivotY(height,socketOffset);
        double ez=pz-kink.axisX()*GearMotion.MIDDLE_HINGE_OFFSET*(fold.axisZ()!=0?width:1);
        GearMotion.Vec elbow=new GearMotion.Vec(ex,ey,ez);
        if(mode==ArmHinge.Mode.SINGLE)return new Pose(around(fold,angle,px,py,pz),around(fold,angle,px,py,pz),elbow,mode,fold);
        if(mode==ArmHinge.Mode.LEGACY_CENTER)return new Pose(IDENTITY,around(fold,angle,ex,ey,ez),elbow,mode,fold);
        Frame upper=around(fold,angle/2,px,py,pz);
        return new Pose(upper,upper.compose(around(fold,angle/2,ex,ey,ez)),elbow,mode,fold);
    }
    private ReferenceHinge(){}
}
