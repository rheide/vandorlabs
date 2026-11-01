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
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST) public void stitch(TextureStitchEvent.Pre event) {
        FrameEdgeTextures.clear();ScreenFrameBorders.clear();TrapdoorFrameMesh.clear();
        event.getMap().registerSprite(new ResourceLocation("vandorlabs:blocks/screen_off"));
        event.getMap().registerSprite(new ResourceLocation("vandorlabs:blocks/sequence_border_off"));
        for(String panel:com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.INPUT_PANELS)
            if(ScreenHousingTextures.canonicalInput(panel).equals(panel))event.getMap().setTextureEntry(new Sprite("vandorlabs:blocks/console_inputs/"+panel+"_off","console_inputs/"+panel+"_off",false,false));
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++) {
            if(ScreenHousingTextures.canonicalChoice(i)!=i)continue;
            // Filesystem examples may be hidden but remain valid external choices.
            if(i<ScreenHousingTextures.BUILTIN_COUNT && !ScreenHousingTextures.visible(i))continue;
            com.google.gson.JsonObject e=ScreenHousingTextures.entry(i);
            if(e!=null&&e.has("frameRail"))FrameEdgeTextures.register(event.getMap(),"vandorlabs:blocks/"+e.get("frameRail").getAsString(),e.get("source").getAsString(),e.has("frameAspect")?e.get("frameAspect").getAsDouble():1);
            if(e!=null && e.has("top"))event.getMap().setTextureEntry(new Sprite(ScreenHousingTextures.texture(i),e.get("source").getAsString(),false,false));
            else if(e!=null && e.has("rectangular"))event.getMap().setTextureEntry(new Sprite(ScreenHousingTextures.fullTexture(i),e.get("source").getAsString(),e.has("crop"),e.has("file")));
            else event.getMap().registerSprite(new ResourceLocation(ScreenHousingTextures.texture(i)));
            if(e!=null && e.has("design") && !"Double Doors".equals(e.get("category").getAsString()))event.getMap().setTextureEntry(new Sprite(ScreenHousingTextures.texture(i),e.get("source").getAsString(),true,false,true));
            if(e!=null && e.has("top")) {
                event.getMap().setTextureEntry(new Sprite("vandorlabs:blocks/"+e.get("top").getAsString(),e.get("top").getAsString(),false,false));
                event.getMap().setTextureEntry(new Sprite("vandorlabs:blocks/"+e.get("side").getAsString(),e.get("side").getAsString(),false,false));
            }
            if(e!=null && "Surface".equals(ScreenHousingTextures.category(i)))event.getMap().registerSprite(new ResourceLocation(ScreenHousingTextures.fullTexture(i)+"_low"));
            if(e!=null && e.has("unlit"))event.getMap().registerSprite(new ResourceLocation(ScreenHousingTextures.texture(i,false)));
        }
    }
    static int contentWidth(TextureAtlasSprite sprite){return sprite instanceof Sprite?Math.round(sprite.getIconWidth()*((Sprite)sprite).usedU):sprite.getIconWidth();}
    static int contentHeight(TextureAtlasSprite sprite){return sprite instanceof Sprite?Math.round(sprite.getIconHeight()*((Sprite)sprite).usedV):sprite.getIconHeight();}
    public static float aspect(TextureAtlasSprite sprite){return sprite instanceof Sprite?((Sprite)sprite).usedU/((Sprite)sprite).usedV:(float)sprite.getIconWidth()/sprite.getIconHeight();}
    private static final class Region {
        final int x,y,width,height,canvas;
        Region(int x,int y,int width,int height,int canvas){this.x=x;this.y=y;this.width=width;this.height=height;this.canvas=canvas;}
        boolean unchanged(int w,int h){return x==0&&y==0&&width==w&&height==h&&canvas==w&&canvas==h;}
    }
    private static Region region(int width,int height,boolean crop,boolean half){int x=crop?width/16:0,y=crop?height/32:0,w=width-x,h=height-2*y;if(half){h/=2;y+=h;}int canvas=1;while(canvas<Math.max(w,h))canvas<<=1;return new Region(x,y,w,h,canvas);}
    private static int[] pixels(BufferedImage image,Region region){int[] result=new int[region.canvas*region.canvas];for(int y=0;y<region.canvas;y++)for(int x=0;x<region.canvas;x++)result[y*region.canvas+x]=image.getRGB(region.x+Math.min(x,region.width-1),region.y+Math.min(y,region.height-1));return result;}
    private static Region shaderRegion(String name,int width,int height){TextureAtlasSprite sprite=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getTextureExtry(name);if(sprite instanceof Sprite){Sprite custom=(Sprite)sprite;return region(width,height,custom.crop,custom.half);}return new Region(0,0,width,height,width>height?width:Math.max(width,height));}
    static boolean shaderCanvasNeeded(String name,int width,int height){TextureAtlasSprite sprite=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getTextureExtry(name);if(!(sprite instanceof Sprite)&&width<=height)return false;return width>0&&height>0&&!shaderRegion(name,width,height).unchanged(width,height);}
    static BufferedImage shaderCanvas(String name,BufferedImage image){if(!shaderCanvasNeeded(name,image.getWidth(),image.getHeight()))return image;Region r=shaderRegion(name,image.getWidth(),image.getHeight());if(r.unchanged(image.getWidth(),image.getHeight()))return image;BufferedImage output=new BufferedImage(r.canvas,r.canvas,BufferedImage.TYPE_INT_ARGB);output.setRGB(0,0,r.canvas,r.canvas,pixels(image,r),0,r.canvas);return output;}
    private static final class Sprite extends TextureAtlasSprite {
        private final ResourceLocation source;
        private final String diskSource;
        private final boolean crop,file,half;
        private float usedU=1,usedV=1;
        Sprite(String name,String source,boolean crop,boolean file){this(name,source,crop,file,false);}
        Sprite(String name,String source,boolean crop,boolean file,boolean half){super(name);this.half=half;this.diskSource=source;this.source=file?new ResourceLocation(name):new ResourceLocation("vandorlabs","textures/blocks/"+source+".png");this.crop=crop;this.file=file;}
        @Override public boolean hasCustomLoader(IResourceManager manager,ResourceLocation location){return true;}
        @Override public boolean load(IResourceManager manager,ResourceLocation location,Function<ResourceLocation,TextureAtlasSprite> getter) {
            try(java.io.InputStream stream=file?java.nio.file.Files.newInputStream(com.vandorlabs.tiles.FilesystemTextures.file(diskSource)):manager.getResource(source).getInputStream()) {
                BufferedImage image=TextureUtil.readBufferedImage(stream);
                if(image==null)throw new java.io.IOException("Invalid PNG");
                Region region=region(image.getWidth(),image.getHeight(),crop,half);
                setIconWidth(region.canvas);setIconHeight(region.canvas);usedU=(float)region.width/region.canvas;usedV=(float)region.height/region.canvas;
                int[] pixels=pixels(image,region);
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
