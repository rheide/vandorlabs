package com.vandorlabs.gear.core;

/** Shared Double hinge translation and sweep bounds, with caller-owned projection scratch. */
public final class ArmHinge {
    public enum Mode { SINGLE, LEGACY_CENTER, DOUBLE }
    private static Mode mode(int value){switch(value){case 0:return Mode.SINGLE;case 1:return Mode.LEGACY_CENTER;case 2:return Mode.DOUBLE;default:throw new IllegalArgumentException("Unknown arm hinge mode");}}
    private static Mode explicit(boolean middle,Integer value){Mode result=mode(value.intValue());if(middle!=(result!=Mode.SINGLE))throw new IllegalArgumentException("Inconsistent arm hinge fields");return result;}
    /** Missing new fields must retain a previous center-only saved stroke exactly. */
    public static Mode readSaved(boolean middle,Integer value){return value==null?(middle?Mode.LEGACY_CENTER:Mode.SINGLE):explicit(middle,value);}
    /** Old copied/UI choices select the corrected Double option when newly applied. */
    public static Mode readChoice(boolean middle,Integer value){return value==null?(middle?Mode.DOUBLE:Mode.SINGLE):explicit(middle,value);}
    public static Mode atRest(Mode value,boolean zeroRest,boolean ownershipPending){if(value==null)throw new IllegalArgumentException("Missing arm hinge mode");return value==Mode.LEGACY_CENTER&&zeroRest&&!ownershipPending?Mode.DOUBLE:value;}
    /** Move the elbow cover while keeping its axle aligned with the fold axis. */
    public static double kinkOffsetX(GearMotion.Direction fold,GearMotion.Direction kink,int width){return (kink.axisZ()-fold.axisZ())*GearMotion.MIDDLE_HINGE_OFFSET*(fold.axisX()!=0?width:1);}
    public static double kinkOffsetZ(GearMotion.Direction fold,GearMotion.Direction kink,int width){return (fold.axisX()-kink.axisX())*GearMotion.MIDDLE_HINGE_OFFSET*(fold.axisZ()!=0?width:1);}
    private static double sine(double degrees){if(degrees==0||Math.abs(degrees)==180)return 0;if(degrees==90)return 1;if(degrees==-90)return -1;return Math.sin(Math.toRadians(degrees));}
    private static double cosine(double degrees){if(degrees==0)return 1;if(Math.abs(degrees)==180)return -1;if(Math.abs(degrees)==90)return 0;return Math.cos(Math.toRadians(degrees));}
    /** Caller-owned scratch: lower translation, upper translation/sine/cosine, authored elbow. */
    public static void prepareDouble(GearMotion.Direction fold,GearMotion.Direction kink,int height,double socketOffset,int width,double angle,double[] out){
        if(fold==null||kink==null||height<1||height>GearTopology.MAX_COLUMN_HEIGHT||width<1||width>2||!Double.isFinite(socketOffset)||!Double.isFinite(angle)||Math.abs(angle)>180||out==null||out.length<13)throw new IllegalArgumentException("Invalid double hinge pose");
        double px=GearMotion.PIVOT_X,py=GearMotion.PIVOT_Y,pz=GearMotion.PIVOT_Z;
        double ex=px+kink.axisZ()*GearMotion.MIDDLE_HINGE_OFFSET*(fold.axisX()!=0?width:1),ey=GearMotion.middlePivotY(height,socketOffset),ez=pz-kink.axisX()*GearMotion.MIDDLE_HINGE_OFFSET*(fold.axisZ()!=0?width:1);
        double ax=fold.axisX(),az=fold.axisZ(),hs=sine(angle/2),hc=cosine(angle/2),hd=1-hc;
        double s=angle%90==0?sine(angle):2*hs*hc,c=angle%90==0?cosine(angle):hc*hc-hs*hs,d=1-c;
        double fullDot=ax*ex+az*ez,dx=ex-px,dy=ey-py,dz=ez-pz,halfDot=ax*dx+az*dz,baseDot=ax*px+az*pz;
        out[0]=px-(ex*c-az*ey*s+ax*fullDot*d)+(dx*hc-az*dy*hs+ax*halfDot*hd);
        out[1]=py-(ey*c+(az*ex-ax*ez)*s)+(dy*hc+(az*dx-ax*dz)*hs);
        out[2]=pz-(ez*c+ax*ey*s+az*fullDot*d)+(dz*hc+ax*dy*hs+az*halfDot*hd);
        out[3]=px-(px*hc-az*py*hs+ax*baseDot*hd);out[4]=py-(py*hc+(az*px-ax*pz)*hs);out[5]=pz-(pz*hc+ax*py*hs+az*baseDot*hd);
        out[6]=hs;out[7]=hc;out[8]=ex;out[9]=ey;out[10]=ez;out[11]=s;out[12]=c;
        for(int i=0;i<13;i++)if(!Double.isFinite(out[i]))throw new IllegalArgumentException("Nonfinite double hinge frame");
    }
    public static double curvatureBound(GearMotion.Direction fold,double[] prepared,double x,double y,double z){
        if(fold==null||prepared==null||prepared.length<13||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Double.isFinite(prepared[8])||!Double.isFinite(prepared[9])||!Double.isFinite(prepared[10]))throw new IllegalArgumentException("Invalid hinge curvature point");
        double lower=fold.axisX()!=0?Math.hypot(y-prepared[9],z-prepared[10]):Math.hypot(x-prepared[8],y-prepared[9]);
        double upper=fold.axisX()!=0?Math.hypot(prepared[9]-GearMotion.PIVOT_Y,prepared[10]-GearMotion.PIVOT_Z):Math.hypot(prepared[8]-GearMotion.PIVOT_X,prepared[9]-GearMotion.PIVOT_Y);
        return lower+.25*upper;
    }
    private ArmHinge(){}
}
