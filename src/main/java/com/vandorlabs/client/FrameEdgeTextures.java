package com.vandorlabs.client;

import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Function;

/** Four opaque border crops, prepared once per resource reload at native material resolution. */
final class FrameEdgeTextures {
    static final int TOP=0,BOTTOM=1,LEFT=2,RIGHT=3;
    private static final Map<String,Edge[]> EDGES=new HashMap<>();
    private FrameEdgeTextures(){}
    static void clear(){EDGES.clear();}
    static void register(TextureMap atlas,String rail,String source,double aspect){
        Edge[] edges=new Edge[4];for(int i=0;i<4;i++)edges[i]=new Edge(rail+(i==0?"":"_edge_"+i),source,i,aspect);
        EDGES.put(rail,edges);for(Edge edge:edges)atlas.setTextureEntry(edge);
    }
    static boolean authored(TextureAtlasSprite sprite){return sprite!=null&&EDGES.containsKey(sprite.getIconName());}
    static TextureAtlasSprite edge(TextureAtlasSprite sprite,double[]... points){
        if(sprite==null)return null;Edge[] choices=EDGES.get(sprite.getIconName());if(choices==null)return sprite;
        double minX=Double.POSITIVE_INFINITY,maxX=-minX,minY=minX,maxY=-minX,cx=0,cy=0;
        for(double[] p:points){minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);cx+=p[0];cy+=p[1];}
        // Horizontal rails/caps use their top/bottom authored edge, vertical rails their side.
        boolean horizontal=maxX-minX>=maxY-minY;
        return choices[horizontal?(cy/points.length>=8?TOP:BOTTOM):(cx/points.length>=8?RIGHT:LEFT)];
    }
    static TextureAtlasSprite edge(TextureAtlasSprite sprite,int edge){Edge[] choices=EDGES.get(sprite.getIconName());return choices==null?sprite:choices[edge];}
    /** Map the authored edge length along the rim and its thickness across the wall.
     * Opaque square finishes retain their physical UV scale. */
    static TextureAtlasSprite map(TextureAtlasSprite sprite,double[]... points){
        if(!authored(sprite))return sprite;
        TextureAtlasSprite selected=edge(sprite,points);
        Edge cut=(Edge)selected;boolean horizontal=cut.edge==TOP||cut.edge==BOTTOM;
        double lo=Double.POSITIVE_INFINITY,hi=-lo;
        for(double[] p:points){double across=p[horizontal?4:3];lo=Math.min(lo,across);hi=Math.max(hi,across);}
        for(double[] p:points){double across=p[horizontal?4:3];p[3]=horizontal?p[0]:16-p[1];p[4]=hi>lo?(across-lo)*16/(hi-lo):0;}
        return selected;
    }
    static BufferedImage crop(BufferedImage image,int edge){
        int w=image.getWidth(),h=image.getHeight();boolean vertical=edge==LEFT||edge==RIGHT;
        int along=vertical?h:w,depth=vertical?w:h;
        // Keep the complete authored edge, including its end details. The former
        // largest-rectangle search silently cropped away directional decorations.
        int[] widths=new int[Math.max(1,along/2)];
        for(int i=0;i<widths.length;i++){int at=along/4+i,n=0;while(n<depth/3&&(pixel(image,edge,at,n)>>>24)>=128)n++;widths[i]=n;}
        Arrays.sort(widths);int band=Math.max(1,widths[widths.length/2]);
        BufferedImage out=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int x=0;x<64;x++){
            int at=Math.min(along-1,x*along/64),fallback=0;
            for(int d=0;d<depth/3;d++)if((pixel(image,edge,at,d)>>>24)>=240){fallback=pixel(image,edge,at,d);break;}
            if(fallback==0){for(int a=0;a<along&&fallback==0;a++)for(int d=0;d<depth/3;d++)if((pixel(image,edge,a,d)>>>24)>=240){fallback=pixel(image,edge,a,d);break;}}
            if(fallback==0)throw new IllegalArgumentException("Frame artwork contains no opaque material");
            for(int y=0;y<64;y++){int color=pixel(image,edge,at,Math.min(band-1,y*band/64));if((color>>>24)>=240)fallback=color;out.setRGB(x,y,fallback|0xff000000);}
        }
        return out;
    }
    private static int pixel(BufferedImage image,int edge,int along,int depth){
        int w=image.getWidth(),h=image.getHeight();return edge==TOP?image.getRGB(along,depth):edge==BOTTOM?image.getRGB(along,h-1-depth):edge==LEFT?image.getRGB(depth,along):image.getRGB(w-1-depth,along);
    }
    private static final class Edge extends TextureAtlasSprite {
        private final ResourceLocation source;private final int edge;private final double aspect;
        Edge(String name,String source,int edge,double aspect){super(name);this.source=new ResourceLocation("vandorlabs","textures/blocks/"+source+".png");this.edge=edge;this.aspect=aspect;}
        public boolean hasCustomLoader(IResourceManager manager,ResourceLocation location){return true;}
        public boolean load(IResourceManager manager,ResourceLocation location,Function<ResourceLocation,TextureAtlasSprite> getter){
            try(java.io.InputStream stream=manager.getResource(source).getInputStream()){
                BufferedImage image=TextureUtil.readBufferedImage(stream);if(aspect>1)image=image.getSubimage(0,0,image.getWidth(),Math.max(1,(int)Math.round(image.getHeight()/aspect)));image=crop(image,edge);setIconWidth(64);setIconHeight(64);int[][] data=new int[net.minecraft.client.Minecraft.getMinecraft().gameSettings.mipmapLevels+1][];data[0]=image.getRGB(0,0,64,64,null,0,64);setFramesTextureData(new ArrayList<>(Collections.singletonList(data)));return false;
            }catch(java.io.IOException failure){throw new IllegalStateException("Cannot load frame border "+source,failure);}
        }
    }
}
