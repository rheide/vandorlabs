package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import com.google.common.collect.ImmutableMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.resources.IResourceManager;
import net.minecraftforge.client.model.*;

/** Supplies configured material variants from the existing shape templates. */
public final class UnifiedItemModels implements ICustomModelLoader {
    private MapHolder choices;
    private static final class MapHolder {
        final java.util.Map<String,Integer> ids=new java.util.HashMap<>();
        MapHolder(){for(int i=ScreenHousingTextures.LEGACY_COUNT;i<ScreenHousingTextures.IDS.length;i++)ids.putIfAbsent(ScreenHousingTextures.IDS[i],i);}
    }
    int choice(ResourceLocation location) {
        if(!"vandorlabs".equals(location.getResourceDomain()))return -1;
        String path=location.getResourcePath();
        if(!path.startsWith("item/configured/") && !path.startsWith("configured/"))return -1;
        if(choices==null)choices=new MapHolder();
        int result=match(path);
        if(path.endsWith("_fit"))result=Math.min(result,match(path.substring(0,path.length()-4)));
        if(path.endsWith("_tile"))result=Math.min(result,match(path.substring(0,path.length()-5)));
        return result==Integer.MAX_VALUE?-1:result;
    }
    private int match(String path){
        int result=Integer.MAX_VALUE;
        for(int offset=path.indexOf('_');offset>=0;offset=path.indexOf('_',offset+1)){
            Integer found=choices.ids.get(path.substring(offset+1));if(found!=null)result=Math.min(result,found);
        }
        return result;
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
