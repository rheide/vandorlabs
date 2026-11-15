package com.vandorlabs.gear.track.core;

/** Immutable authored-track selection. Wire/NBT transport belongs to the platform adapter. */
public final class TrackVisualState {
    public enum Mount { NONE, SOCKET, TOP, TOP_HIGH }
    public final int beltMask,sideMask,bridgeMask,lowerSideMask;
    public final Mount mount;
    public TrackVisualState(int belt,Mount mount,int sides,int bridges,int lower){
        int key=validatedKey(belt,mount==null?-1:mount.ordinal(),sides,bridges,lower);
        beltMask=key&15;this.mount=mount;sideMask=key>>6&15;bridgeMask=key>>10&10;lowerSideMask=key>>14&15;
    }
    public static int validatedKey(int belt,int mount,int sides,int bridges,int lower){if((belt&~15)!=0||mount<0||mount>3||(sides&~15)!=0||(bridges&~10)!=0)throw new IllegalArgumentException("Invalid track render state");return key(belt,mount,sides,bridges,lower&sides&15);}
    public static int key(int belt,int mount,int sides,int bridges,int lower){return belt|mount<<4|sides<<6|bridges<<10|lower<<14;}
    public int key(){return key(beltMask,mount.ordinal(),sideMask,bridgeMask,lowerSideMask);}

    /** Preserve the released Source reader's masking behavior. */
    public static TrackVisualState legacy(int key){return new TrackVisualState(key&15,Mount.values()[key>>4&3],key>>6&15,key>>10&10,key>>14&15);}
    public static TrackVisualState strict(int key){TrackVisualState value=legacy(key);if(value.key()!=key)throw new IllegalArgumentException("Unknown track appearance bits");return value;}
    public TrackVisualState onArm(){return mount==Mount.SOCKET?new TrackVisualState(beltMask,Mount.NONE,sideMask,bridgeMask,lowerSideMask):this;}
    @Override public boolean equals(Object other){return other instanceof TrackVisualState&&((TrackVisualState)other).key()==key();}
    @Override public int hashCode(){return key();}
}
