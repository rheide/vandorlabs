package com.vandorlabs.neoforge.gear.client;

import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/** Shared immutable source vertices and an eight-MiB weighted endpoint transform cache per atlas generation. */
public final class NativeGearMeshes {
    private record Mesh(float[] vertices){}
    private record Key(String name,NativeGearAffine transform){}
    private final Map<String,Mesh> meshes;
    private final LinkedHashMap<Key,float[]> cached=new LinkedHashMap<>(64,.75F,true);
    private int retained,cachedBytes;
    NativeGearMeshes(Map<ModelResourceLocation,BakedModel> models){var sources=new HashMap<String,Mesh>();for(String name:NativeGearResources.MODELS){var model=Objects.requireNonNull(models.get(ModelResourceLocation.standalone(NativeGearResources.model(name))),"Missing arm part "+name);var quads=new ArrayList<BakedQuad>();var random=RandomSource.create(0);quads.addAll(model.getQuads(null,null,random));for(var side:Direction.values())quads.addAll(model.getQuads(null,side,random));float[] data=new float[quads.size()*4*8];int at=0;for(var quad:quads){if(quad.getSprite().contents().name().getPath().contains("missingno"))throw new IllegalStateException("Unresolved arm sprite "+name);int[] raw=quad.getVertices();int stride=raw.length/4;if(stride<8||raw.length%4!=0)throw new IllegalArgumentException("Invalid authored arm vertices");for(int v=0;v<4;v++){int o=v*stride;for(int a=0;a<3;a++)data[at++]=Float.intBitsToFloat(raw[o+a]);data[at++]=Float.intBitsToFloat(raw[o+4]);data[at++]=Float.intBitsToFloat(raw[o+5]);int normal=raw[o+7];for(int a=0;a<3;a++)data[at++]=(byte)(normal>>>(a*8))/127F;}}sources.put(name,new Mesh(data));retained+=128+data.length*4;}if(retained>4*1024*1024)throw new IllegalArgumentException("Arm source geometry exceeds four MiB");meshes=Map.copyOf(sources);}
    public int retainedBytes(){return retained+cachedBytes;}
    public int vertices(String name){return meshes.get(name).vertices.length/8;}
    private static float[] transform(float[] source,NativeGearAffine a){float[] result=new float[source.length];double cxx=a.yy*a.zz-a.yz*a.zy,cxy=a.yz*a.zx-a.yx*a.zz,cxz=a.yx*a.zy-a.yy*a.zx,cyx=a.xz*a.zy-a.xy*a.zz,cyy=a.xx*a.zz-a.xz*a.zx,cyz=a.xy*a.zx-a.xx*a.zy,czx=a.xy*a.yz-a.xz*a.yy,czy=a.xz*a.yx-a.xx*a.yz,czz=a.xx*a.yy-a.xy*a.yx;for(int i=0;i<source.length;i+=8){double x=source[i],y=source[i+1],z=source[i+2];result[i]=(float)(a.xx*x+a.xy*y+a.xz*z+a.tx);result[i+1]=(float)(a.yx*x+a.yy*y+a.yz*z+a.ty);result[i+2]=(float)(a.zx*x+a.zy*y+a.zz*z+a.tz);result[i+3]=source[i+3];result[i+4]=source[i+4];x=source[i+5];y=source[i+6];z=source[i+7];double nx=cxx*x+cxy*y+cxz*z,ny=cyx*x+cyy*y+cyz*z,nz=czx*x+czy*y+czz*z,len=Math.sqrt(nx*nx+ny*ny+nz*nz);result[i+5]=(float)(len<1e-12?0:nx/len);result[i+6]=(float)(len<1e-12?1:ny/len);result[i+7]=(float)(len<1e-12?0:nz/len);}return result;}
    private float[] vertices(String name,NativeGearAffine transform,boolean cache){var mesh=Objects.requireNonNull(meshes.get(name),name);var key=new Key(name,transform);if(cache){var found=cached.get(key);if(found!=null)return found;}var data=transform(mesh.vertices,transform);if(cache){int bytes=256+data.length*4;if(bytes<=8*1024*1024){while(cachedBytes+bytes>8*1024*1024&&!cached.isEmpty()){var entry=cached.entrySet().iterator();var old=entry.next();cachedBytes-=256+old.getValue().length*4;entry.remove();}cached.put(key,data);cachedBytes+=bytes;}}return data;}
    public void bounds(String name,NativeGearAffine a,double[] bounds){for(int i=0;i<meshes.get(name).vertices.length;i+=8){var source=meshes.get(name).vertices;double x=source[i],y=source[i+1],z=source[i+2],px=a.xx*x+a.xy*y+a.xz*z+a.tx,py=a.yx*x+a.yy*y+a.yz*z+a.ty,pz=a.zx*x+a.zy*y+a.zz*z+a.tz;bounds[0]=Math.min(bounds[0],px);bounds[1]=Math.min(bounds[1],py);bounds[2]=Math.min(bounds[2],pz);bounds[3]=Math.max(bounds[3],px);bounds[4]=Math.max(bounds[4],py);bounds[5]=Math.max(bounds[5],pz);}}
    private static void vertex(PoseStack.Pose pose,VertexConsumer buffer,int light,int overlay,float x,float y,float z,float u,float v,float nx,float ny,float nz){float shade=nx*nx*.6F+ny*ny*(ny>0?1:.5F)+nz*nz*.8F;buffer.addVertex(pose.pose(),x,y,z).setColor(shade,shade,shade,1).setUv(u,v).setOverlay(overlay).setLight(light).setNormal(pose,nx,ny,nz);}
    public void draw(String name,NativeGearAffine a,boolean cache,PoseStack.Pose pose,VertexConsumer buffer,int light,int overlay){
        if(cache){float[] data=vertices(name,a,true);for(int i=0;i<data.length;i+=8)vertex(pose,buffer,light,overlay,data[i],data[i+1],data[i+2],data[i+3],data[i+4],data[i+5],data[i+6],data[i+7]);return;}
        // Moving poses stream the shared source records directly: no per-frame vertex arrays.
        var data=Objects.requireNonNull(meshes.get(name),name).vertices;
        double cxx=a.yy*a.zz-a.yz*a.zy,cxy=a.yz*a.zx-a.yx*a.zz,cxz=a.yx*a.zy-a.yy*a.zx,cyx=a.xz*a.zy-a.xy*a.zz,cyy=a.xx*a.zz-a.xz*a.zx,cyz=a.xy*a.zx-a.xx*a.zy,czx=a.xy*a.yz-a.xz*a.yy,czy=a.xz*a.yx-a.xx*a.yz,czz=a.xx*a.yy-a.xy*a.yx;
        for(int i=0;i<data.length;i+=8){double x=data[i],y=data[i+1],z=data[i+2];float px=(float)(a.xx*x+a.xy*y+a.xz*z+a.tx),py=(float)(a.yx*x+a.yy*y+a.yz*z+a.ty),pz=(float)(a.zx*x+a.zy*y+a.zz*z+a.tz);x=data[i+5];y=data[i+6];z=data[i+7];double nx=cxx*x+cxy*y+cxz*z,ny=cyx*x+cyy*y+cyz*z,nz=czx*x+czy*y+czz*z,len=Math.sqrt(nx*nx+ny*ny+nz*nz);vertex(pose,buffer,light,overlay,px,py,pz,data[i+3],data[i+4],(float)(len<1e-12?0:nx/len),(float)(len<1e-12?1:ny/len),(float)(len<1e-12?0:nz/len));}
    }
}
