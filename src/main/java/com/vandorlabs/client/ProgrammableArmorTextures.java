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
    private static final Map<Integer, ResourceLocation> CACHE = new LinkedHashMap<>(16, .75F, true);
    private static final int LIMIT = 128;
    private ProgrammableArmorTextures() { }

    public static String texture(int choice) {
        ResourceLocation cached = CACHE.get(choice);
        if (cached != null) return cached.toString();
        Minecraft mc = Minecraft.getMinecraft();
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(choice));
        if (sprite.getFrameCount() == 0) return "minecraft:textures/models/armor/diamond_layer_1.png";
        int[] pixels = sprite.getFrameTextureData(0)[0];
        int w = sprite.getIconWidth(), h = sprite.getIconHeight();
        BufferedImage skin = new BufferedImage(256,128,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<128;y++) for (int x=0;x<256;x++) {
            // Keep armor opaque even when the selected block material contains transparent pixels.
            skin.setRGB(x,y,0xFF000000 | pixels[(y%32)*h/32*w+(x%32)*w/32]);
        }
        ResourceLocation location = mc.getTextureManager().getDynamicTextureLocation("programmable_armor",new DynamicTexture(skin));
        CACHE.put(choice,location);
        if (CACHE.size()>LIMIT) {
            Iterator<ResourceLocation> oldest=CACHE.values().iterator();
            mc.getTextureManager().deleteTexture(oldest.next());oldest.remove();
        }
        return location.toString();
    }

    /** Atlas frames have changed after resource reload; rebuild derived armor skins on demand. */
    public static final class Events {
        @SubscribeEvent public void reload(TextureStitchEvent.Post event) {
            for (ResourceLocation location:CACHE.values()) Minecraft.getMinecraft().getTextureManager().deleteTexture(location);
            CACHE.clear();
        }
    }
}
