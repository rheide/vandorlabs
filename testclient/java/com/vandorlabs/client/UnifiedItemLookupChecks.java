package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.util.ResourceLocation;

/** Optimized suffix lookup must preserve every catalog and Fit/Tile loader decision. */
public final class UnifiedItemLookupChecks {
    private static int legacy(ResourceLocation location){
        if(!location.getResourceDomain().equals("vandorlabs"))return -1;
        String path=location.getResourcePath();if(!path.startsWith("item/configured/") && !path.startsWith("configured/"))return -1;
        for(int i=ScreenHousingTextures.LEGACY_COUNT;i<ScreenHousingTextures.IDS.length;i++){
            String id=ScreenHousingTextures.IDS[i];if(path.endsWith("_"+id) || path.endsWith("_"+id+"_fit") || path.endsWith("_"+id+"_tile"))return i;
        }
        return -1;
    }
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();UnifiedItemModels loader=new UnifiedItemModels();int count=0;
        for(String domain:new String[]{"vandorlabs","minecraft"})for(String prefix:new String[]{"item/configured/","configured/","item/"})
            for(String id:ScreenHousingTextures.IDS)for(String suffix:new String[]{"","_fit","_tile"}){
                ResourceLocation location=new ResourceLocation(domain,prefix+"programmable_wall_"+id+suffix);
                if(loader.choice(location)!=legacy(location))throw new IllegalStateException("model lookup changed: "+location);count++;
            }
        System.out.println("PASS: indexed configured-item lookup preserves "+count+" catalog, namespace, path and Fit/Tile decisions");
    }
}
