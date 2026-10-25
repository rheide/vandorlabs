package com.vandorlabs.persistence;

import java.util.function.IntUnaryOperator;

/** Stable trigger options and exact legacy band/exact-level artwork selection. */
public final class TriggerState {
    public final int on,low,medium,exact;
    public final boolean bands;
    public TriggerState(int on,boolean bands,int exact,int low,int medium){this.on=on;this.bands=bands;this.exact=exact;this.low=low;this.medium=medium;}
    public static TriggerState defaults(){return new TriggerState(0,false,-1,0,0);}
    public static boolean validExact(int exact){return exact>=-1&&exact<=15;}
    public int visible(int off,int signal){
        if(exact>=0&&signal!=exact)return off;
        if(!bands)return exact>=0||signal>0?on:off;
        return signal==0?off:signal<=5?low:signal<=10?medium:on;
    }
    public void write(PrimitiveData tag){tag.putInt("TriggerOnTexture",on);tag.putBoolean("TriggerLevelStates",bands);tag.putInt("TriggerExactLevel",exact);tag.putInt("TriggerLowTexture",low);tag.putInt("TriggerMediumTexture",medium);}
    public static TriggerState read(PrimitiveData tag,IntUnaryOperator normalize){
        int on=normalize.applyAsInt(tag.getInt("TriggerOnTexture"));
        return new TriggerState(on,tag.getBoolean("TriggerLevelStates"),tag.containsInt("TriggerExactLevel")?Math.max(-1,Math.min(15,tag.getInt("TriggerExactLevel"))):-1,
            tag.containsInt("TriggerLowTexture")?normalize.applyAsInt(tag.getInt("TriggerLowTexture")):on,
            tag.containsInt("TriggerMediumTexture")?normalize.applyAsInt(tag.getInt("TriggerMediumTexture")):on);
    }
}
