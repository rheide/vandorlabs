package com.vandorlabs.client;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import javax.imageio.ImageIO;

/** Canonical authored frame edge pixel baseline, independent of a rendering context. */
public final class FrameEdgePixelChecks {
    public static void main(String[] args)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");int count=0;
        JsonArray entries=new JsonParser().parse(new String(Files.readAllBytes(Paths.get("generated-resources/assets/vandorlabs/data/unified_textures.json")),"UTF-8")).getAsJsonArray();
        for(JsonElement value:entries){JsonObject entry=value.getAsJsonObject();if(!entry.has("frameRail"))continue;
            String source=entry.get("source").getAsString(),rail=entry.get("frameRail").getAsString();Path file=null;
            for(String directory:new String[]{"texture-packs/additional","texture-packs/default","src/main/resources"}){Path candidate=Paths.get(directory,"assets/vandorlabs/textures/blocks",source+".png");if(Files.isRegularFile(candidate)){file=candidate;break;}}
            if(file==null)throw new AssertionError(source);BufferedImage image=ImageIO.read(file.toFile());double aspect=entry.has("frameAspect")?Math.max(1,entry.get("frameAspect").getAsDouble()):1;
            if(aspect>1)image=image.getSubimage(0,0,image.getWidth(),Math.max(1,(int)Math.round(image.getHeight()/aspect)));
            for(int edge=0;edge<4;edge++){BufferedImage crop=FrameEdgeTextures.crop(image,edge);if(crop.getWidth()!=64||crop.getHeight()!=64)throw new AssertionError("Edge size");
                hash.update(rail.getBytes("UTF-8"));hash.update((byte)0);hash.update((byte)edge);
                for(int y=0;y<64;y++)for(int x=0;x<64;x++){int pixel=crop.getRGB(x,y);hash.update((byte)(pixel>>>24));hash.update((byte)(pixel>>>16));hash.update((byte)(pixel>>>8));hash.update((byte)pixel);}count++;
            }
        }
        StringBuilder hex=new StringBuilder();for(byte b:hash.digest())hex.append(String.format("%02x",b&255));
        System.out.println("Frame edge pixels PASS crops="+count+" SHA256="+hex);
        String expected=System.getProperty("vandorlabs.frameEdgeExpected");if(expected!=null&&!expected.equals(hex.toString()))throw new AssertionError("Frame pixel baseline changed");
    }
}
