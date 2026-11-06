package com.vandorlabs.propulsion;

/** Source engine housing/emitter classification and packed ABGR modulation. */
public final class PropulsionMaterial {
    private static final String THRUSTERS="vandorlabs:blocks/thrusters/",HOVER="vandorlabs:blocks/hover/";
    private PropulsionMaterial(){}
    public static boolean housing(String sprite){return (THRUSTERS+"side").equals(sprite)||(THRUSTERS+"top").equals(sprite)||(THRUSTERS+"rear").equals(sprite)||(THRUSTERS+"cavity").equals(sprite);}
    public static boolean emitter(String sprite){return (sprite.startsWith(THRUSTERS)||sprite.startsWith(HOVER))&&(sprite.endsWith("_on")||sprite.endsWith("_stream"));}
    public static float remap(float value,float sourceMin,float sourceMax,float targetMin,float targetMax){float local=(value-sourceMin)/(sourceMax-sourceMin)*16F;return targetMin+(targetMax-targetMin)*local/16F;}
    public static int color(int abgr,int brightness){float factor=.20F+.80F*brightness/15F;int result=abgr&0xff000000;for(int component=0;component<3;component++)result|=Math.round((abgr>>>(component*8)&255)*factor)<<(component*8);return result;}
}
