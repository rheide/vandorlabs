package com.vandorlabs.gear.core;

import java.util.Arrays;

/** Critical roots for distinct exact integer arm mappings, never uniform phase sampling. */
public final class ArmParkingPhases {
    public static final int MAX_PHASES=4096;
    private static final double EPS=1e-10;
    private ArmParkingPhases(){}
    private static final class Roots {
        double[] values=new double[32];int count;
        void add(double p){
            if(!Double.isFinite(p)||p < -EPS||p>1+EPS)return;p=Math.max(0,Math.min(1,p));
            if(count==MAX_PHASES)throw new IllegalArgumentException("Arm phase bound exceeded");
            if(count==values.length)values=Arrays.copyOf(values,Math.min(MAX_PHASES,values.length*2));values[count++]=p;
        }
        double[] finish(){Arrays.sort(values,0,count);int n=0;for(int i=0;i<count;i++)if(n==0||values[i]!=values[n-1])values[n++]=values[i];return Arrays.copyOf(values,n);}
    }
    /** Invert the actual monotone cubic used by GearMotion, including exact endpoints. */
    public static double inverseEase(double value){
        finite(value);if(value < -EPS||value>1+EPS)throw new IllegalArgumentException("Ease outside unit interval");
        if(value<=0)return 0;if(value>=1)return 1;double lo=0,hi=1;
        for(int i=0;i<58;i++){double mid=(lo+hi)*.5;if(GearMotion.smoothstep(mid)<value)lo=mid;else hi=mid;}
        return (lo+hi)*.5;
    }
    private static void finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite arm curve");}
    private static void angles(double first,double last){finite(first);finite(last);if(Math.abs(first)>180||Math.abs(last)>180)throw new IllegalArgumentException("Arm angle outside source range");}
    private static int[] integers(double low,double high){
        finite(low);finite(high);if(Math.abs(low)>1000000||Math.abs(high)>1000000||high-low>512)throw new IllegalArgumentException("Arm coordinate bound exceeded");
        return new int[]{(int)Math.ceil(low-EPS),(int)Math.floor(high+EPS)};
    }
    /** Translation is affine in smoothstep(p); every integer plane crossing is included. */
    public static double[] linear(double[] first,double[] last){
        if(first.length!=3||last.length!=3)throw new IllegalArgumentException("Three coordinates required");
        int axis=0;double distance=0;for(int i=0;i<3;i++){finite(first[i]);finite(last[i]);if(Math.abs(last[i]-first[i])>distance){axis=i;distance=Math.abs(last[i]-first[i]);}}
        Roots out=new Roots();out.add(0);out.add(1);if(distance==0)return out.finish();
        int[] range=integers(Math.min(first[axis],last[axis]),Math.max(first[axis],last[axis]));
        for(int n=range[0];n<=range[1];n++){double eased=(n-first[axis])/(last[axis]-first[axis]);if(eased>=-EPS&&eased<=1+EPS)out.add(inverseEase(eased));}
        return out.finish();
    }
    /** A rigid basis is cardinal only at quarter turns; mount conjugation preserves this fact. */
    public static double[] rotating(double firstAngle,double lastAngle){
        angles(firstAngle,lastAngle);Roots out=new Roots();out.add(0);out.add(1);if(firstAngle==lastAngle)return out.finish();
        int first=(int)Math.ceil(Math.min(firstAngle,lastAngle)/90),last=(int)Math.floor(Math.max(firstAngle,lastAngle)/90);
        for(int turn=first;turn<=last;turn++)out.add(inverseEase((turn*90-firstAngle)/(lastAngle-firstAngle)));
        return out.finish();
    }
    /** origin(theta)=constant+cosine*cos(theta)+sine*sin(theta), with theta affine in ease(p). */
    public static double[] leveling(double[] constant,double[] cosine,double[] sine,double firstAngle,double lastAngle){
        angles(firstAngle,lastAngle);if(constant.length!=3||cosine.length!=3||sine.length!=3)throw new IllegalArgumentException("Three harmonic coordinates required");
        int axis=0;double radius=0;
        for(int i=0;i<3;i++){finite(constant[i]);finite(cosine[i]);finite(sine[i]);double r=Math.hypot(cosine[i],sine[i]);if(r>radius){axis=i;radius=r;}}
        Roots out=new Roots();out.add(0);out.add(1);if(radius==0||firstAngle==lastAngle)return out.finish();
        if(radius<1e-8)throw new IllegalArgumentException("Numerically unresolved arm radius");
        double center=constant[axis],phi=Math.atan2(sine[axis],cosine[axis]);int[] range=integers(center-radius,center+radius);
        double low=Math.toRadians(Math.min(firstAngle,lastAngle)),high=Math.toRadians(Math.max(firstAngle,lastAngle));
        for(int n=range[0];n<=range[1];n++){
            double value=(n-center)/radius;if(value < -1-EPS||value>1+EPS)continue;double angle=Math.acos(Math.max(-1,Math.min(1,value)));
            for(int sign=-1;sign<=1;sign+=2){double base=phi+sign*angle;
                int a=(int)Math.ceil((low-base)/(2*Math.PI)-EPS),b=(int)Math.floor((high-base)/(2*Math.PI)+EPS);
                for(int cycle=a;cycle<=b;cycle++){double degrees=Math.toDegrees(base+cycle*2*Math.PI),eased=(degrees-firstAngle)/(lastAngle-firstAngle);if(eased>=-EPS&&eased<=1+EPS)out.add(inverseEase(eased));}
            }
        }
        return out.finish();
    }
}
