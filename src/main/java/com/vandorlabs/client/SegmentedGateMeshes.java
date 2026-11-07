package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import java.util.*;
import java.util.function.Function;

/** One resource-generation bake. No world/tile references or per-draw names/JSON/mesh construction. */
public final class SegmentedGateMeshes {
    public static final int MAX_BYTES=4*1024*1024;
    private final StaticSurfaceMesh[][] base=new StaticSurfaceMesh[3][4];
    private final StaticSurfaceMesh[][][][] connectors=new StaticSurfaceMesh[3][4][4][2];
    private long retained=1024;
    private static int part(int mask){if((mask&~15)!=0)throw new IllegalArgumentException("Gate mask");return (mask&3)==0?0:(mask&3)==1?3:(mask&3)==2?1:2;}
    private static void kind(int kind){if(kind<0||kind>2)throw new IllegalArgumentException("Gate kind");}
    public long retainedBytes(){return retained;}
    public int baseVertices(int kind,int mask){kind(kind);return base[kind][part(mask)].vertexCount();}
    /** Active resource models and their resolved sprites; caller replaces this whole bake on reload. */
    public SegmentedGateMeshes(ModelBlock[][] models,Function<String,TextureAtlasSprite> sprites){
        if(models==null||models.length!=3||sprites==null)throw new IllegalArgumentException("Gate artwork models");FaceBakery bakery=new FaceBakery();
        for(int kind=0;kind<3;kind++){
            if(models[kind]==null||models[kind].length!=4)throw new IllegalArgumentException("Gate artwork variants");
            for(int part=0;part<4;part++){
                ModelBlock model=models[kind][part];if(model==null||model.getElements().size()>128)throw new IllegalArgumentException("Gate artwork capacity");
                List<List<BakedQuad>> faces=new ArrayList<>();StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
                for(BlockPart element:model.getElements()){
                    List<BakedQuad> elementFaces=new ArrayList<>();faces.add(elementFaces);
                    for(Map.Entry<EnumFacing,BlockPartFace> face:element.mapFaces.entrySet()){
                        TextureAtlasSprite sprite=sprites.apply(model.resolveTextureName(face.getValue().texture));if(sprite==null)throw new IllegalArgumentException("Missing gate sprite");
                        BakedQuad quad=bakery.makeBakedQuad(element.positionFrom,element.positionTo,face.getValue(),sprite,face.getKey(),ModelRotation.X0_Y0,element.partRotation,false,element.shade);elementFaces.add(quad);append(capture,quad);
                    }
                }
                base[kind][part]=capture.finish();retain(base[kind][part]);
                int mask=new int[]{0,2,3,1}[part];boolean canonical;try{canonical=canonical(model,SegmentedGateModels.model(kind,mask));}catch(IllegalArgumentException incompatible){canonical=false;}
                for(int peer=0;peer<4;peer++)for(int side=0;side<2;side++){
                    capture=StaticSurfaceMesh.capture();if(canonical){
                        for(SegmentedGateShape.Connector connector:SegmentedGateModels.connectors(kind,mask|(side==1?8:4),new int[]{0,2,3,1}[peer],side==1))
                            for(BakedQuad quad:faces.get(connector.element))appendConnector(capture,quad,connector.bounds);
                    }
                    connectors[kind][part][peer][side]=capture.finish();retain(connectors[kind][part][peer][side]);
                }
            }
        }
    }
    private void retain(StaticSurfaceMesh mesh){retained+=mesh.cacheRetainedBytes();if(retained>MAX_BYTES)throw new IllegalArgumentException("Gate artwork retained-byte capacity");}
    private static boolean canonical(ModelBlock model,SegmentedGateShape.Model canonical){
        if(model.getElements().size()!=canonical.count())return false;double[] actual=new double[3],expected=new double[3];
        for(int at=0;at<canonical.count();at++){
            BlockPart element=model.getElements().get(at);BlockPartRotation r=element.partRotation;int mask=0;for(EnumFacing face:element.mapFaces.keySet())mask|=1<<face.getIndex();
            SegmentedGateShape.Element active=new SegmentedGateShape.Element(new float[]{element.positionFrom.x,element.positionFrom.y,element.positionFrom.z},new float[]{element.positionTo.x,element.positionTo.y,element.positionTo.z},r==null?-1:r.axis==EnumFacing.Axis.X?0:r.axis==EnumFacing.Axis.Y?1:2,r==null?0:r.angle,r==null?null:new float[]{r.origin.x*16,r.origin.y*16,r.origin.z*16},r!=null&&r.rescale,mask);
            if(mask!=canonical.element(at).faceMask())return false;for(int corner=0;corner<8;corner++){active.corner(corner,actual,0);canonical.element(at).corner(corner,expected,0);if(!Arrays.equals(actual,expected))return false;}
        }return true;
    }
    private static void append(StaticSurfaceMesh.Capture out,BakedQuad quad){
        int[] data=quad.getVertexData();int stride=data.length/4,normal=quad.getFormat().getNormalOffset()/4;if(stride<7)throw new IllegalArgumentException("Gate vertex stride");
        for(int vertex=0;vertex<4;vertex++){
            int at=vertex*stride,packed=data[at+normal];float nx=(byte)packed/127F,ny=(byte)(packed>>>8)/127F,nz=(byte)(packed>>>16)/127F;
            out.pos(Float.intBitsToFloat(data[at])*16,Float.intBitsToFloat(data[at+1])*16,Float.intBitsToFloat(data[at+2])*16-8).tex(Float.intBitsToFloat(data[at+4]),Float.intBitsToFloat(data[at+5])).normal(nx,ny,nz).endVertex();
        }
    }
    private static void appendConnector(StaticSurfaceMesh.Capture out,BakedQuad quad,SegmentedGateShape.Box box){
        int[] data=quad.getVertexData();int stride=data.length/4;float[] face=new float[32];TextureAtlasSprite sprite=quad.getSprite();
        for(int vertex=0;vertex<4;vertex++){
            int at=vertex*stride;for(int axis=0;axis<3;axis++)face[vertex*8+axis]=Float.intBitsToFloat(data[at+axis]);face[vertex*8+3]=(Float.intBitsToFloat(data[at+4])-sprite.getMinU())/(sprite.getMaxU()-sprite.getMinU());face[vertex*8+4]=(Float.intBitsToFloat(data[at+5])-sprite.getMinV())/(sprite.getMaxV()-sprite.getMinV());face[vertex*8+5]=quad.getFace().getFrontOffsetX();face[vertex*8+6]=quad.getFace().getFrontOffsetY();face[vertex*8+7]=quad.getFace().getFrontOffsetZ();
        }
        SegmentedGateConnectorUV.emit(face,box,(x,y,z,u,v,nx,ny,nz)->out.pos(x*16,y*16,z*16-8).tex(sprite.getInterpolatedU(u*16),sprite.getInterpolatedV(v*16)).normal(nx,ny,nz).endVertex());
    }
    /** Neighbour masks come only from the prepared compatible-plane join policy. */
    public void emit(int kind,int mask,int belowMask,int aboveMask,BufferBuilder buffer,double x,double y,double z,double[] pose,int rotation,int light){
        kind(kind);int part=part(mask),below=part(belowMask),above=part(aboveMask);base[kind][part].drawSection(buffer,x,y,z,pose,rotation,light,255);
        if((mask&4)!=0)connectors[kind][part][below][0].drawSection(buffer,x,y,z,pose,rotation,light,255);
        if((mask&8)!=0)connectors[kind][part][above][1].drawSection(buffer,x,y,z,pose,rotation,light,255);
    }
}
