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
    static TextureAtlasSprite edge(TextureAtlasSprite sprite,double[]... points){
        if(sprite==null)return null;Edge[] choices=EDGES.get(sprite.getIconName());if(choices==null)return sprite;
        double minX=Double.POSITIVE_INFINITY,maxX=-minX,minY=minX,maxY=-minX,cx=0,cy=0;
        for(double[] p:points){minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);cx+=p[0];cy+=p[1];}
        // Horizontal rails/caps use their top/bottom authored edge, vertical rails their side.
        boolean horizontal=maxX-minX>=maxY-minY;
        return choices[horizontal?(cy/points.length>=8?TOP:BOTTOM):(cx/points.length>=8?RIGHT:LEFT)];
    }
    static TextureAtlasSprite edge(TextureAtlasSprite sprite,int edge){Edge[] choices=EDGES.get(sprite.getIconName());return choices==null?sprite:choices[edge];}
    static BufferedImage crop(BufferedImage image,int edge){
        if(image.getWidth()>64||image.getHeight()>64){BufferedImage nativeImage=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++)nativeImage.setRGB(x,y,image.getRGB(x*image.getWidth()/64,y*image.getHeight()/64));image=nativeImage;}
        int w=image.getWidth(),h=image.getHeight();boolean vertical=edge==LEFT||edge==RIGHT;int along=vertical?h:w,depth=vertical?w:h;
        // Largest opaque rectangle within the appropriate outer third; never fall back to a hollow image.
        int[] heights=new int[along];int bestArea=0,bx=0,by=0,bw=1,bh=1;
        for(int y=0;y<Math.max(1,depth/3);y++){
            for(int x=0;x<along;x++)heights[x]=((pixel(image,edge,x,y)>>>24)>=240)?heights[x]+1:0;
            for(int x=0;x<along;x++)if(heights[x]>0){int height=heights[x];for(int r=x;r<along&&heights[r]>0;r++){height=Math.min(height,heights[r]);int area=(r-x+1)*height;if(area>bestArea){bestArea=area;bx=x;by=y-height+1;bw=r-x+1;bh=height;}}}
        }
        if(bestArea==0){boolean found=false;for(int y=0;y<depth&&!found;y++)for(int x=0;x<along;x++)if((pixel(image,edge,x,y)>>>24)>=240){bx=x;by=y;found=true;break;}if(!found)throw new IllegalArgumentException("Frame artwork contains no opaque material");}
        BufferedImage out=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)out.setRGB(x,y,pixel(image,edge,bx+Math.min(bw-1,x*bw/64),by+Math.min(bh-1,y*bh/64)));
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
