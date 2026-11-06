package com.vandorlabs.propulsion;

/** Released placed-engine decisions. Particle selection and brightness do not imply a powered block. */
public final class RedstoneLightPolicy {
    private RedstoneLightPolicy(){}
    public static int activation(int channelLevel,int physical,boolean inverted){int level=Math.max(channelLevel,physical);return inverted?15-level:level;}
    public static int brightness(boolean analog,int activation){return analog?activation:15;}
    public static boolean powered(boolean analog,int activation,int primaryChannel,boolean physicalPowered,boolean channelSignal,boolean inverted,boolean manualOn){return analog?activation>0:primaryChannel>0?(physicalPowered||channelSignal)!=inverted:manualOn;}
    public static boolean particles(int particleLevel,boolean analog,int activation,int threshold){return particleLevel>0&&(!analog||activation>=threshold);}
    public static int manualMode(boolean manualOn,int particleLevel){return manualOn?(particleLevel>0?2:1):0;}
    public static int renderMode(boolean powered,boolean particles){return powered?(particles?2:1):0;}
}
