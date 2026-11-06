package com.vandorlabs.propulsion;
/** Released OBJ face construction without world, client APIs or an assumed active consumer format. */
public final class PropulsionObjMesh {
 private PropulsionObjMesh(){}
 public static final class Face {
  public final String sprite;private final float[] position,normal,uv;
  public Face(String sprite,float[] position,float[] normal,float[] uv){if(sprite==null||position.length!=12||normal.length!=12||uv.length!=8)throw new IllegalArgumentException("Authored engine face");this.sprite=sprite;this.position=position.clone();this.normal=normal.clone();this.uv=uv.clone();}
  public float position(int vertex,int axis){return position[vertex*3+axis];}
  public float normal(int axis){return normal[axis];}
 }
 public interface Sprite {float minU();float maxU();float minV();float maxV();int width();int height();}
 public static int bake(Face face,int x,int y,Sprite sprite,int[] data){int stride=data.length/4;if(data.length%4!=0||stride<8)throw new IllegalArgumentException("Engine vertex record");float[] uv=new float[8];float averageU=0,averageV=0;
  for(int v=0;v<4;v++){int at=v*stride;for(int a=0;a<3;a++)data[at+a]=Float.floatToRawIntBits(PropulsionModelRotation.component(x,y,a,face.position[v*3],face.position[v*3+1],face.position[v*3+2],true));float nx=PropulsionModelRotation.normal(x,y,0,face.normal[v*3],face.normal[v*3+1],face.normal[v*3+2]),ny=PropulsionModelRotation.normal(x,y,1,face.normal[v*3],face.normal[v*3+1],face.normal[v*3+2]),nz=PropulsionModelRotation.normal(x,y,2,face.normal[v*3],face.normal[v*3+1],face.normal[v*3+2]),inverse=(float)(1D/Math.sqrt(nx*nx+ny*ny+nz*nz));nx*=inverse;ny*=inverse;nz*=inverse;data[at+7]=(data[at+7]&0xff000000)|(Math.round(nx*127F)&255)|((Math.round(ny*127F)&255)<<8)|((Math.round(nz*127F)&255)<<16);uv[v*2]=sprite.minU()+(sprite.maxU()-sprite.minU())*(face.uv[v*2]*16F)/16F;uv[v*2+1]=sprite.minV()+(sprite.maxV()-sprite.minV())*((1-face.uv[v*2+1])*16F)/16F;averageU+=uv[v*2]/4;averageV+=uv[v*2+1]/4;}
  float tx=sprite.width()/(sprite.maxU()-sprite.minU()),ty=sprite.height()/(sprite.maxV()-sprite.minV()),ep=1F/((tx>ty?tx:ty)*256);for(int v=0;v<4;v++){data[v*stride+4]=Float.floatToRawIntBits(contract(uv[v*2],averageU,ep));data[v*stride+5]=Float.floatToRawIntBits(contract(uv[v*2+1],averageV,ep));}return PropulsionModelRotation.objDirection(data,stride);
 }
 private static float contract(float original,float average,float ep){float next=original*(1-1F/256)+average*(1F/256),delta=original-next;if(Math.abs(delta)<ep)next=Math.abs(original-average)<2*ep?(original+average)/2:original+(delta<0?ep:-ep);return next;}
}
