package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Function;

/** Resource-pack-aware first frames and door faces, padded once during atlas loading. */
public final class UnifiedTextureSprites {
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST) public void stitch(TextureStitchEvent.Pre event) {
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++) {
            com.google.gson.JsonObject e=ScreenHousingTextures.entry(i);
            if(e!=null && e.has("rectangular"))event.getMap().setTextureEntry(new Sprite(ScreenHousingTextures.texture(i),e.get("source").getAsString(),e.has("crop"),e.has("file")));
            else event.getMap().registerSprite(new ResourceLocation(ScreenHousingTextures.texture(i)));
            if(e!=null && e.has("unlit"))event.getMap().registerSprite(new ResourceLocation(ScreenHousingTextures.texture(i,false)));
        }
    }
    private static final class Sprite extends TextureAtlasSprite {
        private final ResourceLocation source;
        private final String diskSource;
        private final boolean crop,file;
        private float usedU=1,usedV=1;
        Sprite(String name,String source,boolean crop,boolean file){super(name);this.diskSource=source;this.source=file?new ResourceLocation(name):new ResourceLocation("vandorlabs","textures/blocks/"+source+".png");this.crop=crop;this.file=file;}
        @Override public boolean hasCustomLoader(IResourceManager manager,ResourceLocation location){return true;}
        @Override public boolean load(IResourceManager manager,ResourceLocation location,Function<ResourceLocation,TextureAtlasSprite> getter) {
            try(java.io.InputStream stream=file?java.nio.file.Files.newInputStream(com.vandorlabs.tiles.FilesystemTextures.file(diskSource)):manager.getResource(source).getInputStream()) {
                BufferedImage image=TextureUtil.readBufferedImage(stream);
                if(image==null)throw new java.io.IOException("Invalid PNG");
                int x0=crop?image.getWidth()/16:0,y0=crop?image.getHeight()/32:0;
                int w=image.getWidth()-x0,h=image.getHeight()-2*y0;
                int canvas=1;while(canvas<Math.max(w,h))canvas<<=1;
                setIconWidth(canvas);setIconHeight(canvas);usedU=(float)w/canvas;usedV=(float)h/canvas;
                int[] pixels=new int[canvas*canvas];
                for(int y=0;y<canvas;y++)for(int x=0;x<canvas;x++)pixels[y*canvas+x]=image.getRGB(x0+Math.min(x,w-1),y0+Math.min(y,h-1));
                int[][] frame=new int[net.minecraft.client.Minecraft.getMinecraft().gameSettings.mipmapLevels+1][];frame[0]=pixels;
                setFramesTextureData(new ArrayList<>(Collections.singletonList(frame)));return false;
            } catch(java.io.IOException e){
                if(!file)throw new IllegalStateException("Shared texture load failed: "+source,e);
                setIconWidth(16);setIconHeight(16);usedU=usedV=1;
                int[] fallback=new int[256];java.util.Arrays.fill(fallback,0xff323944);
                int[][] frame=new int[net.minecraft.client.Minecraft.getMinecraft().gameSettings.mipmapLevels+1][];frame[0]=fallback;
                setFramesTextureData(new ArrayList<>(Collections.singletonList(frame)));return false;
            }
        }
        @Override public float getMaxU(){return getMinU()+(super.getMaxU()-getMinU())*usedU;}
        @Override public float getMaxV(){return getMinV()+(super.getMaxV()-getMinV())*usedV;}
        @Override public float getInterpolatedU(double u){return getMinU()+(getMaxU()-getMinU())*(float)u/16;}
        @Override public float getInterpolatedV(double v){return getMinV()+(getMaxV()-getMinV())*(float)v/16;}
        @Override public float getUnInterpolatedU(float u){return (u-getMinU())/(getMaxU()-getMinU())*16;}
        @Override public float getUnInterpolatedV(float v){return (v-getMinV())/(getMaxV()-getMinV())*16;}
    }
}
