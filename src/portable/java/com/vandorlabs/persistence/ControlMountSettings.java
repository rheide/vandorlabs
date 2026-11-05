package com.vandorlabs.persistence;

/** Current mounted-control save contract; geometry, platform validation and live signals stay host-owned. */
public final class ControlMountSettings {
    public static final String VERSION="ControlMountVersion",HEIGHT="BaseHeight",TILT="BaseTilt",DIRECTION="TiltDirection";
    public final int height,tilt,direction;
    private ControlMountSettings(int height,int tilt,int direction){this.height=height;this.tilt=tilt;this.direction=direction;}
    public static boolean valid(int height,int tilt,int direction){return height>=0&&height<=3&&tilt>=0&&tilt<=3&&direction>=0&&direction<4;}
    /** Earlier positive 5/10/15-degree presets migrate to the first supported tilted option. */
    public static int migratedTilt(int tilt,boolean versionPresent){return !versionPresent&&tilt>0&&tilt<=3?1:tilt;}
    public static ControlMountSettings read(PrimitiveData data,int previousHeight,int previousTilt,int previousDirection){
        int height=data.getInt(HEIGHT),tilt=migratedTilt(data.getInt(TILT),data.contains(VERSION)),direction=data.getInt(DIRECTION);
        // Missing/wrong scalar fields read as zero; only an invalid complete triple retains the previous state.
        return valid(height,tilt,direction)?new ControlMountSettings(height,tilt,direction):new ControlMountSettings(previousHeight,previousTilt,previousDirection);
    }
    /** Scalar hot-path writer: caller owns validation and can reuse its existing primitive adapter. */
    public static void write(PrimitiveData data,int height,int tilt,int direction){
        data.putInt(VERSION,2);data.putInt(HEIGHT,height);data.putInt(TILT,tilt);data.putInt(DIRECTION,direction);
    }
}
