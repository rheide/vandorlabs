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
        return choices[com.vandorlabs.render.FrameEdgeMapping.edge(points)];
    }
    static TextureAtlasSprite edge(TextureAtlasSprite sprite,int edge){Edge[] choices=EDGES.get(sprite.getIconName());return choices==null?sprite:choices[edge];}
    /** Map the authored edge length along the rim and its thickness across the wall.
     * Opaque square finishes retain their physical UV scale. */
    static TextureAtlasSprite map(TextureAtlasSprite sprite,double[]... points){
        if(!authored(sprite))return sprite;
        TextureAtlasSprite selected=edge(sprite,points);
        com.vandorlabs.render.FrameEdgeMapping.map(((Edge)selected).edge,points);
        return selected;
    }
    static BufferedImage crop(BufferedImage image,int edge){return com.vandorlabs.render.FrameEdgePixels.crop(image,edge);}
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
