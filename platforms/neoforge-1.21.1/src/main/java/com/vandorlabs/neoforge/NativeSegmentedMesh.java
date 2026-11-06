package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.segmented.SegmentedPanelTransform;

/** Immutable atlas panel mesh; motion transforms stream through the active consumer format. */
public final class NativeSegmentedMesh {
 private static final int MAX_BYTES=1024*1024;
 private static final ThreadLocal<double[]> VERTEX=ThreadLocal.withInitial(()->new double[6]);
 private final float[] source;
 public NativeSegmentedMesh(float[] source){if(source.length%32!=0||source.length*4>MAX_BYTES)throw new IllegalArgumentException("Segmented panel mesh budget/stride");this.source=source.clone();}
 public static final class Builder implements com.vandorlabs.segmented.SegmentedPanelMesh.Sink {
  private float[] data=new float[256];private int size;
  public void vertex(double x,double y,double z,float u,float v,float nx,float ny,float nz){if(size+8>MAX_BYTES/4)throw new IllegalArgumentException("Segmented panel mesh byte budget");if(size+8>data.length)data=java.util.Arrays.copyOf(data,Math.min(MAX_BYTES/4,data.length*2));data[size++]=(float)x;data[size++]=(float)y;data[size++]=(float)z;data[size++]=u;data[size++]=v;data[size++]=nx;data[size++]=ny;data[size++]=nz;}
  public NativeSegmentedMesh finish(){return new NativeSegmentedMesh(java.util.Arrays.copyOf(data,size));}
  float[] records(){return java.util.Arrays.copyOf(data,size);}
 }
 public int vertices(){return source.length/8;}
 public int retainedBytes(){return 64+source.length*4;}
 public void emitFixed(PoseStack.Pose matrix,VertexConsumer target,int light,int overlay,int alpha){
  for(int at=0;at<source.length;at+=8)target.addVertex(matrix,source[at]/16,source[at+1]/16,source[at+2]/16).setColor(255,255,255,alpha).setUv(source[at+3],source[at+4]).setOverlay(overlay).setLight(light).setNormal(matrix,source[at+5],source[at+6],source[at+7]);
 }
 public void emit(PoseStack.Pose matrix,VertexConsumer target,double[] pose,int rotation,int light,int overlay,int alpha){
  double[] vertex=VERTEX.get();for(int at=0;at<source.length;at+=8){SegmentedPanelTransform.vertex(source,at,pose,rotation,0,0,0,vertex);
   target.addVertex(matrix,(float)vertex[0],(float)vertex[1],(float)vertex[2]).setColor(255,255,255,alpha).setUv(source[at+3],source[at+4]).setOverlay(overlay).setLight(light).setNormal(matrix,(float)vertex[3],(float)vertex[4],(float)vertex[5]);
  }
 }
}
