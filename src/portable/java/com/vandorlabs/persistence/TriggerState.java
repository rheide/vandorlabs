package com.vandorlabs.persistence;

import com.vandorlabs.animation.ScreenBehavior;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/** Trigger saved options. Playback is retained data; trigger artwork remains static. */
public final class TriggerState {
    /** Typed fields required by the trigger schema, supplied by the target NBT adapter. */
    public interface Tag {
        boolean containsInt(String key);
        int getInt(String key);
        boolean getBoolean(String key);
        int[] getIntArray(String key);
        void putInt(String key,int value);
        void putBoolean(String key,boolean value);
        void putIntArray(String key,int[] value);
        void remove(String key);
    }
    public final int on,low,medium,exact,sides;
    public final boolean bands,frontOnly;
    private final int[] modes,speeds;
    public TriggerState(int on,boolean bands,int exact,int low,int medium,
            boolean frontOnly,int sides,int[] modes,int[] speeds){
        this.on=on;this.bands=bands;this.exact=exact;this.low=low;this.medium=medium;
        this.frontOnly=frontOnly;this.sides=sides;this.modes=modes.clone();this.speeds=speeds.clone();
    }
    public int[] artworkModes(){return modes.clone();}
    public int[] artworkSpeeds(){return speeds.clone();}
    /** Validate retained playback fields without normalizing or changing caller-owned arrays. */
    public static boolean validArtwork(int[] modes,int[] speeds){
        if(modes==null||speeds==null||modes.length!=4||speeds.length!=4)return false;
        for(int i=0;i<4;i++)if(modes[i]<ScreenBehavior.OFF||modes[i]>ScreenBehavior.ANIMATED||!ScreenBehavior.validSpeedIndex(speeds[i]))return false;
        return true;
    }
    public static int visibleSlot(boolean bands,int exact,int signal){
        if(exact>=0&&signal!=exact)return 0;
        if(!bands)return exact>=0||signal>0?3:0;
        return signal==0?0:signal<=5?1:signal<=10?2:3;
    }
    public void write(Tag tag){write(tag,on,bands,exact,low,medium,frontOnly,sides,modes,speeds);}
    /** Snapshot writer: only nondefault arrays need an ownership copy for the target tag. */
    public static void write(Tag tag,int on,boolean bands,int exact,int low,int medium,
            boolean frontOnly,int sides,int[] modes,int[] speeds){
        tag.putInt("TriggerOnTexture",on);
        if(frontOnly)tag.putBoolean("TriggerFrontOnly",true);else tag.remove("TriggerFrontOnly");
        if(sides!=0)tag.putInt("TriggerSidesTexture",sides);else tag.remove("TriggerSidesTexture");
        writePlayback(tag,"TriggerArtworkModes",modes,ScreenBehavior.STATIC);
        writePlayback(tag,"TriggerArtworkSpeeds",speeds,1);
        tag.putBoolean("TriggerLevelStates",bands);tag.putInt("TriggerExactLevel",exact);
        tag.putInt("TriggerLowTexture",low);tag.putInt("TriggerMediumTexture",medium);
    }
    public static TriggerState read(Tag tag,IntUnaryOperator normalize){
        int on=normalize.applyAsInt(tag.getInt("TriggerOnTexture"));
        return new TriggerState(on,tag.getBoolean("TriggerLevelStates"),
                tag.containsInt("TriggerExactLevel")?Math.max(-1,Math.min(15,tag.getInt("TriggerExactLevel"))):-1,
                tag.containsInt("TriggerLowTexture")?normalize.applyAsInt(tag.getInt("TriggerLowTexture")):on,
                tag.containsInt("TriggerMediumTexture")?normalize.applyAsInt(tag.getInt("TriggerMediumTexture")):on,
                tag.getBoolean("TriggerFrontOnly"),normalize.applyAsInt(tag.getInt("TriggerSidesTexture")),
                readPlayback(tag,"TriggerArtworkModes",ScreenBehavior.STATIC,true),
                readPlayback(tag,"TriggerArtworkSpeeds",1,false));
    }
    private static int[] readPlayback(Tag tag,String key,int fallback,boolean mode){
        int[] values=tag.getIntArray(key).clone();
        if(values.length!=4){values=new int[4];Arrays.fill(values,fallback);return values;}
        for(int i=0;i<4;i++)values[i]=mode?ScreenBehavior.clampMode(values[i]):ScreenBehavior.clampSpeedIndex(values[i]);
        return values;
    }
    private static void writePlayback(Tag tag,String key,int[] values,int fallback){
        for(int value:values)if(value!=fallback){tag.putIntArray(key,values.clone());return;}
        tag.remove(key);
    }
}
