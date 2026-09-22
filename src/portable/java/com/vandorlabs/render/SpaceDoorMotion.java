package com.vandorlabs.render;

/** Dialog choices; the existing saved sliding/direction fields remain compatible. */
public enum SpaceDoorMotion {
    ROTATING("Rotating", false, 0),
    SIDEWAYS("Sliding Sideways", true, 0),
    UP("Sliding Up", true, 1),
    DOWN("Sliding Down", true, 2);

    public final String label;
    public final boolean sliding;
    public final int direction;
    SpaceDoorMotion(String label, boolean sliding, int direction) {
        this.label=label; this.sliding=sliding; this.direction=direction;
    }
    public SpaceDoorMotion next() { return values()[(ordinal()+1)%values().length]; }
    public static SpaceDoorMotion fromSettings(boolean sliding,int direction) {
        return !sliding?ROTATING:direction==1?UP:direction==2?DOWN:SIDEWAYS;
    }
}
