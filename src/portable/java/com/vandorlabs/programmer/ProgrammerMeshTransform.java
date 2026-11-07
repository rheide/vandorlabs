package com.vandorlabs.programmer;
/** Released casing submission on caller-owned records; extra component payload is preserved. */
public final class ProgrammerMeshTransform {
 private ProgrammerMeshTransform(){}
 public static void quad(int[] source,int stride,int[] target,ProgrammerTransform.Affine a,double x,double y,double z,int light,float alpha,boolean fullbright){
  if(stride<7||source.length!=stride*4||target.length<source.length)throw new IllegalArgumentException("Programmer vertex record");
  System.arraycopy(source,0,target,0,source.length);
  for(int vertex=0;vertex<4;vertex++){int at=vertex*stride;double vx=Float.intBitsToFloat(source[at]),vy=Float.intBitsToFloat(source[at+1]),vz=Float.intBitsToFloat(source[at+2]);target[at]=Float.floatToRawIntBits((float)(a.xx*vx+a.xz*vz+x+a.offsetX));target[at+1]=Float.floatToRawIntBits((float)(vy+y));target[at+2]=Float.floatToRawIntBits((float)(a.zx*vx+a.zz*vz+z+a.offsetZ));int color=source[at+3],opacity=(int)Math.round((color>>>24)*ProgrammerTransform.clamp01(alpha));target[at+3]=(fullbright?0xFFFFFF:color&0xFFFFFF)|(opacity<<24);target[at+6]=fullbright?0x00F000F0:light;}
 }
}
