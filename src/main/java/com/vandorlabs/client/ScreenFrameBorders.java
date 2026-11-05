package com.vandorlabs.client;

import com.vandorlabs.tiles.FrameAppearance;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.imageio.ImageIO;

/** Resource-pack-aware straight-edge thickness, excluding decorative corners. */
final class ScreenFrameBorders {
    private static final Map<Integer,double[]> CACHE=new HashMap<>();
    /** Shared nine-slice cut: corners retain authored width; middle pieces extend per cell. */
    static double cut(int index,double first,double last){return index==0?0:index==1?first:index==2?1-last:1;}
    static void clear(){CACHE.clear();}
    static double[] get(int choice){return CACHE.computeIfAbsent(choice,ScreenFrameBorders::load);}
    /** Include authored outer padding and anti-aliased inner pixels. An edge is
     * not required to start at the canvas boundary or have equal thickness. */
    static int extent(BufferedImage image,int edge,int width,int height){return com.vandorlabs.render.FrameEdgePixels.extent(image,edge,width,height);}
    private static double[] load(int choice){
        if(choice<0)return new double[4];
        if(!FrameAppearance.valid(choice)||!FrameAppearance.border(choice))return new double[]{1D/16,1D/16,1D/16,1D/16};
        ResourceLocation sprite=new ResourceLocation(ScreenHousingTextures.texture(choice));
        ResourceLocation file=new ResourceLocation(sprite.getResourceDomain(),"textures/"+sprite.getResourcePath()+".png");
        try(java.io.InputStream stream=Minecraft.getMinecraft().getResourceManager().getResource(file).getInputStream()){
            BufferedImage image=ImageIO.read(stream);int width=image.getWidth(),height=(int)Math.round(image.getHeight()/FrameAppearance.aspect(choice));
            double[] result=new double[4];
            for(int edge=0;edge<4;edge++)result[edge]=extent(image,edge,width,height)/(double)(edge<2?width:height);
            return result;
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot inspect screen frame "+file,failure);}
    }
}
