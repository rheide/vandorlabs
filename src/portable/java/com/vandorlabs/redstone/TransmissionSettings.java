package com.vandorlabs.redstone;

/** Built-in ranged output values; hosts supply typed tag presence and their existing defaults. */
public final class TransmissionSettings {
    public static final String SETTINGS="VandorTransmission",CHANNELS="Channels",RANGE="Range",LOCAL="Local";
    public static final int DEFAULT_RANGE=16;
    /** Retains the existing public range-array API, including caller mutation semantics. */
    public static final int[] RANGES={4,8,16,32,64,128,256};
    private TransmissionSettings(){}
    public static boolean validRange(int range){for(int allowed:RANGES)if(range==allowed)return true;return false;}
    public static boolean validConfiguration(ChannelList channels,int range){return channels!=null&&validRange(range);}
    /** A typed but invalid saved range uses 16 even for an unsupported host. */
    public static int readRange(boolean containsInt,int saved,boolean supported){
        return containsInt?(validRange(saved)?saved:DEFAULT_RANGE):supported?DEFAULT_RANGE:-1;
    }
    public static boolean readLocal(boolean containsByte,boolean saved,boolean defaultLocal){return containsByte?saved:defaultLocal;}
    /** A malformed typed array is an explicit empty setting, not an absent host fallback. */
    public static ChannelList readChannels(boolean containsIntArray,int[] saved,ChannelList fallback){
        if(!containsIntArray)return fallback;
        try{return ChannelList.of(saved);}catch(IllegalArgumentException invalid){return ChannelList.EMPTY;}
    }
    public static ChannelList defaultChannels(boolean outputOnly,ChannelList hostChannels){return outputOnly?hostChannels:ChannelList.EMPTY;}
}
