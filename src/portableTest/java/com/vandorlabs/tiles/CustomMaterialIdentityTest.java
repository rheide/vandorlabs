package com.vandorlabs.tiles;

/** Literal saved-ID goldens independently computed with CRC-32/ISO-HDLC over UTF-8. */
public final class CustomMaterialIdentityTest {
    private static void check(String name,int metadata,int expected){
        int actual=CustomMaterialIdentity.identifier(name,metadata);
        if(actual!=expected)throw new AssertionError(name+"@"+metadata+" saved ID "+Integer.toHexString(actual)+" != "+Integer.toHexString(expected));
        if(!MaterialChoicePolicy.isCustom(actual))throw new AssertionError("Custom namespace lost");
    }
    public static void main(String[] ignored){
        check("minecraft:stone",0,0x3100e0dd);
        check("minecraft:planks",0,0x25b1909e);check("minecraft:planks",1,0x32b6a008);check("minecraft:planks",5,0x35db6411);
        check("minecraft:log",4,0x378edb2f);check("minecraft:wool",14,0x2998f6b8);check("minecraft:wool",15,0x3e9fc62e);
        check("minecraft:wooden_door",0,0x33011d3d);check("minecraft:stained_hardened_clay",9,0x357ac7c7);
        check("example:machine_panel",15,0x36a2c4fd);check("example:machine_panel",-1,0x37b85db9);check("example:machine_panel",16,0x2fab9547);
        check("vandorlabs:\u00b5_panel",7,0x348f1dfc);check("",0,0x37796d56);check(null,0,0x3493a560);
        System.out.println("Custom material identity PASS: 15 literal saved-ID goldens, UTF-8 and unnormalized metadata/null-string compatibility");
    }
}
