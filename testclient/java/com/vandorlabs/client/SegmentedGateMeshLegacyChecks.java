package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.EnumFacing;
import java.io.StringReader;
import java.util.*;

/** Runtime mesh adapter against independently emitted Forge positions/UVs/normals; no GL context. */
public final class SegmentedGateMeshLegacyChecks {
    private static int checks;private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final class Capture extends BufferBuilder {
        final float[] data=new float[131072];int used;float x,y,z,u,v,nx,ny,nz;
        Capture(){super(16);}public VertexFormat getVertexFormat(){return DefaultVertexFormats.ITEM;}
        public BufferBuilder pos(double x,double y,double z){this.x=(float)x;this.y=(float)y;this.z=(float)z;return this;}
        public BufferBuilder tex(double u,double v){this.u=(float)u;this.v=(float)v;return this;}
        public BufferBuilder normal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
        public BufferBuilder lightmap(int a,int b){return this;}public BufferBuilder color(int r,int g,int b,int a){return this;}
        public void endVertex(){data[used++]=x;data[used++]=y;data[used++]=z;data[used++]=u;data[used++]=v;data[used++]=nx;data[used++]=ny;data[used++]=nz;}
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();TextureAtlasSprite sprite=new TextureAtlasSprite("gate_mesh_reference"){};sprite.setIconWidth(128);sprite.setIconHeight(128);sprite.initSprite(512,512,128,128,false);
        ModelBlock[][] models=new ModelBlock[3][4];int[] masks={0,2,3,1};for(int kind=0;kind<3;kind++)for(int part=0;part<4;part++)models[kind][part]=DoorArtworkLegacyChecks.load(SegmentedGateJoin.model(kind,masks[part]));
        SegmentedGateMeshes meshes=new SegmentedGateMeshes(models,name->sprite);need(meshes.retainedBytes()<=SegmentedGateMeshes.MAX_BYTES,"retained-byte bound");Capture capture=new Capture();FaceBakery bakery=new FaceBakery();double[] pose={.5,.5,.5,0,1,1};int vertices=0;
        for(int kind=0;kind<3;kind++)for(int part=0;part<4;part++)for(int rotation=0;rotation<4;rotation++){
            capture.used=0;meshes.emit(kind,masks[part],0,0,capture,0,0,0,pose,rotation,0);int at=0;
            for(BlockPart element:models[kind][part].getElements())for(Map.Entry<EnumFacing,BlockPartFace> face:element.mapFaces.entrySet()){
                BakedQuad reference=bakery.makeBakedQuad(element.positionFrom,element.positionTo,face.getValue(),sprite,face.getKey(),ModelRotation.X0_Y0,element.partRotation,false,element.shade);int[] data=reference.getVertexData();int stride=data.length/4,normal=reference.getFormat().getNormalOffset()/4;
                for(int vertex=0;vertex<4;vertex++){
                    double x=Float.intBitsToFloat(data[vertex*stride]),y=Float.intBitsToFloat(data[vertex*stride+1]),z=Float.intBitsToFloat(data[vertex*stride+2]);int packed=data[vertex*stride+normal];float nx=(byte)packed/127F,ny=(byte)(packed>>>8)/127F,nz=(byte)(packed>>>16)/127F;
                    double[] expected={rotation==1?z:rotation==2?1-x:rotation==3?1-z:x,y,rotation==1?1-x:rotation==2?1-z:rotation==3?x:z,Float.intBitsToFloat(data[vertex*stride+4]),Float.intBitsToFloat(data[vertex*stride+5]),rotation==1?nz:rotation==2?-nx:rotation==3?-nz:nx,ny,rotation==1?-nx:rotation==2?-nz:rotation==3?nx:nz};
                    for(int a=0;a<8;a++)need(Math.abs(capture.data[at++]-expected[a])<1e-6,"runtime authored mesh vertex/UV/normal");vertices++;
                }
            }need(at==capture.used&&at/8==meshes.baseVertices(kind,masks[part]),"complete authored base mesh");
        }
        for(int kind=0;kind<3;kind++)for(int mask=0;mask<16;mask++)for(int peer=0;peer<4;peer++){
            capture.used=0;meshes.emit(kind,mask,masks[peer],masks[peer],capture,0,0,0,pose,0,0);need(capture.used/8>=meshes.baseVertices(kind,mask),"joined meshes retain original geometry");if((mask&8)!=0)need(capture.used/8>meshes.baseVertices(kind,mask),"canonical top connectors are present");for(int at=0;at<capture.used;at+=8){need(capture.data[at]>=-1e-6&&capture.data[at]<=1+1e-6&&capture.data[at+1]>=-1e-6&&capture.data[at+1]<=1+1e-6&&capture.data[at+2]>=7D/16-1e-6&&capture.data[at+2]<=9D/16+1e-6,"joined mesh stays in authored cell envelope");need(capture.data[at+3]>=sprite.getMinU()-1e-6&&capture.data[at+3]<=sprite.getMaxU()+1e-6&&capture.data[at+4]>=sprite.getMinV()-1e-6&&capture.data[at+4]<=sprite.getMaxV()+1e-6,"joined mesh UV atlas bounds");}
        }
        ModelBlock[][] replaced={models[0].clone(),models[1].clone(),models[2].clone()};replaced[0][0]=ModelBlock.deserialize(new StringReader("{\"textures\":{\"black\":\"vandorlabs:blocks/segmented_gates/black\"},\"elements\":[{\"from\":[2,1,7],\"to\":[5,14,9],\"faces\":{\"north\":{\"texture\":\"#black\",\"uv\":[0,0,16,16]}}}]}"));SegmentedGateMeshes custom=new SegmentedGateMeshes(replaced,name->sprite);capture.used=0;custom.emit(0,8,0,0,capture,0,0,0,pose,0,0);need(capture.used==32&&custom.baseVertices(0,0)==4,"replacement geometry retained without canonical connectors");
        com.sun.management.ThreadMXBean allocation=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();allocation.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        for(int warm=0;warm<10000;warm++){capture.used=0;meshes.emit(warm%3,12|(warm&3),3,3,capture,0,0,0,pose,0,0);}long before=allocation.getThreadAllocatedBytes(thread);for(int warm=0;warm<10000;warm++){capture.used=0;meshes.emit(warm%3,12|(warm&3),3,3,capture,0,0,0,pose,0,0);}long allocated=allocation.getThreadAllocatedBytes(thread)-before;need(allocated==0,"warm prepared emitter allocation "+allocated);
        capture.used=0;meshes.emit(0,0,0,0,capture,0,0,0,pose,0,0);float oldU=capture.data[3],oldV=capture.data[4];TextureAtlasSprite moved=new TextureAtlasSprite("gate_mesh_reload"){};moved.setIconWidth(128);moved.setIconHeight(128);moved.initSprite(512,512,256,256,false);SegmentedGateMeshes reloaded=new SegmentedGateMeshes(models,name->moved);capture.used=0;reloaded.emit(0,0,0,0,capture,0,0,0,pose,0,0);need(Math.abs(capture.data[3]-oldU-.25)<1e-6&&Math.abs(capture.data[4]-oldV-.25)<1e-6,"new bake replaces atlas coordinates");
        System.out.println("Source Segmented gate meshes PASS authoredVertices="+vertices+" checks="+checks+" retainedBytes="+meshes.retainedBytes()+" warm10000Bytes="+allocated);
    }
}
