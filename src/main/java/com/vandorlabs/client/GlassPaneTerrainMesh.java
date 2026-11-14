package com.vandorlabs.client;

import com.vandorlabs.blocks.PanelDepth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;

/**
 * Immutable block-local quads for the native translucent chunk builder.
 * Every cell keeps its authored UVs; no overlay or atlas wrapping is needed.
 * Minecraft supplies live lighting and optional shader attributes when these
 * quads are written into the normal terrain buffer.
 */
final class GlassPaneTerrainMesh {
    final List<BakedQuad> quads;
    final VertexFormat format;
    final TextureAtlasSprite sprite;
    final boolean rotated,singleSurface;
    final int depth;

    private GlassPaneTerrainMesh(boolean rotated,int depth,TextureAtlasSprite sprite,
            boolean single,VertexFormat format){
        this.rotated=rotated;this.depth=depth;this.sprite=sprite;singleSurface=single;
        this.format=new VertexFormat(format);
        List<BakedQuad> result=new ArrayList<>(single?2:4);
        double offset=PanelDepth.offset(depth)/16D;
        for(int sheet=0;sheet<(single?1:2);sheet++){
            double plane=(single?.5:sheet==0?7D/16:9D/16)+offset;
            // Native terrain uses backface culling. Opposite windings make one
            // physical sheet visible from either side, with one visible face.
            result.add(quad(plane,true));result.add(quad(plane,false));
        }
        quads=Collections.unmodifiableList(result);
    }

    static GlassPaneTerrainMesh build(boolean rotated,int depth,TextureAtlasSprite sprite,
            boolean single,VertexFormat format){
        if(sprite==null)throw new IllegalArgumentException("Missing pane atlas sprite");
        if(!supports(format))throw new IllegalArgumentException("Unsupported native pane vertex format");
        return new GlassPaneTerrainMesh(rotated,depth,sprite,single,format);
    }

    /** Required native fields; additional shader payload remains the block writer's responsibility. */
    static boolean supports(VertexFormat format){
        if(format==null||format.getNextOffset()<=0||(format.getNextOffset()&3)!=0)return false;
        boolean position=false,color=false,texture=false,light=false;
        for(int i=0;i<format.getElementCount();i++){
            VertexFormatElement element=format.getElement(i);
            int offset=format.getOffset(i),count=element.getElementCount();
            VertexFormatElement.EnumType type=element.getType();
            switch(element.getUsage()){
                case POSITION:
                    if(element.getIndex()!=0||offset!=0||type!=VertexFormatElement.EnumType.FLOAT||count!=3)return false;
                    position=true;break;
                case COLOR:
                    if(element.getIndex()!=0||(offset&3)!=0||type!=VertexFormatElement.EnumType.UBYTE||count!=4)return false;
                    color=true;break;
                case UV:
                    if(element.getIndex()==0){
                        if((offset&3)!=0||type!=VertexFormatElement.EnumType.FLOAT||count!=2)return false;
                        texture=true;
                    }else if(element.getIndex()==1){
                        if((offset&3)!=0||type!=VertexFormatElement.EnumType.SHORT||count!=2)return false;
                        light=true;
                    }
                    break;
                case NORMAL:
                    if(element.getIndex()!=0||(offset&3)!=0||type!=VertexFormatElement.EnumType.BYTE||count!=3)return false;
                    break;
                default:break;
            }
        }
        return position&&color&&texture&&light;
    }

    private BakedQuad quad(double plane,boolean near){
        int stride=format.getIntegerSize(),color=format.getColorOffset()/4,uv=format.getUvOffsetById(0)/4;
        final int[] data=new int[stride*4];
        int nx=rotated?(near?1:-1):0,nz=rotated?0:(near?-1:1);
        for(int vertex=0;vertex<4;vertex++){
            int index=near?(4-vertex)&3:vertex;
            float u=index==1||index==2?1:0,v=index<2?0:1;
            int at=vertex*stride;
            data[at]=Float.floatToRawIntBits(rotated?(float)(1-plane):u);
            data[at+1]=Float.floatToRawIntBits(v);
            data[at+2]=Float.floatToRawIntBits(rotated?u:(float)plane);
            data[at+color]=-1;
            data[at+uv]=Float.floatToRawIntBits(sprite.getInterpolatedU(u*16));
            data[at+uv+1]=Float.floatToRawIntBits(sprite.getInterpolatedV((1-v)*16));
            if(format.hasNormal())data[at+format.getNormalOffset()/4]=((nx*127)&255)|(((nz*127)&255)<<16);
        }
        EnumFacing face=rotated?(near?EnumFacing.EAST:EnumFacing.WEST):(near?EnumFacing.NORTH:EnumFacing.SOUTH);
        // Pin the immutable layout. OptiFine's native block writer owns entity
        // IDs, tangents and mid-UV; no private shader hooks run in this emitter.
        return new BakedQuad(data,-1,face,sprite,false,format){
            @Override public int[] getVertexData(){return data;}
        };
    }

    int bytes(){return 160+quads.size()*(256+16*format.getIntegerSize());}
}
