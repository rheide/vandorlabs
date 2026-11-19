package com.vandorlabs.client;

import com.vandorlabs.armor.ArmorMaterialSkin;
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
        return texture(ScreenHousingTextures.fullTexture(choice),leggings);
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

    static BufferedImage bake(int[] pixels,int width,int height,BufferedImage mask) {
        int[] baked = ArmorMaterialSkin.bake((x,y) -> pixels[y * width + x], width, height,
                mask::getRGB, mask.getWidth(), mask.getHeight());
        BufferedImage skin = new BufferedImage(ArmorMaterialSkin.WIDTH, ArmorMaterialSkin.HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        skin.setRGB(0, 0, skin.getWidth(), skin.getHeight(), baked, 0, skin.getWidth());
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
