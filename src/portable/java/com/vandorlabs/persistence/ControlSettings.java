package com.vandorlabs.persistence;

import com.vandorlabs.render.ControlMount;
import com.vandorlabs.redstone.RedstoneScreenPolicy;

/** Stable mounted-control options; live per-channel latch state is stored separately. */
public final class ControlSettings {
    private ControlSettings(){}
    public static final class Mount {
        public final int height,tilt,direction;
        public Mount(int height,int tilt,int direction){this.height=height;this.tilt=tilt;this.direction=direction;}
        public static Mount read(PrimitiveData tag,Mount fallback){
            int h=tag.getInt("BaseHeight"),t=ControlMount.migratedTilt(tag.getInt("BaseTilt"),tag.contains("ControlMountVersion")),d=tag.getInt("TiltDirection");
            return ControlMount.valid(h,t,d)?new Mount(h,t,d):fallback;
        }
        public void write(PrimitiveData tag){tag.putInt("ControlMountVersion",2);tag.putInt("BaseHeight",height);tag.putInt("BaseTilt",tilt);tag.putInt("TiltDirection",direction);}
    }
    public static final class Detents {
        public final int low,high,step;
        public Detents(int low,int high,int step){this.low=low;this.high=high;this.step=step;}
        public static Detents read(PrimitiveData tag,int previousLow,int previousHigh){
            // Legacy limits test presence, not integer type; malformed present values read as zero.
            int low=tag.contains("LowLimit")?tag.getInt("LowLimit"):5,high=tag.contains("HighLimit")?tag.getInt("HighLimit"):15;
            if(!RedstoneScreenPolicy.validLimits(low,high)){low=previousLow;high=previousHigh;}
            return new Detents(low,high,Math.max(0,Math.min(3,tag.getInt("ControlStep"))));
        }
        public void write(PrimitiveData tag){tag.putInt("LowLimit",low);tag.putInt("HighLimit",high);tag.putInt("ControlStep",step);}
    }
    /** -1 means inherit the placed registration's original size. */
    public static int readSize(PrimitiveData tag){return tag.containsInt("PowerLeverSize")?Math.max(0,Math.min(1,tag.getInt("PowerLeverSize"))):-1;}
    public static int size(int saved,int registrationDefault){return saved>=0?saved:registrationDefault;}
    public static boolean validType(int value){return value>=0&&value<3;}
    public static boolean validSize(int value){return value>=0&&value<2;}
}
