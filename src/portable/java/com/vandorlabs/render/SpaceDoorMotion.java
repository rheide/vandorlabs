package com.vandorlabs.render;

/** Append saved direction values: zero retains legacy placement/pairing defaults. */
public enum SpaceDoorMotion {
    ROTATING("Rotating", false, 0),
    SIDEWAYS("Slide (Auto)", true, 0),
    LEFT("Slide Left", true, 4),
    RIGHT("Slide Right", true, 5),
    HORIZONTAL_SPLIT("Split Horizontal", true, 6),
    VERTICAL_SPLIT("Split Vertical", true, 7),
    UP("Sliding Up", true, 1),
    DOWN("Sliding Down", true, 2),
    X_SPLIT("Sliding X", true, 3);

    public final String label;
    public final boolean sliding;
    public final int direction;
    SpaceDoorMotion(String label, boolean sliding, int direction) {
        this.label=label; this.sliding=sliding; this.direction=direction;
    }
    public SpaceDoorMotion next() {
        SpaceDoorMotion next=values()[(ordinal()+1)%values().length];
        return next==SIDEWAYS?LEFT:next;
    }
    public static SpaceDoorMotion fromSettings(boolean sliding,int direction) {
        if(!sliding)return ROTATING;
        for(SpaceDoorMotion mode:values())if(mode.sliding && mode.direction==direction)return mode;
        return SIDEWAYS;
    }
}
