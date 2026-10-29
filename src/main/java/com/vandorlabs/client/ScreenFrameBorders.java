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
    private static double[] load(int choice){
        if(choice<0)return new double[4];
        if(!FrameAppearance.border(choice))return new double[]{1D/16,1D/16,1D/16,1D/16};
        ResourceLocation sprite=new ResourceLocation(ScreenHousingTextures.texture(choice));
        ResourceLocation file=new ResourceLocation(sprite.getResourceDomain(),"textures/"+sprite.getResourcePath()+".png");
        try(java.io.InputStream stream=Minecraft.getMinecraft().getResourceManager().getResource(file).getInputStream()){
            BufferedImage image=ImageIO.read(stream);int width=image.getWidth(),height=(int)Math.round(image.getHeight()/FrameAppearance.aspect(choice));
            double[] result=new double[4];
            for(int edge=0;edge<4;edge++){
                int along=edge<2?height:width,depth=edge<2?width:height;int[] samples=new int[Math.max(1,along/2)];
                for(int i=0;i<samples.length;i++){
                    int at=Math.min(along-1,along/4+i),length=0;
                    while(length<depth/3){int x=edge==0?length:edge==1?width-1-length:at,y=edge==2?length:edge==3?height-1-length:at;if((image.getRGB(x,y)>>>24)<128)break;length++;}
                    samples[i]=length;
                }
                Arrays.sort(samples);result[edge]=Math.min(.25,(double)samples[samples.length/2]/depth);
            }
            return result;
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot inspect screen frame "+file,failure);}
    }
}
