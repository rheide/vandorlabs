package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.awt.image.BufferedImage;
import java.util.*;

/** Bake square atlas artwork into the vanilla armor UV layout, at one tile per 8 UV pixels. */
public final class ProgrammableArmorTextures {
    private static final Map<Long, ResourceLocation> CACHE = new LinkedHashMap<>(16, .75F, true);
    private static final int LIMIT = 128;
    private static final BufferedImage[] MASKS = new BufferedImage[2];
    private ProgrammableArmorTextures() { }

    public static String texture(int choice, boolean leggings) {
        int layer = leggings ? 1 : 0;
        long key = ((long)choice << 1) | layer;
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) return cached.toString();
        Minecraft mc = Minecraft.getMinecraft();
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(choice));
        if (sprite.getFrameCount() == 0) return "minecraft:textures/models/armor/diamond_layer_"+(layer+1)+".png";
        int[] pixels = sprite.getFrameTextureData(0)[0];
        int w = sprite.getIconWidth(), h = sprite.getIconHeight();
        BufferedImage mask = mask(layer);
        BufferedImage skin = new BufferedImage(256,128,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<128;y++) for (int x=0;x<256;x++) {
            // Preserve the diamond helmet's face opening and the separate leggings UV mask.
            // Material transparency does not punch additional holes in protective armor.
            int alpha = mask.getRGB(x*mask.getWidth()/256,y*mask.getHeight()/128) & 0xFF000000;
            skin.setRGB(x,y,alpha | (pixels[(y%32)*h/32*w+(x%32)*w/32] & 0xFFFFFF));
        }
        ResourceLocation location = mc.getTextureManager().getDynamicTextureLocation("programmable_armor",new DynamicTexture(skin));
        CACHE.put(key,location);
        if (CACHE.size()>LIMIT) {
            Iterator<ResourceLocation> oldest=CACHE.values().iterator();
            mc.getTextureManager().deleteTexture(oldest.next());oldest.remove();
        }
        return location.toString();
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
