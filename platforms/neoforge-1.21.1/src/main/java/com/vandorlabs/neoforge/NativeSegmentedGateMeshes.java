package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.segmented.*;
import com.vandorlabs.neoforge.segmented.NativeSegmentedGateModels;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.*;
import java.util.function.*;

/** One immutable atlas generation of authored gates; no world reads or model lookup while drawing. */
public final class NativeSegmentedGateMeshes {
    public static final int MAX_BYTES=4*1024*1024;
    private final NativeSegmentedMesh[][] base=new NativeSegmentedMesh[3][4];
    private final NativeSegmentedMesh[][][][] joins=new NativeSegmentedMesh[3][4][4][2];
    private int retained=1024;
    private static int part(int mask){if((mask&~15)!=0)throw new IllegalArgumentException("Gate mask");return (mask&3)==0?0:(mask&3)==1?3:(mask&3)==2?1:2;}
    private static void kind(int kind){if(kind<0||kind>2)throw new IllegalArgumentException("Gate kind");}
    public int retainedBytes(){return retained;}
    public int baseVertices(int kind,int mask){kind(kind);return base[kind][part(mask)].vertices();}
    public NativeSegmentedGateMeshes(Function<String,BakedModel> models,Predicate<String> canonicalGeometry){
        int[] masks={0,2,3,1};for(int kind=0;kind<3;kind++)for(int part=0;part<4;part++){
            String id=SegmentedGateJoin.model(kind,masks[part]);BakedModel model=Objects.requireNonNull(models.apply(id),"Gate model");boolean canonical=canonicalGeometry.test(id);List<BakedQuad> quads;
            if(canonical&&NativeDoorArtwork.isStatic(model))quads=NativeDoorArtwork.quads(id,model);else{List<BakedQuad> raw=new ArrayList<>();for(Direction side:Direction.values())raw.addAll(model.getQuads(null,side,RandomSource.create(0),ModelData.EMPTY,null));raw.addAll(model.getQuads(null,null,RandomSource.create(0),ModelData.EMPTY,null));quads=NativeDoorArtwork.content(raw);}
            if(quads.size()>4096)throw new IllegalArgumentException("Gate quad capacity");NativeSegmentedMesh.Builder builder=new NativeSegmentedMesh.Builder();for(BakedQuad quad:quads)append(builder,quad);base[kind][part]=builder.finish();retain(base[kind][part]);
            SegmentedGateShape.Model geometry=NativeSegmentedGateModels.model(kind,masks[part]);List<List<BakedQuad>> elements=new ArrayList<>();for(int at=0;at<geometry.count();at++)elements.add(new ArrayList<>());
            if(canonical){for(BakedQuad quad:quads){int element=element(geometry,quad);if(element<0){canonical=false;break;}elements.get(element).add(quad);}for(int at=0;at<geometry.count();at++)if(elements.get(at).size()!=Integer.bitCount(geometry.element(at).faceMask()))canonical=false;}
            for(int peer=0;peer<4;peer++)for(int side=0;side<2;side++){
                builder=new NativeSegmentedMesh.Builder();if(canonical)for(SegmentedGateShape.Connector connector:NativeSegmentedGateModels.connectors(kind,masks[part]|(side==1?8:4),masks[peer],side==1))for(BakedQuad quad:elements.get(connector.element))appendConnector(builder,quad,connector.bounds);
                joins[kind][part][peer][side]=builder.finish();retain(joins[kind][part][peer][side]);
            }
        }
    }
    private void retain(NativeSegmentedMesh mesh){retained+=mesh.retainedBytes();if(retained>MAX_BYTES)throw new IllegalArgumentException("Gate mesh retained-byte capacity");}
    private static int element(SegmentedGateShape.Model model,BakedQuad quad){
        int[] data=quad.getVertices();int stride=data.length/4;double[] point=new double[3];for(int at=0;at<model.count();at++){boolean match=true;for(int vertex=0;vertex<4;vertex++){boolean found=false;for(int corner=0;corner<8;corner++){model.element(at).corner(corner,point,0);found|=Math.abs(point[0]-Float.intBitsToFloat(data[vertex*stride]))<1e-6&&Math.abs(point[1]-Float.intBitsToFloat(data[vertex*stride+1]))<1e-6&&Math.abs(point[2]-Float.intBitsToFloat(data[vertex*stride+2]))<1e-6;}match&=found;}if(match)return at;}return -1;
    }
    private static void append(NativeSegmentedMesh.Builder out,BakedQuad quad){
        int[] data=quad.getVertices();int stride=data.length/4;if(stride<8)throw new IllegalArgumentException("Gate vertex stride");for(int vertex=0;vertex<4;vertex++){int at=vertex*stride,n=data[at+7];out.vertex(Float.intBitsToFloat(data[at])*16,Float.intBitsToFloat(data[at+1])*16,Float.intBitsToFloat(data[at+2])*16-8,Float.intBitsToFloat(data[at+4]),Float.intBitsToFloat(data[at+5]),(byte)n/127F,(byte)(n>>>8)/127F,(byte)(n>>>16)/127F);}
    }
    private static void appendConnector(NativeSegmentedMesh.Builder out,BakedQuad quad,SegmentedGateShape.Box box){
        int[] data=quad.getVertices();int stride=data.length/4;float[] face=new float[32];var sprite=quad.getSprite();float usedU=NativeDoorArtwork.usedU(sprite),usedV=NativeDoorArtwork.usedV(sprite),du=(sprite.getU1()-sprite.getU0())*usedU,dv=(sprite.getV1()-sprite.getV0())*usedV;
        for(int vertex=0;vertex<4;vertex++){int at=vertex*stride;for(int axis=0;axis<3;axis++)face[vertex*8+axis]=Float.intBitsToFloat(data[at+axis]);face[vertex*8+3]=(Float.intBitsToFloat(data[at+4])-sprite.getU0())/du;face[vertex*8+4]=(Float.intBitsToFloat(data[at+5])-sprite.getV0())/dv;face[vertex*8+5]=quad.getDirection().getStepX();face[vertex*8+6]=quad.getDirection().getStepY();face[vertex*8+7]=quad.getDirection().getStepZ();}
        SegmentedGateConnectorUV.emit(face,box,(x,y,z,u,v,nx,ny,nz)->out.vertex(x*16,y*16,z*16-8,sprite.getU0()+du*(float)u,sprite.getV0()+dv*(float)v,nx,ny,nz));
    }
    public void emit(int kind,int mask,int belowMask,int aboveMask,PoseStack.Pose matrix,VertexConsumer target,double[] pose,int rotation,int light,int overlay){
        kind(kind);int part=part(mask),below=part(belowMask),above=part(aboveMask);base[kind][part].emit(matrix,target,pose,rotation,light,overlay,255);if((mask&4)!=0)joins[kind][part][below][0].emit(matrix,target,pose,rotation,light,overlay,255);if((mask&8)!=0)joins[kind][part][above][1].emit(matrix,target,pose,rotation,light,overlay,255);
    }
}
