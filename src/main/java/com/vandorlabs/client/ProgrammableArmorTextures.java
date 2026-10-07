package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.awt.image.BufferedImage;
import java.util.*;

/** Center one aspect-preserving material image on each visible vanilla armor face. */
public final class ProgrammableArmorTextures {
    private static final Map<String, ResourceLocation> CACHE = new LinkedHashMap<>(16, .75F, true);
    private static final int LIMIT = 128;
    private static final BufferedImage[] MASKS = new BufferedImage[2];
    private ProgrammableArmorTextures() { }

    public static String texture(int choice, boolean leggings) {
        return texture(ScreenHousingTextures.texture(choice),leggings);
    }
    public static String texture(String spriteName,boolean leggings) {
        int layer = leggings ? 1 : 0;
        String key = spriteName+"/"+layer;
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) return cached.toString();
        Minecraft mc = Minecraft.getMinecraft();
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(spriteName);
        if (sprite.getFrameCount() == 0) return "minecraft:textures/models/armor/diamond_layer_"+(layer+1)+".png";
        int[] pixels = sprite.getFrameTextureData(0)[0];
        int w = UnifiedTextureSprites.contentWidth(sprite), h = UnifiedTextureSprites.contentHeight(sprite);
        if(w!=sprite.getIconWidth() || h!=sprite.getIconHeight()) {
            int[] content=new int[w*h];
            for(int y=0;y<h;y++)System.arraycopy(pixels,y*sprite.getIconWidth(),content,y*w,w);
            pixels=content;
        }
        BufferedImage mask = mask(layer);
        BufferedImage skin = bake(pixels,w,h,mask);
        ResourceLocation location = mc.getTextureManager().getDynamicTextureLocation("programmable_armor",new DynamicTexture(skin));
        CACHE.put(key,location);
        if (CACHE.size()>LIMIT) {
            Iterator<ResourceLocation> oldest=CACHE.values().iterator();
            mc.getTextureManager().deleteTexture(oldest.next());oldest.remove();
        }
        return location.toString();
    }

    // Vanilla ModelBiped cuboid faces: head, torso, arms and legs, in 64x32 UV units.
    private static final int[][] FACES = {
        {8,0,8,8},{16,0,8,8},{0,8,8,8},{8,8,8,8},{16,8,8,8},{24,8,8,8},
        {20,16,8,4},{28,16,8,4},{16,20,4,12},{20,20,8,12},{28,20,4,12},{32,20,8,12},
        {44,16,4,4},{48,16,4,4},{40,20,4,12},{44,20,4,12},{48,20,4,12},{52,20,4,12},
        {4,16,4,4},{8,16,4,4},{0,20,4,12},{4,20,4,12},{8,20,4,12},{12,20,4,12}
    };
    static BufferedImage bake(int[] pixels,int width,int height,BufferedImage mask) {
        BufferedImage skin=new BufferedImage(256,128,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<128;y++)for(int x=0;x<256;x++)
            skin.setRGB(x,y,mask.getRGB(x*mask.getWidth()/256,y*mask.getHeight()/128)&0xFF000000);
        for(int[] face:FACES) {
            int left=face[0]*4,top=face[1]*4,right=left+face[2]*4,bottom=top+face[3]*4;
            int x0=right,y0=bottom,x1=left,y1=top;
            for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)if((skin.getRGB(x,y)>>>24)!=0) {
                x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x+1);y1=Math.max(y1,y+1);
            }
            if(x0>=x1 || y0>=y1)continue;
            // Center on the visible area, so the boots' shorter mask is centered too.
            double scale=Math.max((double)(x1-x0)/width,(double)(y1-y0)/height);
            for(int y=top;y<bottom;y++)for(int x=left;x<right;x++) {
                int sx=Math.max(0,Math.min(width-1,(int)((x+.5-(x0+x1)/2.0)/scale+width/2.0)));
                int sy=Math.max(0,Math.min(height-1,(int)((y+.5-(y0+y1)/2.0)/scale+height/2.0)));
                skin.setRGB(x,y,(skin.getRGB(x,y)&0xFF000000)|(pixels[sy*width+sx]&0xFFFFFF));
            }
        }
        return skin;
    }

    private static BufferedImage mask(int layer) {
        if (MASKS[layer] == null) {
            ResourceLocation location = new ResourceLocation("minecraft", "textures/models/armor/diamond_layer_"+(layer+1)+".png");
            try (java.io.InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream()) {
                MASKS[layer] = TextureUtil.readBufferedImage(stream);
            } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load vanilla armor mask: "+location,e); }
        }
        return MASKS[layer];
    }

    /** Atlas frames have changed after resource reload; rebuild derived armor skins on demand. */
    public static final class Events {
        @SubscribeEvent public void reload(TextureStitchEvent.Post event) {
            for (ResourceLocation location:CACHE.values()) Minecraft.getMinecraft().getTextureManager().deleteTexture(location);
            CACHE.clear();
            java.util.Arrays.fill(MASKS,null);
        }
    }
}
