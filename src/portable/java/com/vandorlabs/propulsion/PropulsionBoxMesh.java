package com.vandorlabs.propulsion;
/** Axis-box faces used by the released engine model graph; Source double UV inset precedes atlas interpolation. */
public final class PropulsionBoxMesh {
 private static final int[][] CORNERS={{1,0,4,5},{2,3,7,6},{6,4,0,2},{3,1,5,7},{2,0,1,3},{7,5,4,6}};
 private PropulsionBoxMesh(){}
 public static final class Face {
  public final String sprite;public final int direction,cull,tint;public final boolean shade;private final float[] from,to,uv;private final int rotation;
  public Face(String sprite,float[] from,float[] to,int direction,float[] uv,int rotation,int cull,int tint,boolean shade){if(sprite==null||from.length!=3||to.length!=3||uv.length!=4||direction<0||direction>5||rotation%90!=0)throw new IllegalArgumentException("Authored engine box face");this.sprite=sprite;this.from=from.clone();this.to=to.clone();this.direction=direction;this.uv=uv.clone();this.rotation=rotation;this.cull=cull;this.tint=tint;this.shade=shade;}
  public float position(int vertex,int axis){int corner=CORNERS[direction][vertex];return ((corner&(axis==0?4:axis==1?2:1))==0?from:to)[axis]/16F;}
  private float u(int vertex){int at=(vertex+rotation/90)%4;return at==0||at==1?uv[0]:uv[2];}private float v(int vertex){int at=(vertex+rotation/90)%4;return at==0||at==3?uv[1]:uv[3];}
 }
 public static int bake(Face face,int x,int y,PropulsionObjMesh.Sprite sprite,int[] data){int stride=data.length/4;if(data.length%4!=0||stride<8)throw new IllegalArgumentException("Engine vertex record");for(int v=0;v<4;v++){int corner=CORNERS[face.direction][v],at=v*stride;data[at]=Float.floatToRawIntBits(((corner&4)==0?face.from:face.to)[0]/16F);data[at+1]=Float.floatToRawIntBits(((corner&2)==0?face.from:face.to)[1]/16F);data[at+2]=Float.floatToRawIntBits(((corner&1)==0?face.from:face.to)[2]/16F);float u=(float)(face.u(v)*.999D+face.u((v+2)%4)*.001D),w=(float)(face.v(v)*.999D+face.v((v+2)%4)*.001D);data[at+4]=Float.floatToRawIntBits(sprite.minU()+(sprite.maxU()-sprite.minU())*u/16F);data[at+5]=Float.floatToRawIntBits(sprite.minV()+(sprite.maxV()-sprite.minV())*w/16F);}return PropulsionModelRotation.quad(data,x,y,true);}
}
