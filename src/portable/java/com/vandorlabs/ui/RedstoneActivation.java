package com.vandorlabs.ui;

/** Existing saved activation indices and labels, shared by mechanism editors. */
public final class RedstoneActivation {
    public static final int MANUAL=0,ON=1,OFF=2,SIGNAL=3;
    private RedstoneActivation(){}
    public static int mode(boolean enabled,boolean inverted){return !enabled?MANUAL:inverted?OFF:ON;}
    public static String label(int mode){return label(mode,"Manual");}
    public static String label(int mode,String inactive){return mode==ON?"Redstone ON":mode==OFF?"Redstone OFF":mode==SIGNAL?"Signal level":inactive;}
}
