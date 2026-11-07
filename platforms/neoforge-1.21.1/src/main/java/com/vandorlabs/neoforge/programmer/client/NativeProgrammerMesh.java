package com.vandorlabs.neoforge.programmer.client;
import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.programmer.*;
import com.vandorlabs.programmer.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
/** World-free casing submission. Caller owns the render layer, light sample and pose. */
public final class NativeProgrammerMesh {
 private static final String[] PARTS={"sample_input","sample_master","sample_output","screen","static"};
 private static final int[] BITS={2,1,4,0,0};
 private static final class Scratch{int[] data=new int[48];final org.joml.Vector3f position=new org.joml.Vector3f(),normal=new org.joml.Vector3f();final RandomSource random=RandomSource.create(0);}
 private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
 private NativeProgrammerMesh(){}
 public static void emit(NativeProgrammerBake bake,ProgrammerStatus status,Direction facing,int exampleMask,PoseStack.Pose pose,VertexConsumer target,double originX,double originY,double originZ,int light,int overlay,float opacity){var scratch=SCRATCH.get();var a=NativeProgrammerLayout.of(facing).transform();for(int part=0;part<PARTS.length;part++){if(BITS[part]!=0&&(exampleMask&BITS[part])==0)continue;boolean lit=part==3&&status.screenLit;var quads=bake.quads(NativeProgrammerAssets.part(status,PARTS[part]),null,scratch.random,ModelData.EMPTY,null);for(int q=0;q<quads.size();q++){var quad=quads.get(q);int[] source=quad.getVertices();int stride=source.length/4;if(source.length%4!=0||stride<8)throw new IllegalArgumentException("Programmer native vertex record");if(scratch.data.length<source.length)scratch.data=new int[source.length];ProgrammerMeshTransform.quad(source,stride,scratch.data,a,originX,originY,originZ,light,opacity,lit);for(int vertex=0;vertex<4;vertex++){int at=vertex*stride,n=source[at+7],color=scratch.data[at+3];float nx=(byte)n/127F,ny=(byte)(n>>>8)/127F,nz=(byte)(n>>>16)/127F;pose.pose().transformPosition(Float.intBitsToFloat(scratch.data[at]),Float.intBitsToFloat(scratch.data[at+1]),Float.intBitsToFloat(scratch.data[at+2]),scratch.position);pose.transformNormal((float)(a.xx*nx+a.xz*nz),ny,(float)(a.zx*nx+a.zz*nz),scratch.normal);target.addVertex(scratch.position.x,scratch.position.y,scratch.position.z).setColor(color&255,(color>>>8)&255,(color>>>16)&255,color>>>24).setUv(Float.intBitsToFloat(scratch.data[at+4]),Float.intBitsToFloat(scratch.data[at+5])).setOverlay(overlay).setLight(scratch.data[at+6]).setNormal(scratch.normal.x,scratch.normal.y,scratch.normal.z);}}}}
}
