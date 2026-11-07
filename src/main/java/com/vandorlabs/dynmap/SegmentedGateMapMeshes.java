package com.vandorlabs.dynmap;

import com.google.gson.*;
import com.vandorlabs.segmented.*;
import java.util.*;
import org.dynmap.renderer.*;

/** Fixed canonical model/material records; no client model or atlas API. */
final class SegmentedGateMapMeshes {
 static final String[] MATERIALS={"amber","black","dark","lattice","red","white","zinc"};
 private static final int[][] CORNERS={{1,0,4,5},{2,3,7,6},{6,4,0,2},{3,1,5,7},{2,0,1,3},{7,5,4,6}};
 private static final String[] FACES={"down","up","north","south","west","east"};
 private static final Face[][][] BASE=new Face[3][4][];
 private static final Face[][][][][] CONNECTORS=new Face[3][4][4][2][];
 private static int bytes=2048;
 private static final class Face {final int texture,element;final float[] data;Face(int texture,int element,float[] data){this.texture=texture;this.element=element;this.data=data;bytes+=48+16+data.length*4;}}
 static {
  for(int kind=0;kind<3;kind++)for(int part=0;part<4;part++)BASE[kind][part]=read(kind,part);
  for(int kind=0;kind<3;kind++)for(int part=0;part<4;part++)for(int peer=0;peer<4;peer++)for(int side=0;side<2;side++){
   List<Face> result=new ArrayList<>();int mask=mask(part)|(side==0?4:8);
   for(SegmentedGateShape.Connector connector:SegmentedGateModels.connectors(kind,mask,mask(peer),side==1))for(Face face:BASE[kind][part])if(face.element==connector.element){
    final float[] pending=new float[32];final int[] count={0};
    SegmentedGateConnectorUV.emit(face.data,connector.bounds,(x,y,z,u,v,nx,ny,nz)->{int at=count[0]*8;pending[at]=(float)x;pending[at+1]=(float)y;pending[at+2]=(float)z;pending[at+3]=(float)u;pending[at+4]=(float)v;pending[at+5]=(float)nx;pending[at+6]=(float)ny;pending[at+7]=(float)nz;if(++count[0]==4){result.add(new Face(face.texture,face.element,pending.clone()));count[0]=0;}});
   }
   CONNECTORS[kind][part][peer][side]=result.toArray(new Face[0]);bytes+=16+result.size()*8;
  }
  if(bytes>4*1024*1024)throw new IllegalStateException("Gate map mesh byte limit");
 }
 private static int mask(int part){return part==0?0:part==1?2:part==2?3:1;}
 private static int part(int mask){switch(mask&3){case 1:return 3;case 2:return 1;case 3:return 2;default:return 0;}}
 private static Face[] read(int kind,int part){
  JsonObject root=SegmentedGateModels.document(kind,mask(part)),textures=root.getAsJsonObject("textures");JsonArray elements=root.getAsJsonArray("elements");SegmentedGateShape.Model model=SegmentedGateModels.model(kind,mask(part));List<Face> result=new ArrayList<>();double[] point=new double[3],normal=new double[3];
  for(int element=0;element<elements.size();element++){JsonObject faces=elements.get(element).getAsJsonObject().getAsJsonObject("faces");for(int side=0;side<6;side++)if(faces.has(FACES[side])){
   JsonObject face=faces.getAsJsonObject(FACES[side]);if(face.has("rotation")&&face.get("rotation").getAsInt()!=0)throw new IllegalArgumentException("Gate map UV rotation");JsonArray uv=face.getAsJsonArray("uv");String texture=face.get("texture").getAsString();int depth=0;while(texture.startsWith("#")){if(++depth>16)throw new IllegalArgumentException("Gate map texture reference");texture=textures.get(texture.substring(1)).getAsString();}
   int material=-1;for(int i=0;i<MATERIALS.length;i++)if(texture.equals("vandorlabs:blocks/segmented_gates/"+MATERIALS[i]))material=i;if(material<0)throw new IllegalArgumentException("Unknown gate map material");
   float[] data=new float[32];model.element(element).normal(side,normal,0);for(int vertex=0;vertex<4;vertex++){model.element(element).corner(CORNERS[side][vertex],point,0);int at=vertex*8;for(int axis=0;axis<3;axis++){data[at+axis]=(float)point[axis];data[at+5+axis]=(float)normal[axis];}data[at+3]=uv.get(vertex<2?0:2).getAsFloat()/16;data[at+4]=uv.get(vertex==0||vertex==3?1:3).getAsFloat()/16;}
   result.add(new Face(material,element,data));
  }}bytes+=16+result.size()*8;return result.toArray(new Face[0]);
 }
 static int retainedBytes(){return bytes;}
 static void emit(MapDataContext context,List<RenderPatch> out,int kind,int mask,int below,int above,int row,double[] pose,int rotation,int textureBase){
  int part=part(mask);emit(context,out,BASE[kind][part],row,pose,rotation,textureBase);if((mask&4)!=0)emit(context,out,CONNECTORS[kind][part][part(below)][0],row,pose,rotation,textureBase);if((mask&8)!=0)emit(context,out,CONNECTORS[kind][part][part(above)][1],row,pose,rotation,textureBase);
 }
 private static void emit(MapDataContext context,List<RenderPatch> out,Face[] faces,int row,double[] pose,int rotation,int textureBase){for(Face face:faces){List<MapSurface.Vertex> polygon=new ArrayList<>(4);for(int vertex=0;vertex<4;vertex++){int at=vertex*8;polygon.add(new MapSurface.Vertex(SegmentedDoorMapRenderer.point(face.data[at],face.data[at+1],face.data[at+2]-.5,row,pose,rotation),face.data[at+3],face.data[at+4]));}MapSurface.emitUnclipped(context,out,polygon,textureBase+face.texture);}}
}
