package com.vandorlabs.neoforge;

import com.vandorlabs.render.FrameEdgePixels;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Active-pack border pixels are inspected once per material bake, never during emission. */
public final class NativeTrapdoorFrameBorders {
    private NativeTrapdoorFrameBorders(){}
    public record Borders(double left,double right,double top,double bottom){
        public double[] fractions(){return new double[]{left,right,top,bottom};}
    }
    public static Borders read(BufferedImage image,double aspect){
        if(image==null||!Double.isFinite(aspect)||aspect<=0)throw new IllegalArgumentException("Trapdoor frame pixels");
        int width=image.getWidth(),height=(int)Math.round(image.getHeight()/aspect);
        if(height<1||height>image.getHeight())throw new IllegalArgumentException("Trapdoor frame aspect");
        return new Borders(FrameEdgePixels.extent(image,0,width,height)/(double)width,
                FrameEdgePixels.extent(image,1,width,height)/(double)width,
                FrameEdgePixels.extent(image,2,width,height)/(double)height,
                FrameEdgePixels.extent(image,3,width,height)/(double)height);
    }
    public static Borders read(ResourceManager resources,ModernFinishCatalog.Entry entry){
        if(entry.frame()==null||!entry.frame().border())return new Borders(1D/16,1D/16,1D/16,1D/16);
        var sprite=ResourceLocation.parse(entry.texture());var file=ResourceLocation.fromNamespaceAndPath(sprite.getNamespace(),"textures/"+sprite.getPath()+".png");
        var resource=resources.getResource(file).orElseThrow(()->new IllegalStateException("Missing Trapdoor frame "+file));
        try(var stream=resource.open()){return read(ImageIO.read(stream),entry.frame().aspect());}
        catch(IOException failure){throw new IllegalStateException("Cannot inspect Trapdoor frame "+file,failure);}
    }
}
