package com.vandorlabs.neoforge.client;

import com.mojang.serialization.MapCodec;
import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.render.FrameEdgePixels;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

/** Re-crop resource-pack frame artwork once per atlas reload, at the released64px edge resolution. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeFrameEdgeSource implements SpriteSource {
    public static final MapCodec<NativeFrameEdgeSource> CODEC=MapCodec.unit(NativeFrameEdgeSource::new);
    public static final SpriteSourceType TYPE=new SpriteSourceType(CODEC);
    @SubscribeEvent public static void register(RegisterSpriteSourceTypesEvent event){event.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","frame_edges"),TYPE);}
    @Override public SpriteSourceType type(){return TYPE;}
    @Override public void run(ResourceManager manager,Output output){
        for(var entry:ModernFinishCatalog.ENTRIES){var frame=entry.frame();if(frame==null||frame.rail()==null)continue;
            var sprite=ResourceLocation.parse(entry.texture());var file=ResourceLocation.fromNamespaceAndPath(sprite.getNamespace(),"textures/"+sprite.getPath()+".png");
            Resource resource=manager.getResource(file).orElseThrow(()->new IllegalStateException("Missing frame artwork "+file));
            Crops crops=new Crops(resource,Math.max(1,frame.aspect()));
            for(int edge=0;edge<4;edge++)output.add(ResourceLocation.parse(frame.rail()+(edge==0?"":"_edge_"+edge)),new Edge(crops,edge,ResourceLocation.parse(frame.rail()+(edge==0?"":"_edge_"+edge))));
        }
    }
    /** At most four64px crops retained per authored source; never retain its full decoded image. */
    private static final class Crops {
        private final Resource resource;private final double aspect;
        private BufferedImage[] images;private final boolean[] done=new boolean[4];
        Crops(Resource resource,double aspect){this.resource=resource;this.aspect=aspect;}
        synchronized BufferedImage take(int edge){
            if(done[edge])throw new IllegalStateException("Frame crop already consumed");
            if(images==null){
                BufferedImage[] loaded=new BufferedImage[4];
                try(var input=resource.open()){
                    BufferedImage source=ImageIO.read(input);if(source==null)throw new IOException("Unsupported frame image");
                    if(aspect>1)source=source.getSubimage(0,0,source.getWidth(),Math.max(1,(int)Math.round(source.getHeight()/aspect)));
                    for(int i=0;i<4;i++)if(!done[i])loaded[i]=FrameEdgePixels.crop(source,i);
                    images=loaded;
                }catch(IOException failure){throw new IllegalStateException("Cannot crop frame resource "+resource.sourcePackId(),failure);}
            }
            BufferedImage result=images[edge];images[edge]=null;done[edge]=true;return result;
        }
        synchronized void discard(int edge){done[edge]=true;if(images!=null)images[edge]=null;}
    }
    private static final class Edge implements SpriteSupplier {
        private final Crops crops;private final int edge;private final ResourceLocation name;
        Edge(Crops crops,int edge,ResourceLocation name){this.crops=crops;this.edge=edge;this.name=name;}
        @Override public SpriteContents apply(SpriteResourceLoader loader){
            BufferedImage image=crops.take(edge);NativeImage nativeImage=new NativeImage(64,64,false);
            try{
                for(int y=0;y<64;y++)for(int x=0;x<64;x++){int argb=image.getRGB(x,y);nativeImage.setPixelRGBA(x,y,(argb&0xff00ff00)|((argb>>>16)&255)|((argb&255)<<16));}
                return new SpriteContents(name,new FrameSize(64,64),nativeImage,ResourceMetadata.EMPTY);
            }catch(Throwable failure){nativeImage.close();throw failure;}
        }
        @Override public void discard(){crops.discard(edge);}
    }
}
