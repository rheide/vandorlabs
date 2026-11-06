package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.render.ScreenMeshEmission;
import com.vandorlabs.segmented.SegmentedPanelMesh;
import java.util.Arrays;

/** Packed immutable owner-relative records; no graphics format, host or world is retained. */
public final class NativeScreenMesh {
 public interface Emitter {void emit(ScreenMeshEmission.Sink sink);}
 private static final int STRIDE=8,MAX_VERTICES=4096;
 private final float[] data;
 private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
 private static final class Scratch {final org.joml.Vector3f position=new org.joml.Vector3f(),normal=new org.joml.Vector3f();}
 private NativeScreenMesh(float[] data){this.data=data;}
 public int vertices(){return data.length/STRIDE;}public long retainedBytes(){return 48L+data.length*4L;}
 public static NativeScreenMesh build(int mode,double[] matrix,boolean halfDiagonal,SegmentedPanelMesh.Texture texture,Emitter emitter){
  if(mode!=4&&mode!=7||matrix==null||matrix.length<16)throw new IllegalArgumentException("Screen primitive transform");for(int i=0;i<16;i++)if(!Double.isFinite(matrix[i]))throw new IllegalArgumentException("Non-finite Screen transform");Builder builder=new Builder(matrix,halfDiagonal,texture);emitter.emit(builder);int width=mode==4?3:4;if(builder.vertices%width!=0)throw new IllegalArgumentException("Incomplete Screen primitive");
  if(mode==7)return new NativeScreenMesh(Arrays.copyOf(builder.values,builder.vertices*STRIDE));
  float[] quads=new float[builder.vertices/3*4*STRIDE];for(int triangle=0;triangle<builder.vertices/3;triangle++){int source=triangle*3*STRIDE,target=triangle*4*STRIDE;System.arraycopy(builder.values,source,quads,target,3*STRIDE);System.arraycopy(builder.values,source+2*STRIDE,quads,target+3*STRIDE,STRIDE);}return new NativeScreenMesh(quads);
 }
 private static final class Builder implements ScreenMeshEmission.Sink {
  final double[] matrix;final boolean half;final SegmentedPanelMesh.Texture texture;float[] values=new float[32*STRIDE];int vertices;
  Builder(double[] matrix,boolean half,SegmentedPanelMesh.Texture texture){this.matrix=matrix;this.half=half;this.texture=java.util.Objects.requireNonNull(texture);}
  public void vertex(double x,double y,double z,double u,double v,float nx,float ny,float nz){if(vertices>=MAX_VERTICES)throw new IllegalStateException("Screen mesh vertex budget exceeded");if((vertices+1)*STRIDE>values.length)values=Arrays.copyOf(values,Math.min(MAX_VERTICES*STRIDE,values.length*2));int at=vertices++*STRIDE;values[at]=(float)(matrix[0]*x+matrix[4]*y+matrix[8]*z+matrix[12]);values[at+1]=(float)(matrix[1]*x+matrix[5]*y+matrix[9]*z+matrix[13]);values[at+2]=(float)(matrix[2]*x+matrix[6]*y+matrix[10]*z+matrix[14]);values[at+3]=texture.u(u);values[at+4]=texture.v(v);double ys=half?32:16,zs=half?32:16;values[at+5]=(float)(matrix[0]*16*nx+matrix[4]*ys*ny+matrix[8]*zs*nz);values[at+6]=(float)(matrix[1]*16*nx+matrix[5]*ys*ny+matrix[9]*zs*nz);values[at+7]=(float)(matrix[2]*16*nx+matrix[6]*ys*ny+matrix[10]*zs*nz);}
 }
 /** Animated records contain normalized V and select their authored strip section only at emission. */
 public void emit(PoseStack.Pose pose,VertexConsumer target,int light,int overlay,double top,double span){var scratch=SCRATCH.get();for(int at=0;at<data.length;at+=STRIDE){pose.pose().transformPosition(data[at],data[at+1],data[at+2],scratch.position);pose.transformNormal(data[at+5],data[at+6],data[at+7],scratch.normal);target.addVertex(scratch.position.x,scratch.position.y,scratch.position.z).setColor(255,255,255,255).setUv(data[at+3],(float)(top+span*data[at+4])).setOverlay(overlay).setLight(light).setNormal(scratch.normal.x,scratch.normal.y,scratch.normal.z);}}
}
