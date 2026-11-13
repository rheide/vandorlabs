package com.vandorlabs.client;

import com.vandorlabs.render.GlassPanePixels;

/** Source-over, shadow isolation and explicit quality choices, without a GL context. */
public final class GlassPanePolicyChecks {
    private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int alpha=0;alpha<256;alpha++){
            int one=GlassPanePixels.singleAlpha(alpha);double expected=255*(1-Math.pow(1-alpha/255D,2));
            need(Math.abs(one-expected)<=.5000001,"single surface transmittance "+alpha);
            need(GlassPanePixels.singlePixel((alpha<<24)|0xd89b2b)==(one<<24|0xd89b2b),"gold hue is unchanged");
            for(int foreground:new int[]{0,43,155,216,255})for(int background:new int[]{0,64,128,255}){
                double a=alpha/255D,old=(foreground*a+background*(1-a))*(1-a)+foreground*a;
                double now=foreground*one/255D+background*(1-one/255D);
                need(Math.abs(now-old)<=.5000001,"one/two surface blend parity within alpha quantization");
            }
        }
        need(GlassPanePixels.dominant(new int[]{0x33d89b2b,0x88ffffff,0x33d89b2b,0x33d89b2b})==0x33d89b2b,"shimmer is not averaged into base tint");
        need(GlassPanePixels.uniform(new int[]{1,1,1}),"uniform material merges");
        need(!GlassPanePixels.uniform(new int[]{1,1,2}),"authored pattern cannot be stretched");
        for(boolean frames:new boolean[]{false,true})for(boolean shadow:new boolean[]{false,true})for(boolean opaque:new boolean[]{false,true}){
            boolean omitted=GlassPanePolicy.omitPane(opaque,shadow,frames);
            need(omitted==(frames&&shadow&&!opaque),"only transparent shadow interior can be omitted");
            if(!shadow||opaque)need(!omitted,"main view and opaque depth remain present");
        }
        need(GlassPanePolicy.frames(null)&&GlassPanePolicy.frames("frames"),"performance shadow default");
        need(!GlassPanePolicy.frames("full")&&!GlassPanePolicy.frames("typo"),"full and unknown shadow settings preserve effect");
        need(GlassPanePolicy.smooth(null)&&GlassPanePolicy.smooth("smooth"),"smooth default");
        need(!GlassPanePolicy.smooth("authored")&&!GlassPanePolicy.smooth("typo"),"authored and unknown texture modes preserve artwork");
        need(!GlassPanePolicy.enabled("false",true)&&GlassPanePolicy.enabled(null,true),"compatibility override");
        System.out.println("GlassPanePolicyChecks PASS assertions="+checks+" all alpha bytes, gold RGB, source-over backgrounds, shadow isolation and compatibility choices");
    }
    private GlassPanePolicyChecks(){}
}
