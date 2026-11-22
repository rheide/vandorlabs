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
    /** Opaque panes belong to the solid chunk layer; all others to the translucent layer. */
    final boolean opaque;
    final int depth;

    private GlassPaneTerrainMesh(boolean rotated,int depth,TextureAtlasSprite sprite,
            boolean single,VertexFormat format){
        this.rotated=rotated;this.depth=depth;this.sprite=sprite;singleSurface=single;opaque=false;
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
    private GlassPaneTerrainMesh(boolean rotated,int depth,TextureAtlasSprite sprite,VertexFormat format,List<BakedQuad> quads,boolean opaque){
        this.rotated=rotated;this.depth=depth;this.sprite=sprite;singleSurface=false;this.format=format;this.quads=quads;this.opaque=opaque;
    }

    /**
     * Selected artwork, already mapped onto the joined surface. The source mesh is
     * the two-sheet material plane; native backface culling leaves one visible
     * sheet, so {@code opacity} must already be the single-layer equivalent. Opaque
     * meshes are drawn in the solid layer, where the same culling applies.
     */
    static GlassPaneTerrainMesh mapped(boolean rotated,int depth,TextureAtlasSprite sprite,StaticSurfaceMesh mesh,
            float opacity,boolean opaque,VertexFormat source){
        if(sprite==null||mesh==null)throw new IllegalArgumentException("Missing pane artwork");
        if(!supports(source))throw new IllegalArgumentException("Unsupported native pane vertex format");
        VertexFormat format=new VertexFormat(source);
        float[] v=mesh.vertexArray();
        int stride=format.getIntegerSize(),color=format.getColorOffset()/4,uv=format.getUvOffsetById(0)/4;
        int alpha=Math.max(0,Math.min(255,Math.round(opacity*255F)));
        int packed=java.nio.ByteOrder.nativeOrder()==java.nio.ByteOrder.LITTLE_ENDIAN?(alpha<<24)|0x00FFFFFF:0xFFFFFF00|alpha;
        double offset=PanelDepth.offset(depth)/16D;
        List<BakedQuad> result=new ArrayList<>(v.length/32);
        for(int q=0;q+32<=v.length;q+=32){
            final int[] data=new int[stride*4];
            float fx=0,fz=0;
            for(int vertex=0;vertex<4;vertex++){
                int i=q+vertex*8,at=vertex*stride;
                double a=v[i]/16D,b=v[i+1]/16D,c=v[i+2]/16D,x=a,z=c;
                float nx=v[i+5],nz=v[i+7];
                // Matches StaticSurfaceMesh.drawPlacedColored rotation 3 and the TESR depth placement.
                if(rotated){x=1-c-offset;z=a;fx=-nz;fz=nx;}else{z=c+offset;fx=nx;fz=nz;}
                data[at]=Float.floatToRawIntBits((float)x);
                data[at+1]=Float.floatToRawIntBits((float)b);
                data[at+2]=Float.floatToRawIntBits((float)z);
                data[at+color]=packed;
                data[at+uv]=Float.floatToRawIntBits(v[i+3]);
                data[at+uv+1]=Float.floatToRawIntBits(v[i+4]);
                if(format.hasNormal())data[at+format.getNormalOffset()/4]=((Math.round(fx*127)&255))|((Math.round(v[i+6]*127)&255)<<8)|((Math.round(fz*127)&255)<<16);
            }
            EnumFacing face=Math.abs(fx)>=Math.abs(fz)?(fx>=0?EnumFacing.EAST:EnumFacing.WEST):(fz>=0?EnumFacing.SOUTH:EnumFacing.NORTH);
            result.add(new BakedQuad(data,-1,face,sprite,false,format){
                @Override public int[] getVertexData(){return data;}
            });
        }
        return new GlassPaneTerrainMesh(rotated,depth,sprite,format,Collections.unmodifiableList(result),opaque);
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
