package com.vandorlabs.neoforge.canopy;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import com.vandorlabs.render.TranslucentMip;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

/** Inactive standalone canopy sheets retain their original full UVs and low-alpha glass mipmaps. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeCanopySpriteSource implements SpriteSource {
    public static final MapCodec<NativeCanopySpriteSource> CODEC=MapCodec.unit(NativeCanopySpriteSource::new);
    public static final SpriteSourceType TYPE=new SpriteSourceType(CODEC);
    @SubscribeEvent public static void register(RegisterSpriteSourceTypesEvent event){event.register(ResourceLocation.parse("vandorlabs:canopy_sprites"),TYPE);}
    public static boolean active(){return Boolean.getBoolean("vandorlabs.canopyItemCheck")||Boolean.getBoolean("vandorlabs.canopyWorldCheck")||"true".equals(System.getenv("VANDOR_CANOPY_RENDER_CHECK"))||BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("vandorlabs:canopy"))||BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("vandorlabs:regular_canopy_glass"));}
    @Override public SpriteSourceType type(){return TYPE;}
    @Override public void run(ResourceManager manager,Output output){
        if(!active())return;
        for(var texture:NativeCanopyArtwork.textures()){
            ResourceLocation id=ResourceLocation.parse(texture.id()),path=id.withPrefix("textures/").withSuffix(".png");
            var resource=manager.getResource(path).orElseThrow(()->new IllegalStateException("Missing canopy sheet "+path));
            if(texture.id().endsWith("_glass"))output.add(id,loader->loader.loadSprite(id,resource,Contents::new));else output.add(id,resource);
        }
    }
    private static final class Contents extends SpriteContents {
        Contents(ResourceLocation name,FrameSize size,NativeImage image,ResourceMetadata metadata){super(name,size,image,metadata);}
        @Override public void increaseMipLevel(int levels){
            if(levels<byMipLevel.length)return;NativeImage[] old=byMipLevel,next=Arrays.copyOf(old,levels+1);
            try{for(int level=old.length;level<next.length;level++){NativeImage source=next[level-1];int width=Math.max(1,source.getWidth()>>1),height=Math.max(1,source.getHeight()>>1);NativeImage image=new NativeImage(width,height,false);next[level]=image;for(int y=0;y<height;y++)for(int x=0;x<width;x++){int x0=x*2,x1=Math.min(x0+1,source.getWidth()-1),y0=y*2,y1=Math.min(y0+1,source.getHeight()-1);image.setPixelRGBA(x,y,TranslucentMip.average(source.getPixelRGBA(x0,y0),source.getPixelRGBA(x1,y0),source.getPixelRGBA(x0,y1),source.getPixelRGBA(x1,y1)));}}byMipLevel=next;}
            catch(Throwable error){for(int level=old.length;level<next.length;level++)if(next[level]!=null)next[level].close();throw error;}
        }
    }
}
