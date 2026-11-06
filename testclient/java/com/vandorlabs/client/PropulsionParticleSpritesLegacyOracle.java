package com.vandorlabs.client;
import com.google.gson.*;
import java.io.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
/** Original vanilla 1.12 particle-sheet pixels, kept only in local verification artifacts. */
public final class PropulsionParticleSpritesLegacyOracle {
 public static void main(String[] args)throws Exception{JsonObject tiles=new JsonObject();String[] names={"flame","critical_hit","effect_0","generic_7"};int[] indexes={48,65,128,7};try(ZipFile zip=new ZipFile(args[0]);InputStream in=zip.getInputStream(zip.getEntry("assets/minecraft/textures/particle/particles.png"))){java.awt.image.BufferedImage image=ImageIO.read(in);if(image.getWidth()!=128||image.getHeight()!=128)throw new AssertionError("Released vanilla particle sheet");for(int i=0;i<indexes.length;i++){JsonArray pixels=new JsonArray();for(int y=0;y<8;y++)for(int x=0;x<8;x++){int argb=image.getRGB((indexes[i]%16)*8+x,(indexes[i]/16)*8+y);pixels.add((argb&0xff00ff00)|((argb>>>16)&255)|((argb&255)<<16));}tiles.add(names[i],pixels);}}File file=new File(args[1]);file.getAbsoluteFile().getParentFile().mkdirs();try(PrintWriter out=new PrintWriter(file,"UTF-8")){out.println(new Gson().toJson(tiles));}SegmentedReferenceGolden.check(file,"3ed04f281276885333ae355e7d3b58940fa75fab2a194b969338268bcafc4c6b");System.out.println("Actual Source particle sprites PASS tiles=4 pixels=256 original1.12sheet; verification-only no copied assets");}
}
