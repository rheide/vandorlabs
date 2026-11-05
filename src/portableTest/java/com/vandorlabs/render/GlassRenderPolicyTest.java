package com.vandorlabs.render;

public final class GlassRenderPolicyTest {
    private static void need(boolean value){if(!value)throw new AssertionError("glass render policy");}
    public static void main(String[] args){
        int count=0;for(int material:new int[]{Integer.MIN_VALUE,-2,-1,0,1,100,0x40001234,Integer.MAX_VALUE})for(int tint:new int[]{Integer.MIN_VALUE,-1,0,1,2,3,4,Integer.MAX_VALUE}){
            float old=material<0?1F:tint==0?.35F:tint==1?.65F:tint==2?.85F:1F;
            need(Float.floatToRawIntBits(old)==Float.floatToRawIntBits(GlassRenderPolicy.opacity(material,tint)));
            need(GlassRenderPolicy.opaque(tint)==(tint==3));count++;
        }
        need(GlassRenderPolicy.opacity(-1,0)==1&&!GlassRenderPolicy.opaque(0));
        System.out.println("GlassRenderPolicy PASS cases="+count+" exact saved opacity and independent palette depth pass");
    }
}
