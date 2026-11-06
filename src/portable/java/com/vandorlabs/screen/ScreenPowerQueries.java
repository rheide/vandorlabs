package com.vandorlabs.screen;
/** Source light entry points: only luminous screen roles query physical power, and fixed mode bypasses it. */
public final class ScreenPowerQueries {
 private ScreenPowerQueries(){}
 public static boolean lightQueriesPhysical(ScreenFamily family,int activationMode,int playback,boolean gated,boolean primaryRows,boolean secondaryRows,int channelLevel,boolean channelSignal){if(family==null||activationMode < -1||activationMode>2||playback<0||playback>2||channelLevel<0||channelLevel>15)throw new IllegalArgumentException("Screen light query");return (family==ScreenFamily.SCREEN||family==ScreenFamily.CONSOLE||family==ScreenFamily.DIAGONAL)&&(activationMode>0&&channelLevel<15||activationMode<0&&!channelSignal&&!primaryRows&&!secondaryRows&&(gated||playback==0));}
 public static boolean lightUsesGroupCache(ScreenFamily family,int activationMode,int playback,boolean gated,boolean primaryRows,boolean secondaryRows,int channelLevel,boolean channelSignal){return lightQueriesPhysical(family,activationMode,playback,gated,primaryRows,secondaryRows,channelLevel,channelSignal)&&(activationMode>0||gated);}
}
