package com.vandorlabs.propulsion;

/** Exact unrolled released plume billboard; no temporary vectors or fixed native consumer format. */
public strictfp final class PropulsionBillboard {
 private PropulsionBillboard(){}
 public interface Sink {void vertex(float x,float y,float z,float u,float v,int abgr,int light);}
 public static void emit(PropulsionParticle.State state,float partial,double cameraX,double cameraY,double cameraZ,float rx,float rz,float ryz,float rxy,float rxz,Sink sink){
  float u=(state.sprite%16)/16F,u1=u+.0624375F,v=(state.sprite/16)/16F,v1=v+.0624375F,size=.1F*state.scale;
  float x=(float)(state.previousX+(state.x-state.previousX)*(double)partial-cameraX),y=(float)(state.previousY+(state.y-state.previousY)*(double)partial-cameraY),z=(float)(state.previousZ+(state.z-state.previousZ)*(double)partial-cameraZ);
  int color=((int)(state.red*255F))|((int)(state.green*255F)<<8)|((int)(state.blue*255F)<<16)|0xff000000;
  vertex(sink,x,y,z,-rx*size-rxy*size,-rz*size,-ryz*size-rxz*size,u1,v1,color);
  vertex(sink,x,y,z,-rx*size+rxy*size,rz*size,-ryz*size+rxz*size,u1,v,color);
  vertex(sink,x,y,z,rx*size+rxy*size,rz*size,ryz*size+rxz*size,u,v,color);
  vertex(sink,x,y,z,rx*size-rxy*size,-rz*size,ryz*size-rxz*size,u,v1,color);
 }
 private static void vertex(Sink sink,float x,float y,float z,float dx,float dy,float dz,float u,float v,int color){sink.vertex((float)((double)x+(dx==0F?0D:(double)dx)),(float)((double)y+(dy==0F?0D:(double)dy)),(float)((double)z+(dz==0F?0D:(double)dz)),u,v,color,PropulsionParticle.FULL_BRIGHT);}
 public static float localU(int sprite,float sourceU){return (sourceU-(sprite%16)/16F)*16F;}
 public static float localV(int sprite,float sourceV){return (sourceV-(sprite/16)/16F)*16F;}
}
