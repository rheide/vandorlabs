package com.vandorlabs.client;

import com.vandorlabs.render.GlassPanePixels;
import com.vandorlabs.tiles.GlassTextures;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;

/** Pixel and resource-pack regressions for the authored native atlas variants. */
public final class GlassPaneTextureChecks {
    private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class Pack implements IResourcePack {
        final Map<ResourceLocation,byte[]> files=new HashMap<>();
        void put(String path,byte[] data){files.put(new ResourceLocation("vandorlabs",path),data);}
        public boolean resourceExists(ResourceLocation id){return files.containsKey(id);}
        public InputStream getInputStream(ResourceLocation id)throws IOException{
            byte[] data=files.get(id);if(data==null)throw new FileNotFoundException(id.toString());return new ByteArrayInputStream(data);
        }
        public Set<String> getResourceDomains(){return Collections.singleton("vandorlabs");}
        public <T extends IMetadataSection>T getPackMetadata(MetadataSerializer serializer,String section){return null;}
        public BufferedImage getPackImage(){return new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);}
        public String getPackName(){return "native glass fixture";}
    }
    private static byte[] bytes(InputStream input)throws IOException{
        try(InputStream source=input;ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];for(int count;(count=source.read(buffer))>=0;)output.write(buffer,0,count);return output.toByteArray();
        }
    }
    private static byte[] png(BufferedImage image)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();}
    private static void pixels(){
        for(int alpha=0;alpha<256;alpha++){
            double a=alpha/255D;int expected=(int)Math.round((1-(1-a)*(1-a))*255);
            need(GlassPanePixels.singleAlpha(alpha)==expected,"two-sheet alpha compensation");
            for(int rgb:new int[]{0,0xffffff,0xb98a23,0x123456}){
                int pixel=(alpha<<24)|rgb,result=GlassPanePixels.single(pixel);
                need((result&0xffffff)==rgb&&result>>>24==expected,"authored RGB changed");
            }
            need(GlassPanePolicy.terrainAlphaSafe(alpha,false)==(alpha==0||alpha>=27),"native alpha threshold");
            need(GlassPanePolicy.terrainAlphaSafe(alpha,true)==(expected==0||expected>=27),"compensated threshold");
        }
        BufferedImage animation=new BufferedImage(4,12,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<12;y++)for(int x=0;x<4;x++)animation.setRGB(x,y,((x+y*7)<<24)|(x*1024+y*32768));
        BufferedImage authored=GlassPaneTextures.transform(animation,true,false);
        need(authored.getWidth()==4&&authored.getHeight()==12,"animation frame layout flattened");
        for(int y=0;y<12;y++)for(int x=0;x<4;x++)need(authored.getRGB(x,y)==GlassPanePixels.single(animation.getRGB(x,y)),"animation pixel changed");
        BufferedImage unchanged=GlassPaneTextures.transform(animation,false,false);
        for(int y=0;y<12;y++)for(int x=0;x<4;x++)need(unchanged.getRGB(x,y)==animation.getRGB(x,y),"two-sheet artwork changed");
        BufferedImage smooth=GlassPaneTextures.transform(animation,true,true);
        need(smooth.getWidth()==4&&smooth.getHeight()==4,"explicit smooth frame dimensions");
        for(int y=0;y<4;y++)for(int x=0;x<4;x++)need(smooth.getRGB(x,y)==smooth.getRGB(0,0),"explicit smooth not uniform");
    }
    private static void aliases()throws Exception{
        Class<?> type=Class.forName("com.vandorlabs.client.GlassPaneTextures$VariantPack");
        Constructor<?> constructor=type.getDeclaredConstructor(List.class);constructor.setAccessible(true);
        Class<?> variantType=Class.forName("com.vandorlabs.client.GlassPaneTextures$Variant");
        Constructor<?> variantConstructor=variantType.getDeclaredConstructor(int.class,int.class,BufferedImage.class);variantConstructor.setAccessible(true);
        Method add=type.getDeclaredMethod("add",variantType);add.setAccessible(true);
        int gold=Arrays.asList(GlassTextures.IDS).indexOf("gold");need(gold>=0,"Gold palette missing");
        BufferedImage art=new BufferedImage(4,12,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<12;y++)for(int x=0;x<4;x++)art.setRGB(x,y,0x60b98a23+x+y);
        String source=new ResourceLocation(GlassTextures.fullTexture(gold,1,1)).getResourcePath();
        String stem=source.substring(0,source.length()-4),target="textures/blocks/native_glass/"+gold+"/1";
        byte[] meta="{\"animation\":{\"frametime\":2}}".getBytes(StandardCharsets.UTF_8);
        Pack base=new Pack(),custom=new Pack();base.put(source,png(art));base.put(source+".mcmeta",new byte[]{1});
        custom.put(source+".mcmeta",meta);base.put(stem+"_n.png",png(art));base.put(stem+"_s.png",new byte[]{4,5,6});
        custom.put(stem+"_s.png",new byte[]{7,8,9});custom.put(stem+"_s.png.mcmeta",meta);
        IResourcePack pack=(IResourcePack)constructor.newInstance(Arrays.asList(base,custom));
        add.invoke(pack,variantConstructor.newInstance(gold,1,art));
        need(pack.resourceExists(new ResourceLocation("vandorlabs",target+".png")),"variant PNG missing");
        BufferedImage transformed=ImageIO.read(pack.getInputStream(new ResourceLocation("vandorlabs",target+".png")));
        need(transformed.getWidth()==4&&transformed.getHeight()==12,"authored animation alias lost frames");
        for(int y=0;y<12;y++)for(int x=0;x<4;x++)need(transformed.getRGB(x,y)==GlassPanePixels.single(art.getRGB(x,y)),"variant RGB/alpha mismatch");
        need(Arrays.equals(bytes(pack.getInputStream(new ResourceLocation("vandorlabs",target+".png.mcmeta"))),meta),"animation metadata or pack priority lost");
        need(Arrays.equals(bytes(pack.getInputStream(new ResourceLocation("vandorlabs",target+"_n.png"))),png(art)),"normal map modified");
        need(Arrays.equals(bytes(pack.getInputStream(new ResourceLocation("vandorlabs",target+"_s.png"))),new byte[]{7,8,9}),"specular pack priority lost");
        need(Arrays.equals(bytes(pack.getInputStream(new ResourceLocation("vandorlabs",target+"_s.png.mcmeta"))),meta),"shader metadata lost");
        need(!pack.resourceExists(new ResourceLocation("vandorlabs",target+"_n.png.mcmeta")),"invented source metadata");
        need(!pack.resourceExists(new ResourceLocation("vandorlabs","textures/unrelated.png")),"alias captured unrelated resource");
    }
    public static void main(String[] args)throws Exception{
        need(GlassPanePolicy.terrainEnabled()&&!GlassPanePolicy.smoothTextures()&&GlassPanePolicy.singleSurface(),"authored native defaults");
        pixels();aliases();
        System.out.println("Glass native textures PASS checks="+checks+" alpha bytes, authored RGB, animation frames, native cutoff, metadata, shader companions and resource-pack priority");
    }
    private GlassPaneTextureChecks(){}
}
