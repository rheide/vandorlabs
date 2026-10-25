package com.vandorlabs.render;

/** A bright sky neighbour and a different torch neighbour must both contribute. */
public final class HousingLightPolicyChecks {
    private HousingLightPolicyChecks(){}
    private static void need(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        int sky=0x00F00020,torch=0x003000E0,both=0x00F000E0;
        need(HousingLightPolicy.max(sky,torch)==both,"independent mixed sky/block neighbours");
        need(Math.max(sky,torch)!=both,"whole packed integer maximum negative control");
        need(HousingLightPolicy.max(0xFFFE0001,0x0002FFFF)==0xFFFEFFFF,"unsigned packed components retained");
        int count=0;
        for(int skyLevel=0;skyLevel<=15;skyLevel++)for(int blockLevel=0;blockLevel<=15;blockLevel++){
            int a=skyLevel<<20,b=blockLevel<<4,expected=a|b;
            need(HousingLightPolicy.max(a,b)==expected,"all native sky/block levels");
            need(HousingLightPolicy.max(b,a)==expected,"neighbour enumeration order");
            need(HousingLightPolicy.max(expected,expected)==expected,"idempotence");
            need(HousingLightPolicy.max(HousingLightPolicy.max(a,b),0)==HousingLightPolicy.max(a,HousingLightPolicy.max(b,0)),"grouping");count++;
        }
        System.out.println("Housing light policy PASS "+count+" mixed levels, independent unsigned components and whole-packed negative control");
    }
}
