package com.vandorlabs.redstone;

/** Shared int-backed channel address space. Zero is disconnected, never an enabled output. */
public final class ChannelRange {
    public static final int MAX_ID=Integer.MAX_VALUE;
    /** Largest useful translation of at least one enabled channel within the same address space. */
    public static final int MAX_INCREMENT=MAX_ID-1;
    public static final int INCREMENT_TEXT_LENGTH=11;
    private ChannelRange(){}
    public static boolean enabled(int channel){return channel>0;}
    public static boolean validIncrement(int increment){return increment>=-MAX_INCREMENT&&increment<=MAX_INCREMENT;}
    public static int clampIncrement(int increment){return Math.max(-MAX_INCREMENT,Math.min(MAX_INCREMENT,increment));}
    public static void requireIncrement(int increment){if(!validIncrement(increment))throw new IllegalArgumentException("Channel increment must be between -"+MAX_INCREMENT+" and "+MAX_INCREMENT);}
    public static int shift(int channel,int increment){
        requireIncrement(increment);if(channel==0)return 0;
        long shifted=(long)channel+increment;
        if(!enabled(channel)||shifted<1||shifted>MAX_ID)throw new IllegalArgumentException("Channel "+channel+" cannot be shifted by "+increment+"; enabled channels must remain within 1.."+MAX_ID);
        return (int)shifted;
    }
}
