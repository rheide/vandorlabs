package com.vandorlabs.machines;

/** Allocation-free warm fabrication light, sampled with the pause-aware visual clock. */
public final class SynthesisLaserStyle {
    public static int color(int beam,double now){
        double phase=beam*2.399963229728653;
        double warmth=.5+.5*Math.sin(now*.031+phase);
        double glow=.5+.5*Math.sin(now*.047+phase*1.71);
        int red=250+(int)Math.round(5*glow);
        int green=108+(int)Math.round(48*warmth);
        int blue=86+(int)Math.round(32*warmth);
        int alpha=156+(int)Math.round(40*glow);
        return alpha<<24|red<<16|green<<8|blue;
    }
    private SynthesisLaserStyle(){}
}
