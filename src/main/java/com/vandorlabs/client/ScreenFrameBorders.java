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
    static void clear(){CACHE.clear();}
    static double[] get(int choice){return CACHE.computeIfAbsent(choice,ScreenFrameBorders::load);}
    /** Include authored outer padding and anti-aliased inner pixels. An edge is
     * not required to start at the canvas boundary or have equal thickness. */
    static int extent(BufferedImage image,int edge,int width,int height){
        int along=edge<2?height:width,depth=edge<2?width:height,extent=0;
        for(int at=along/4;at<along-along/4;at++)for(int d=0;d<depth/3;d++){
            int x=edge==0?d:edge==1?width-1-d:at,y=edge==2?d:edge==3?height-1-d:at;
            if((image.getRGB(x,y)>>>24)>=32)extent=Math.max(extent,d+1);
        }
        return extent;
    }
    private static double[] load(int choice){
        if(choice<0)return new double[4];
        if(!FrameAppearance.border(choice))return new double[]{1D/16,1D/16,1D/16,1D/16};
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
