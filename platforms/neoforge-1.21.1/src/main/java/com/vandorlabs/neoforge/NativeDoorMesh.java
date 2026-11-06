package com.vandorlabs.neoforge;

import com.google.gson.JsonArray;
import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.client.NativeDoorSpriteSource;
import com.vandorlabs.render.DoorMeshTransform;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Inactive Door body/pane backend. Bake owns static meshes; live motion never rebuilds them. */
public final class NativeDoorMesh {
    private static final int BUDGET=16*1024*1024,MAX_MESH=1024*1024;
    private final int[] source;private final boolean[] shade;private final boolean panel,lit,unlit;
    private NativeDoorMesh(int[] source,boolean[] shade,boolean panel){this.source=source;this.shade=shade;this.panel=panel;boolean lit=false,unlit=false;for(boolean value:shade){lit|=value;unlit|=!value;}this.lit=lit;this.unlit=unlit;}
    public boolean hasDiffuse(boolean value){return value?lit:unlit;}
    public int vertices(){return source.length/8;}
    public int retainedBytes(){return 96+source.length*4+shade.length;}
    /** Drop this generation-local byte-bounded cache on resource reload. */
    public static final class Bake {
        private final com.google.common.cache.Cache<BakedModel,NativeDoorMesh> meshes=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(BUDGET).weigher((BakedModel key,NativeDoorMesh mesh)->mesh.retainedBytes()+64).build();
        public NativeDoorMesh prepare(BakedModel model,JsonArray elements){
            var mesh=meshes.getIfPresent(model);if(mesh!=null)return mesh;if(!NativeAuthoredControlBake.isStatic(model))return null;
            var corrected=elements==null?model:NativeAuthoredControlBake.correct(model,null,new NativeMountedControlGeometry.Authored(elements,0,0));
            var quads=new ArrayList<BakedQuad>();for(Direction side:Direction.values())quads.addAll(corrected.getQuads(null,side,RandomSource.create(0),ModelData.EMPTY,null));quads.addAll(corrected.getQuads(null,null,RandomSource.create(0),ModelData.EMPTY,null));
            mesh=prepareQuads(quads,false);meshes.put(model,mesh);return mesh;
        }
        public long retainedBytes(){meshes.cleanUp();return meshes.asMap().values().stream().mapToLong(mesh->mesh.retainedBytes()+64).sum();}
        public void clear(){meshes.invalidateAll();}
    }
    public static NativeDoorMesh prepareQuads(List<BakedQuad> quads,boolean panel){return prepareQuads(quads,panel,true);}
    public static NativeDoorMesh prepareMappedQuads(List<BakedQuad> quads,boolean panel){return prepareQuads(quads,panel,false);}
    private static NativeDoorMesh prepareQuads(List<BakedQuad> quads,boolean panel,boolean content){
        if((long)quads.size()*128>MAX_MESH)throw new IllegalArgumentException("Door single-model byte budget");
        int[] source=new int[quads.size()*32];boolean[] shade=new boolean[quads.size()];int at=0,q=0;
        for(var quad:quads){int[] data=quad.getVertices();if(data.length!=32)throw new IllegalArgumentException("Native Door vertex format");System.arraycopy(data,0,source,at,32);shade[q++]=quad.isShade();
            if(content&&quad.getSprite().contents() instanceof NativeDoorSpriteSource.Contents descriptor){float u0=quad.getSprite().getU0(),v0=quad.getSprite().getV0();for(int v=0;v<4;v++){int i=at+v*8;source[i+4]=Float.floatToRawIntBits(u0+(Float.intBitsToFloat(source[i+4])-u0)*descriptor.usedU());source[i+5]=Float.floatToRawIntBits(v0+(Float.intBitsToFloat(source[i+5])-v0)*descriptor.usedV());}}at+=32;
        }return new NativeDoorMesh(source,shade,panel);
    }
    /** Numeric control pad uses the same exact six-face source as TESlidingDoor. */
    public static NativeDoorMesh numericPad(net.minecraft.client.renderer.texture.TextureAtlasSprite buttons,net.minecraft.client.renderer.texture.TextureAtlasSprite wall,boolean sliding,boolean farEdge,com.vandorlabs.render.SpaceDoorControlPanel.Side side){
        var quads=new ArrayList<BakedQuad>();
        com.vandorlabs.render.DoorControlPadMesh.UV buttonUV=new com.vandorlabs.render.DoorControlPadMesh.UV(){public float u(double p){return buttons.getU((float)(p/16));}public float v(double p){return buttons.getV((float)(p/16));}};
        com.vandorlabs.render.DoorControlPadMesh.UV wallUV=new com.vandorlabs.render.DoorControlPadMesh.UV(){public float u(double p){return wall.getU((float)(p/16));}public float v(double p){return wall.getV((float)(p/16));}};
        com.vandorlabs.render.DoorControlPadMesh.emit(sliding,farEdge,side,buttonUV,wallUV,(x0,y0,z0,x1,y1,z1,x2,y2,z2,x3,y3,z3,u0,v0,u1,v1,u2,v2,u3,v3)->{
            float[] points={x0,y0,z0,u0,v0,x1,y1,z1,u1,v1,x2,y2,z2,u2,v2,x3,y3,z3,u3,v3};int[] data=new int[32];
            double ax=x1-x0,ay=y1-y0,az=z1-z0,bx=x2-x0,by=y2-y0,bz=z2-z0,nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,length=Math.sqrt(nx*nx+ny*ny+nz*nz);nx/=length;ny/=length;nz/=length;
            int normal=((byte)(int)(nx*127)&255)|(((byte)(int)(ny*127)&255)<<8)|(((byte)(int)(nz*127)&255)<<16);
            for(int v=0;v<4;v++){for(int a=0;a<3;a++)data[v*8+a]=Float.floatToRawIntBits(points[v*5+a]/16);data[v*8+3]=-1;data[v*8+4]=Float.floatToRawIntBits(points[v*5+3]);data[v*8+5]=Float.floatToRawIntBits(points[v*5+4]);data[v*8+7]=normal;}
            quads.add(new BakedQuad(data,-1,Direction.getNearest((float)nx,(float)ny,(float)nz),quads.isEmpty()?buttons:wall,false,false));
        });return prepareQuads(quads,true);
    }
    private static final class Scratch {int[] transformed=new int[0];final org.joml.Vector3f position=new org.joml.Vector3f(),normal=new org.joml.Vector3f();}
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    /** Caller selects the body/pane layer and diffuse group; source colors and UVs survive. */
    public void emit(PoseStack.Pose pose,VertexConsumer target,Direction face,double depth,int light,int overlay,double shiftX,double shiftY,double pivotX,double pivotZ,double angle,double size,double leafOffset,float opacity,boolean diffuse){
        var s=SCRATCH.get();if(s.transformed.length<source.length)s.transformed=new int[source.length];DoorMeshTransform.write(source,8,s.transformed,panel,false,face.get3DDataValue(),0,0,0,depth,light,shiftX,shiftY,pivotX,pivotZ,angle,size,leafOffset);
        double radians=Math.toRadians(angle),cos=Math.cos(radians),sin=Math.sin(radians);
        for(int q=0;q<shade.length;q++){if(shade[q]!=diffuse)continue;for(int v=0;v<4;v++){
            int at=q*32+v*8,n=source[at+7];double nx=(byte)n/127D,ny=(byte)(n>>>8)/127D,nz=(byte)(n>>>16)/127D,rx=cos*nx+sin*nz,rz=-sin*nx+cos*nz;
            double wx=face==Direction.NORTH?-rx:face==Direction.EAST?rz:face==Direction.WEST?-rz:rx,wz=face==Direction.NORTH?-rz:face==Direction.EAST?-rx:face==Direction.WEST?rx:rz;
            pose.pose().transformPosition(Float.intBitsToFloat(s.transformed[at]),Float.intBitsToFloat(s.transformed[at+1]),Float.intBitsToFloat(s.transformed[at+2]),s.position);pose.transformNormal((float)wx,(float)ny,(float)wz,s.normal);
            int color=source[at+3],alpha=Math.max(0,Math.min(255,(int)((color>>>24)*opacity)));target.addVertex(s.position.x,s.position.y,s.position.z).setColor(color&255,(color>>>8)&255,(color>>>16)&255,alpha).setUv(Float.intBitsToFloat(source[at+4]),Float.intBitsToFloat(source[at+5])).setOverlay(overlay).setLight(light).setNormal(s.normal.x,s.normal.y,s.normal.z);
        }}
    }
}
