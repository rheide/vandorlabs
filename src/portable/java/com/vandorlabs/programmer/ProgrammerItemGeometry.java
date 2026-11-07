package com.vandorlabs.programmer;
/** Released complete-OBJ normalization and GUI fit. Float operation order is saved artwork policy. */
public final class ProgrammerItemGeometry {
 public static void normalize(int[] data,int stride){if(stride<3||data.length!=stride*4)throw new IllegalArgumentException("Programmer quad stride");for(int i=0;i<4;i++){int at=i*stride;data[at]=Float.floatToRawIntBits((Float.intBitsToFloat(data[at])-1)*.5F+.5F);data[at+1]=Float.floatToRawIntBits(Float.intBitsToFloat(data[at+1])*.5F);data[at+2]=Float.floatToRawIntBits((Float.intBitsToFloat(data[at+2])-.5F)*.5F+.5F);}}
 public static final class Bounds {
  private double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
  public void include(int[] data,int stride){for(int i=0;i<4;i++){int at=i*stride;double x=Float.intBitsToFloat(data[at])-.5,y=Float.intBitsToFloat(data[at+1])-.5,z=Float.intBitsToFloat(data[at+2])-.5;double ry=Math.toRadians(225),rx=Math.toRadians(25);double px=x*Math.cos(ry)+z*Math.sin(ry),pz=z*Math.cos(ry)-x*Math.sin(ry),py=y*Math.cos(rx)-pz*Math.sin(rx);minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);}}
  /** rotation XYZ, translation XYZ, scale XYZ, in the released transform order. */
  public float[] gui(){if(!Double.isFinite(minX))return new float[]{25,225,0,0,0,0,1,1,1};float scale=(float)(.76/Math.max(maxX-minX,maxY-minY));return new float[]{25,225,0,(float)(-(minX+maxX)*scale/2),(float)(-(minY+maxY)*scale/2),0,scale,scale,scale};}
 }
 private ProgrammerItemGeometry(){}
}
