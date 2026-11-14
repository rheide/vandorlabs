package com.vandorlabs.client;

import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.tiles.ScreenHousingTextures;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;

/** Real shipped palette/frame pixels and actual generated rail crops, including mipmaps. */
final class GlassAuthoredAtlas extends TextureMap {
    static final int MIPMAPS=4;
    final Map<String,TextureAtlasSprite> sprites=new LinkedHashMap<>();
    final Map<String,BufferedImage> images=new LinkedHashMap<>();
    final List<Integer> emissiveChoices=new ArrayList<>();
    final Map<Integer,Integer> partialPixels=new LinkedHashMap<>();
    private TextureAtlasSprite missing;
    private int texture;

    private static final class Sprite extends TextureAtlasSprite {
        Sprite(String name,BufferedImage image){
            super(name);setIconWidth(image.getWidth());setIconHeight(image.getHeight());
            int[][] pixels=new int[MIPMAPS+1][];
            pixels[0]=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
            setFramesTextureData(new ArrayList<>(Collections.singletonList(pixels)));
            generateMipmaps(MIPMAPS);
        }
    }
    GlassAuthoredAtlas()throws Exception{
        super("");
        BufferedImage white=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)white.setRGB(x,y,0xffffffff);
        add("vandorlabs:checks/white",white);missing=sprites.get("vandorlabs:checks/white");
        for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<GlassTextures.TINT_COUNT;tint++){
            String name=GlassTextures.texture(shade,1,tint);BufferedImage image=read(name);add(name,image);
            add("vandorlabs:blocks/native_glass/"+shade+"/"+tint,
                    GlassPaneTextures.transform(image,GlassPanePolicy.singleSurface(),GlassPanePolicy.smoothTextures()));
        }
        add("vandorlabs:blocks/programmable_glass/metal_side",read("vandorlabs:blocks/programmable_glass/metal_side"));
        add("vandorlabs:blocks/space_doors/medium/double_frame_metal",read("vandorlabs:blocks/space_doors/medium/double_frame_metal"));
        int solid=ScreenHousingTextures.screenIndex("light_alloy_hull");
        GlassPanePipelineChecks.need(solid>=0,"authored opaque frame fixture missing");
        add(ScreenHousingTextures.texture(solid),read(ScreenHousingTextures.texture(solid)));
        FrameEdgeTextures.clear();
        for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++){
            com.google.gson.JsonObject entry=ScreenHousingTextures.entry(choice);
            if(entry==null||!entry.has("frameLight")||entry.get("frameLight").getAsInt()<=0)continue;
            String name=ScreenHousingTextures.texture(choice);BufferedImage image=read(name);add(name,image);
            int partial=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                int alpha=image.getRGB(x,y)>>>24;if(alpha>0&&alpha<255)partial++;
            }
            GlassPanePipelineChecks.need(partial>0,"authored emissive fixture must include blended pixels: "+name);
            emissiveChoices.add(choice);partialPixels.put(choice,partial);
            if(entry.has("frameRail"))FrameEdgeTextures.register(this,"vandorlabs:blocks/"+entry.get("frameRail").getAsString(),
                    entry.get("source").getAsString(),entry.has("frameAspect")?entry.get("frameAspect").getAsDouble():1);
        }
        GlassPanePipelineChecks.need(emissiveChoices.size()==8,"shipped emissive frame catalog changed; expand the matrix explicitly");
        for(TextureAtlasSprite sprite:new ArrayList<>(sprites.values()))if(!images.containsKey(sprite.getIconName())){
            sprite.load(Minecraft.getMinecraft().getResourceManager(),new ResourceLocation(sprite.getIconName()),id->getAtlasSprite(id.toString()));
            List<int[][]> frames=new ArrayList<>();
            for(int frame=0;frame<sprite.getFrameCount();frame++)frames.add(java.util.Arrays.copyOf(sprite.getFrameTextureData(frame),MIPMAPS+1));
            sprite.setFramesTextureData(frames);
            sprite.generateMipmaps(MIPMAPS);
            BufferedImage image=new BufferedImage(sprite.getIconWidth(),sprite.getIconHeight(),BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0,0,image.getWidth(),image.getHeight(),sprite.getFrameTextureData(0)[0],0,image.getWidth());
            images.put(sprite.getIconName(),image);
        }
        install();
    }
    static BufferedImage read(String name)throws Exception{
        ResourceLocation id=new ResourceLocation(name.replace(":blocks/",":textures/blocks/")+".png");
        InputStream input=GlassAuthoredAtlas.class.getResourceAsStream("/assets/"+id.getResourceDomain()+"/"+id.getResourcePath());
        if(input==null)input=new SharedTextureResources().getInputStream(id);
        try(InputStream stream=input){BufferedImage image=ImageIO.read(stream);if(image==null)throw new AssertionError("Invalid authored texture "+id);return image;}
    }
    private void add(String name,BufferedImage image){if(!sprites.containsKey(name)){sprites.put(name,new Sprite(name,image));images.put(name,image);}}
    @Override public boolean setTextureEntry(TextureAtlasSprite sprite){return sprites.put(sprite.getIconName(),sprite)==null;}
    @Override public TextureAtlasSprite getAtlasSprite(String name){TextureAtlasSprite sprite=sprites.get(name);return sprite==null?missing:sprite;}
    @Override public TextureAtlasSprite getMissingSprite(){return missing;}
    private void install()throws Exception{
        int width=2048,x=0,y=0,row=0;
        Map<TextureAtlasSprite,int[]> positions=new LinkedHashMap<>();
        for(TextureAtlasSprite sprite:sprites.values()){
            if(x+sprite.getIconWidth()>width){x=0;y+=row;row=0;}
            positions.put(sprite,new int[]{x,y});x+=sprite.getIconWidth();row=Math.max(row,sprite.getIconHeight());
        }
        int height=16;while(height<y+row)height*=2;
        BufferedImage atlas=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(Map.Entry<TextureAtlasSprite,int[]> entry:positions.entrySet()){
            TextureAtlasSprite sprite=entry.getKey();int[] at=entry.getValue();BufferedImage image=images.get(sprite.getIconName());
            sprite.initSprite(width,height,at[0],at[1],false);
            atlas.setRGB(at[0],at[1],image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()),0,image.getWidth());
        }
        texture=GlassPanePipelineChecks.texture(atlas);org.lwjgl.opengl.GL11.glDeleteTextures(GlassPanePipelineChecks.atlasTexture);
        GlassPanePipelineChecks.atlasTexture=texture;
        // Upload exactly the sprite loader's per-level pixels, not a runtime
        // glGenerateMipmap approximation that mixes adjacent atlas entries.
        for(int level=1;level<=MIPMAPS;level++){
            int mipWidth=width>>level,mipHeight=height>>level;
            int[] pixels=new int[mipWidth*mipHeight];
            for(Map.Entry<TextureAtlasSprite,int[]> entry:positions.entrySet()){
                TextureAtlasSprite sprite=entry.getKey();int[] at=entry.getValue();
                int sw=sprite.getIconWidth()>>level,sh=sprite.getIconHeight()>>level;
                int[] source=sprite.getFrameTextureData(0)[level];
                for(int rowIndex=0;rowIndex<sh;rowIndex++)System.arraycopy(source,rowIndex*sw,pixels,((at[1]>>level)+rowIndex)*mipWidth+(at[0]>>level),sw);
            }
            java.nio.ByteBuffer buffer=org.lwjgl.BufferUtils.createByteBuffer(pixels.length*4);
            for(int pixel:pixels)buffer.put((byte)(pixel>>16)).put((byte)(pixel>>8)).put((byte)pixel).put((byte)(pixel>>24));buffer.flip();
            org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,level,org.lwjgl.opengl.GL11.GL_RGBA8,mipWidth,mipHeight,0,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,buffer);
        }
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL,MIPMAPS);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,org.lwjgl.opengl.GL11.GL_NEAREST_MIPMAP_LINEAR);
        for(Field field:Minecraft.class.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&field.getType()==TextureMap.class){field.setAccessible(true);field.set(GlassPanePipelineChecks.mc,this);}
        Class<?> variant=Class.forName("com.vandorlabs.client.GlassPaneTextures$Variant");
        Constructor<?> make=variant.getDeclaredConstructor(int.class,int.class,BufferedImage.class);make.setAccessible(true);
        Constructor<GlassPaneTextures.Selection> select=GlassPaneTextures.Selection.class.getDeclaredConstructor(TextureAtlasSprite.class,variant);select.setAccessible(true);
        GlassPaneTextures.Selection[] selections=new GlassPaneTextures.Selection[GlassTextures.IDS.length*GlassTextures.TINT_COUNT];
        for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<GlassTextures.TINT_COUNT;tint++)
            selections[shade*GlassTextures.TINT_COUNT+tint]=select.newInstance(getAtlasSprite("vandorlabs:blocks/native_glass/"+shade+"/"+tint),make.newInstance(shade,tint,images.get(GlassTextures.texture(shade,1,tint))));
        GlassPanePipelineChecks.set(null,GlassPaneTextures.class,"selections",selections);
        GlassNativePanes.clear();GlassFrameChunks.reload();
    }
}
