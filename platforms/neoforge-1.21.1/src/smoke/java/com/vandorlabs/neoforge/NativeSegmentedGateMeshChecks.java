package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.segmented.SegmentedGateJoin;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/** Prepared native emitter over actual Forge quad records; actual native model bake is a separate client check. */
public final class NativeSegmentedGateMeshChecks {
    private static int checks,queries;private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final class Sprite extends TextureAtlasSprite {Sprite(SpriteContents contents,int origin){super(TextureAtlas.LOCATION_BLOCKS,contents,512,512,origin,origin);}}
    private static BakedModel model(List<BakedQuad> quads){return (BakedModel)Proxy.newProxyInstance(BakedModel.class.getClassLoader(),new Class[]{BakedModel.class},(proxy,method,args)->{
        if(method.getName().equals("getQuads")){queries++;return args[1]==null?quads:List.of();}if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);if(method.getName().equals("equals"))return proxy==args[0];return method.getReturnType()==boolean.class?false:null;
    });}
    private static final class Capture implements VertexConsumer {
        final float[] data=new float[131072];int used,at;
        public VertexConsumer addVertex(float x,float y,float z){at=used;data[used++]=x;data[used++]=y;data[used++]=z;used+=5;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv(float u,float v){data[at+3]=u;data[at+4]=v;return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}public VertexConsumer setNormal(float x,float y,float z){data[at+5]=x;data[at+6]=y;data[at+7]=z;return this;}
    }
    public static void main(String[] args)throws Exception{
        try(var image=new NativeImage(128,128,false);var contents=new SpriteContents(ResourceLocation.parse("vandorlabs:gate_oracle"),new FrameSize(128,128),image,ResourceMetadata.EMPTY)){
            var sprite=new Sprite(contents,128);Map<String,List<BakedQuad>> records=new HashMap<>();Map<String,BakedModel> models=new HashMap<>();
            for(String line:Files.readAllLines(Path.of(args[0]))){var record=JsonParser.parseString(line).getAsJsonObject();if(record.get("yaw").getAsInt()!=0)continue;List<BakedQuad> quads=new ArrayList<>();for(var value:record.getAsJsonArray("quads")){var q=value.getAsJsonObject();var source=q.getAsJsonArray("vertices");int[] data=new int[32];for(int vertex=0;vertex<4;vertex++){for(int a=0;a<6;a++)data[vertex*8+a]=source.get(vertex*7+a).getAsInt();data[vertex*8+7]=source.get(vertex*7+6).getAsInt();}quads.add(new BakedQuad(data,-1,Direction.from3DDataValue(q.get("face").getAsInt()),sprite,q.get("shade").getAsBoolean(),false));}String id=record.get("id").getAsString();records.put(id,quads);models.put(id,model(quads));}
            var bake=new NativeSegmentedGateMeshes(models::get,id->true);need(bake.retainedBytes()<=NativeSegmentedGateMeshes.MAX_BYTES,"retained byte bound");var capture=new Capture();var matrix=new PoseStack().last();double[] pose={.5,.5,.5,0,1,1};int vertices=0;int[] masks={0,2,3,1};
            for(int kind=0;kind<3;kind++)for(int part=0;part<4;part++)for(int rotation=0;rotation<4;rotation++){
                capture.used=0;bake.emit(kind,masks[part],0,0,matrix,capture,pose,rotation,0,0);int at=0;for(BakedQuad quad:records.get(SegmentedGateJoin.model(kind,masks[part]))){int[] data=quad.getVertices();for(int vertex=0;vertex<4;vertex++){
                    double x=Float.intBitsToFloat(data[vertex*8]),y=Float.intBitsToFloat(data[vertex*8+1]),z=Float.intBitsToFloat(data[vertex*8+2]);int packed=data[vertex*8+7];float nx=(byte)packed/127F,ny=(byte)(packed>>>8)/127F,nz=(byte)(packed>>>16)/127F;
                    double[] expected={rotation==1?z:rotation==2?1-x:rotation==3?1-z:x,y,rotation==1?1-x:rotation==2?1-z:rotation==3?x:z,Float.intBitsToFloat(data[vertex*8+4]),Float.intBitsToFloat(data[vertex*8+5]),rotation==1?nz:rotation==2?-nx:rotation==3?-nz:nx,ny,rotation==1?-nx:rotation==2?-nz:rotation==3?nx:nz};for(int a=0;a<8;a++)need(Math.abs(capture.data[at++]-expected[a])<1e-6,"native emitted source position/UV/normal");vertices++;
                }}need(at==capture.used&&at/8==bake.baseVertices(kind,masks[part]),"complete native mesh");
            }
            for(int kind=0;kind<3;kind++)for(int mask=0;mask<16;mask++)for(int peer=0;peer<4;peer++){
                capture.used=0;bake.emit(kind,mask,masks[peer],masks[peer],matrix,capture,pose,0,0,0);need(capture.used/8>=bake.baseVertices(kind,mask),"retained base geometry");if((mask&8)!=0)need(capture.used/8>bake.baseVertices(kind,mask),"canonical top connectors are present");for(int at=0;at<capture.used;at+=8){need(capture.data[at]>=-1e-6&&capture.data[at]<=1+1e-6&&capture.data[at+1]>=-1e-6&&capture.data[at+1]<=1+1e-6&&capture.data[at+2]>=7D/16-1e-6&&capture.data[at+2]<=9D/16+1e-6,"native joined envelope");need(capture.data[at+3]>=sprite.getU0()-1e-6&&capture.data[at+3]<=sprite.getU1()+1e-6&&capture.data[at+4]>=sprite.getV0()-1e-6&&capture.data[at+4]<=sprite.getV1()+1e-6,"native joined atlas bounds");}
            }
            var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();for(int i=0;i<10000;i++){capture.used=0;bake.emit(i%3,12|(i&3),3,3,matrix,capture,pose,0,0,0);}int beforeQueries=queries;long before=meter.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++){capture.used=0;bake.emit(i%3,12|(i&3),3,3,matrix,capture,pose,0,0,0);}long allocated=meter.getThreadAllocatedBytes(thread)-before;need(allocated==0&&queries==beforeQueries,"warm native prepared allocation/query count "+allocated);
            var key=SegmentedGateJoin.model(0,0);var custom=new HashMap<>(models);custom.put(key,model(List.of(records.get(key).getFirst())));var changed=new NativeSegmentedGateMeshes(custom::get,id->!id.equals(key));capture.used=0;changed.emit(0,8,0,0,matrix,capture,pose,0,0,0);need(capture.used==32,"pack replacement keeps its geometry without original connectors");
            System.out.println("Native Segmented gate mesh PASS authoredVertices="+vertices+" checks="+checks+" retainedBytes="+bake.retainedBytes()+" warm10000Bytes="+allocated+" warmModelQueries=0");
        }
    }
}
