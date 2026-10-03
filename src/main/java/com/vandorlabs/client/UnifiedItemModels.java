package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import com.google.common.collect.ImmutableMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.resources.IResourceManager;
import net.minecraftforge.client.model.*;

/** Supplies configured material variants from the existing shape templates. */
public final class UnifiedItemModels implements ICustomModelLoader {
    private int choice(ResourceLocation location) {
        if(!"vandorlabs".equals(location.getResourceDomain()))return -1;
        String path=location.getResourcePath();
        if(!path.startsWith("item/configured/") && !path.startsWith("configured/"))return -1;
        for(int i=ScreenHousingTextures.LEGACY_COUNT;i<ScreenHousingTextures.IDS.length;i++)
            if(path.endsWith("_"+ScreenHousingTextures.IDS[i]) || path.endsWith("_"+ScreenHousingTextures.IDS[i]+"_fit") || path.endsWith("_"+ScreenHousingTextures.IDS[i]+"_tile"))return i;
        return -1;
    }
    @Override public boolean accepts(ResourceLocation location){return choice(location)>=0;}
    @Override public IModel loadModel(ResourceLocation location) throws Exception {
        int i=choice(location);String path=location.getResourcePath().replace("_"+ScreenHousingTextures.IDS[i],"_dark_wall_panel");
        if(path.contains("programmable_storage_"))
            return ModelLoaderRegistry.getModel(new ResourceLocation(location.getResourceDomain(),path))
                .retexture(ImmutableMap.of("top",ScreenHousingTextures.storageTexture(i,net.minecraft.util.EnumFacing.UP),
                    "side",ScreenHousingTextures.storageTexture(i,net.minecraft.util.EnumFacing.EAST),
                    "front",ScreenHousingTextures.texture(i),"particle",ScreenHousingTextures.texture(i)));
        return ModelLoaderRegistry.getModel(new ResourceLocation(location.getResourceDomain(),path))
            .retexture(ImmutableMap.of("all",ScreenHousingTextures.texture(i),"wall",ScreenHousingTextures.texture(i),"particle",ScreenHousingTextures.texture(i)));
    }
    @Override public void onResourceManagerReload(IResourceManager manager){ }
}
