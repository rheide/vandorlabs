package com.vandorlabs.animation;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.zip.DeflaterOutputStream;

public final class CompactAnimationDecoderTest {
    private interface CheckedRunnable { void run() throws Exception; }

    private static byte[] encoded(int width, int height, int frames,
            int declaredPayload, byte[] payload) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x564C5441); out.writeByte(2);
        out.writeShort(width); out.writeShort(height); out.writeShort(frames);
        out.writeInt(declaredPayload);
        DeflaterOutputStream compressed=new DeflaterOutputStream(out);
        compressed.write(payload); compressed.finish(); out.close();
        return bytes.toByteArray();
    }

    private static void rejects(CheckedRunnable action, String message) throws Exception {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        BufferedImage base=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        base.setRGB(0,0,0xFF102030);
        CompactAnimationDecoder.Result result=CompactAnimationDecoder.decode(base,
                new ByteArrayInputStream(encoded(1,1,2,3,new byte[]{1,(byte)255,2})));
        if (result.frameCount!=2||result.strip.getHeight()!=2
                ||result.strip.getRGB(0,0)!=0xFF102030||result.strip.getRGB(0,1)!=0xFF111F32)
            throw new AssertionError("compact animation decoded incorrectly");
        rejects(()->CompactAnimationDecoder.decode(base,
                new ByteArrayInputStream(encoded(1,1,2,3,new byte[]{1,2}))),
                "truncated payload accepted");
        rejects(()->CompactAnimationDecoder.decode(base,
                new ByteArrayInputStream(encoded(1,1,2,3,new byte[]{1,2,3,4}))),
                "oversized payload accepted");
        rejects(()->CompactAnimationDecoder.decode(base,
                new ByteArrayInputStream(encoded(1,1,2,4,new byte[]{1,2,3,4}))),
                "false declared length accepted");
        System.out.println("Compact animation decoder PASS");
    }
}
