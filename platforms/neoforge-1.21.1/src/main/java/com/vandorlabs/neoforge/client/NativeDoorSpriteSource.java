package com.vandorlabs.neoforge.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.door.NativeDoorAssets;
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
import net.neoforged.neoforge.client.event.*;

/** Full door content ratios and the existing low-alpha pane mip policy survive atlas reloads. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeDoorSpriteSource implements SpriteSource {
    public static final MapCodec<NativeDoorSpriteSource> CODEC=MapCodec.unit(NativeDoorSpriteSource::new);
    public static final SpriteSourceType TYPE=new SpriteSourceType(CODEC);
    @SubscribeEvent public static void register(RegisterSpriteSourceTypesEvent event){event.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","door_sprites"),TYPE);}
    private static boolean active(){return "true".equals(System.getenv("VANDOR_DOOR_RESOURCE_CHECK"))||BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs","programmable_door"));}
    @Override public SpriteSourceType type(){return TYPE;}
    @Override public void run(ResourceManager manager,Output output){
        if(!active())return;
        for(var descriptor:NativeDoorAssets.textures()){
            var id=ResourceLocation.parse(descriptor.id());var path=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"textures/"+id.getPath()+".png");var resource=manager.getResource(path).orElseThrow(()->new IllegalStateException("Missing authored door artwork "+path));
            output.add(id,loader->loader.loadSprite(id,resource,(name,size,image,metadata)->new Contents(name,size,image,metadata,descriptor)));
        }
    }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event){if(active())for(String model:NativeDoorAssets.models())event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ResourceLocation.parse(model)));}
    public static final class Contents extends SpriteContents {
        private final NativeDoorAssets.Texture descriptor;
        public Contents(ResourceLocation name,FrameSize size,NativeImage image,ResourceMetadata metadata,NativeDoorAssets.Texture descriptor){super(name,size,image,metadata);this.descriptor=descriptor;}
        public float usedU(){return descriptor.usedU();}public float usedV(){return descriptor.usedV();}
        @Override public void increaseMipLevel(int levels){
            if(!descriptor.alphaMips()){super.increaseMipLevel(levels);return;}if(levels<byMipLevel.length)return;
            NativeImage[] old=byMipLevel,next=Arrays.copyOf(old,levels+1);
            try{
                for(int level=old.length;level<next.length;level++){
                    var source=next[level-1];int width=Math.max(1,source.getWidth()>>1),height=Math.max(1,source.getHeight()>>1);var image=new NativeImage(width,height,false);next[level]=image;
                    for(int y=0;y<height;y++)for(int x=0;x<width;x++){int x0=x*2,x1=Math.min(x0+1,source.getWidth()-1),y0=y*2,y1=Math.min(y0+1,source.getHeight()-1);image.setPixelRGBA(x,y,TranslucentMip.average(source.getPixelRGBA(x0,y0),source.getPixelRGBA(x1,y0),source.getPixelRGBA(x0,y1),source.getPixelRGBA(x1,y1)));}
                }
                byMipLevel=next;
            }catch(Throwable failure){for(int level=old.length;level<next.length;level++)if(next[level]!=null)next[level].close();throw failure;}
        }
    }
}
