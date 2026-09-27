package com.vandorlabs.render;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class ControllerTexturePixelsTest {
    public static void main(String[] args) throws Exception {
        int repaired=0;
        for (String pack:new String[]{"default","original"})
            for (String face:new String[]{"front","back","left","right","top","bottom"})
                for (String state:new String[]{"on","off"}) {
                    BufferedImage image=ImageIO.read(new File("texture-packs/"+pack+
                            "/assets/vandorlabs/textures/block/ramp_elevator_controller_"+face+"_"+state+".png"));
                    int w=image.getWidth(),h=image.getHeight();
                    int[] pixels=image.getRGB(0,0,w,h,null,0,w),before=pixels.clone();
                    ControllerTexturePixels.seal(pixels,w,h);
                    for (int i=0;i<pixels.length;i++) {
                        if ((pixels[i]>>>24)!=255) throw new AssertionError("Controller alpha hole");
                        if ((before[i]>>>24)>=128 && (before[i]&0xFFFFFF)!=(pixels[i]&0xFFFFFF))
                            throw new AssertionError("Solid artwork RGB changed");
                        if ((before[i]>>>24)<128) repaired++;
                    }
                    int[] once=pixels.clone();ControllerTexturePixels.seal(pixels,w,h);
                    if (!java.util.Arrays.equals(once,pixels)) throw new AssertionError("Repair is not stable");
                }
        if (repaired==0) throw new AssertionError("No source seam reproduced");
        System.out.println("Controller textures PASS: 24 faces, sealed "+repaired+" alpha-cutoff pixels; solid RGB preserved");
    }
}
