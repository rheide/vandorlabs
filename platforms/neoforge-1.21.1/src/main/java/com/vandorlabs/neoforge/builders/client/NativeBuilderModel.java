package com.vandorlabs.neoforge.builders.client;

import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/** Immutable atlas-generation mesh. Frame rendering never reparses OBJ or allocates vertex arrays. */
public final class NativeBuilderModel {
    private final int[] vertices;
    private final float[] bottomUV;
    private static final class Scratch {final Vector3f position=new Vector3f(),normal=new Vector3f();final float[][] clip=new float[6][5];}
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    public NativeBuilderModel(BakedModel model){this(quads(model));}
    private static List<BakedQuad> quads(BakedModel model){var result=new ArrayList<BakedQuad>();var random=RandomSource.create(0);result.addAll(model.getQuads(null,null,random));for(var face:Direction.values())result.addAll(model.getQuads(null,face,random));return result;}
    public NativeBuilderModel(List<BakedQuad> quads){
        if(quads.size()>8192)throw new IllegalArgumentException("Builder single-mesh byte budget");
        vertices=new int[quads.size()*32];bottomUV=new float[quads.size()*8];int at=0;
        for(var quad:quads){int[] source=quad.getVertices();int stride=source.length/4;if(stride<8||source.length%4!=0)throw new IllegalArgumentException("Builder vertex format");
            for(int v=0;v<4;v++)System.arraycopy(source,v*stride,vertices,at+v*8,8);
            for(int v=0;v<4;v++){
                int p=at+v*8,other=v;
                for(int b=0;b<4;b++){int q=at+b*8;if(vertices[p]==vertices[q]&&vertices[p+2]==vertices[q+2]&&Float.intBitsToFloat(vertices[q+1])<.01F){other=b;break;}}
                int uv=(at/8+v)*2;bottomUV[uv]=Float.intBitsToFloat(vertices[at+other*8+4]);bottomUV[uv+1]=Float.intBitsToFloat(vertices[at+other*8+5]);
                if(vertices[p+7]==0){var n=quad.getDirection();vertices[p+7]=(n.getStepX()*127&255)|(n.getStepY()*127&255)<<8|(n.getStepZ()*127&255)<<16;}
            }at+=32;
        }
    }
    public int vertexCount(){return vertices.length/8;}
    public int retainedBytes(){return 64+vertices.length*4+bottomUV.length*4;}
    public void emit(PoseStack.Pose pose,VertexConsumer target,int light,int overlay){emit(pose,target,light,overlay,-1);}
    /** Clip actual scaffold polygons at the fabrication plane, preserving diagonal struts and empty space. */
    public void emitClipped(PoseStack.Pose pose,VertexConsumer target,int light,int overlay,float height){
        float limit=Math.clamp(height,.001F,1F);var scratch=SCRATCH.get();
        for(int quad=0;quad<vertices.length;quad+=32){int count=0;
            for(int vertex=0;vertex<4;vertex++){int a=quad+vertex*8,b=quad+(vertex+1)%4*8;float ay=Float.intBitsToFloat(vertices[a+1]),by=Float.intBitsToFloat(vertices[b+1]);boolean inside=ay<=limit,next=by<=limit;
                if(inside){for(int axis=0;axis<3;axis++)scratch.clip[count][axis]=Float.intBitsToFloat(vertices[a+axis]);scratch.clip[count][3]=Float.intBitsToFloat(vertices[a+4]);scratch.clip[count++][4]=Float.intBitsToFloat(vertices[a+5]);}
                if(inside!=next){float fraction=(limit-ay)/(by-ay);for(int axis=0;axis<3;axis++){float value=Float.intBitsToFloat(vertices[a+axis]);scratch.clip[count][axis]=value+(Float.intBitsToFloat(vertices[b+axis])-value)*fraction;}for(int uv=0;uv<2;uv++){float value=Float.intBitsToFloat(vertices[a+4+uv]);scratch.clip[count][3+uv]=value+(Float.intBitsToFloat(vertices[b+4+uv])-value)*fraction;}count++;}
            }
            for(int triangle=1;triangle<count-1;triangle++){clippedVertex(pose,target,light,overlay,scratch.clip[0],quad);clippedVertex(pose,target,light,overlay,scratch.clip[triangle],quad);clippedVertex(pose,target,light,overlay,scratch.clip[triangle+1],quad);clippedVertex(pose,target,light,overlay,scratch.clip[triangle+1],quad);}
        }
    }
    private void clippedVertex(PoseStack.Pose pose,VertexConsumer target,int light,int overlay,float[] vertex,int quad){var s=SCRATCH.get();pose.pose().transformPosition(vertex[0],vertex[1],vertex[2],s.position);int normal=vertices[quad+7],color=vertices[quad+3];pose.normal().transform((byte)normal/127F,(byte)(normal>>>8)/127F,(byte)(normal>>>16)/127F,s.normal);target.addVertex(s.position.x,s.position.y,s.position.z).setColor(color&255,color>>>8&255,color>>>16&255,color>>>24).setUv(vertex[3],vertex[4]).setLight(light).setOverlay(overlay).setNormal(s.normal.x,s.normal.y,s.normal.z);}

    private void emit(PoseStack.Pose pose,VertexConsumer target,int light,int overlay,float height){
        var s=SCRATCH.get();
        for(int i=0;i<vertices.length;i+=8){
            float x=Float.intBitsToFloat(vertices[i]),y=Float.intBitsToFloat(vertices[i+1]),z=Float.intBitsToFloat(vertices[i+2]);
            float u=Float.intBitsToFloat(vertices[i+4]),v=Float.intBitsToFloat(vertices[i+5]);int normal=vertices[i+7];
            float nx=(byte)normal/127F,ny=(byte)(normal>>>8)/127F,nz=(byte)(normal>>>16)/127F;
            if(height>=0&&y>height){if(Math.abs(ny)<.5F){int uv=i/4;u=bottomUV[uv]+(u-bottomUV[uv])*height;v=bottomUV[uv+1]+(v-bottomUV[uv+1])*height;}y=height;}
            pose.pose().transformPosition(x,y,z,s.position);pose.normal().transform(nx,ny,nz,s.normal);int color=vertices[i+3];
            target.addVertex(s.position.x,s.position.y,s.position.z).setColor(color&255,color>>>8&255,color>>>16&255,color>>>24).setUv(u,v).setLight(light).setOverlay(overlay).setNormal(s.normal.x,s.normal.y,s.normal.z);
        }
    }
}
