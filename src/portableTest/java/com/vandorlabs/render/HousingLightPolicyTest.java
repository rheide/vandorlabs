package com.vandorlabs.render;

/** Packed light retains independent unsigned sky and block components. */
public final class HousingLightPolicyTest {
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    public static void main(String[] args){
        int sky=0x00f00020,torch=0x003000e0;
        check(HousingLightPolicy.max(sky,torch)==0x00f000e0,"different neighbours contribute sky and block maxima");
        check(Math.max(sky,torch)!=0x00f000e0,"whole-packed maximum negative control");
        int count=0;
        for(int a=0;a<256;a++)for(int b=0;b<256;b++){
            int left=(a<<16)|b,right=(b<<16)|a,m=Math.max(a,b),expected=(m<<16)|m;
            check(HousingLightPolicy.max(left,right)==expected,"exhaustive byte channels");
            check(HousingLightPolicy.max(right,left)==expected,"enumeration order");
            check(HousingLightPolicy.max(left,left)==left,"idempotence");count++;
        }
        int[] edge={0,1,15,16,240,255,256,32767,32768,65534,65535};
        for(int value=0;value<=65535;value++)for(int boundary:edge){
            int left=(value<<16)|boundary,right=(boundary<<16)|value,m=Math.max(value,boundary);
            check(HousingLightPolicy.max(left,right)==((m<<16)|m),"full unsigned16 channel sweep/sign boundaries");count++;
        }
        check(HousingLightPolicy.max(0xfffe0001,0x0002ffff)==0xfffeffff,"unsigned high and low components preserved");
        int combined=0;for(int sample:new int[]{sky,0,torch,0,0,0})combined=HousingLightPolicy.max(combined,sample);
        check(combined==0x00f000e0,"six-neighbour fold and zero identity");
        System.out.println("HousingLightPolicy contracts PASS cases="+count+" byte-exhaustive unsigned16-boundaries independent-channels negative-control");
    }
}
