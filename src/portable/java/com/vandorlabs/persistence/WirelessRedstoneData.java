package com.vandorlabs.persistence;

/** Scalar endpoint save and signal policy; side queries, channels and notifications belong to the host. */
public final class WirelessRedstoneData {
    public static final String ANALOG="WirelessAnalog",ON="WirelessOn",OFF="WirelessOff",INPUT="WirelessInput",RECEIVED="WirelessReceived";
    private WirelessRedstoneData(){}
    public static int level(int value){return Math.max(0,Math.min(15,value));}
    /** Analog defaults on only when the field is absent; other saved tag types use the platform boolean read. */
    public static boolean savedAnalog(boolean present,boolean value){return !present||value;}
    /** Only an actual int tag selects the configured On level. */
    public static int savedOn(boolean typedInt,int value){return typedInt?level(value):15;}
    public static int sourceLevel(boolean analog,int input){return analog?input:input>0?15:0;}
    public static int outputLevel(boolean analog,int received,int on,int off){return received>0?(analog?received:on):off;}
}
