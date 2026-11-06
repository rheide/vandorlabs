package com.vandorlabs.render;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Random;

/** Freeze the actual existing pane sprite's alpha-weighted mip implementation. */
public final class TranslucentMipTest {
    public static void main(String[] args)throws Exception{
        int[] faint={0x32abcdef,0,0,0};if((TranslucentMip.average(faint[0],faint[1],faint[2],faint[3])>>>24)==0)throw new AssertionError("Faint reflection disappeared");
        Random random=new Random(0x44524d495053L);MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=65536;for(int i=0;i<cases;i++){int[] pixels={random.nextInt(),random.nextInt(),random.nextInt(),random.nextInt()};digest.update(ByteBuffer.allocate(4).putInt(TranslucentMip.average(pixels[0],pixels[1],pixels[2],pixels[3])).array());}
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));String expected=System.getProperty("vandorlabs.doorMipGolden","cbc37c6a6c10ec57ca86d6e96ca5f6d4cdb376725c2fb94d283e2088cadb73ce");if(!expected.isEmpty()&&!expected.equals(hash.toString()))throw new AssertionError("Existing pane mip arithmetic changed "+hash);
        System.out.println("Door translucent mips actual PASS cases="+cases+" SHA256="+hash+" actual faint reflection retained; native cutout negative control separate");
    }
}
