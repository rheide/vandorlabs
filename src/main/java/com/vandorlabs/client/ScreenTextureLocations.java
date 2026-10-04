package com.vandorlabs.client;

import com.vandorlabs.VandorLabs;
import net.minecraft.util.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Texture identifiers only; texture objects and resource-pack resolution stay live. */
final class ScreenTextureLocations {
    private static final ResourceLocation OFF_PLAIN=location("screen_off.png");
    private static final ResourceLocation OFF_FRAMED=location("sequence_border_off.png");
    private static final Map<String,ResourceLocation> STATIC=new LinkedHashMap<String,ResourceLocation>(32,.75F,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,ResourceLocation> entry) {
            return size()>256;
        }
    };
    private ScreenTextureLocations() { }

    static ResourceLocation off(boolean framed) {return framed?OFF_FRAMED:OFF_PLAIN;}

    static synchronized ResourceLocation stationary(String id) {
        ResourceLocation texture=STATIC.get(id);
        if(texture==null) {
            texture=location(id+"_static.png");
            STATIC.put(id,texture);
        }
        return texture;
    }

    private static ResourceLocation location(String path) {
        return new ResourceLocation(VandorLabs.MODID,"textures/blocks/"+path);
    }
}
