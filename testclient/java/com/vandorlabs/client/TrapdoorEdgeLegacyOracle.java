package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.render.*;
import java.io.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
/** Actual released native metal edge faces, including slope thickness UVs. */
public final class TrapdoorEdgeLegacyOracle {
 public static void main(String[] args)throws Exception{
  TextureAtlasSprite sprite=new TextureAtlasSprite("oracle/edge"){};sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(256,256,48,80,false);File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0,vertices=0;
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){for(int shape=0;shape<4;shape++)for(int turn=0;turn<4;turn++)for(boolean inverted:new boolean[]{false,true})for(boolean sliding:new boolean[]{false,true})for(double pose:new double[]{0,.17,.5,.91,1}){
   boolean diagonal=shape!=0,tall=shape==1||shape==2;int first=tall?2:0;double[][] corners=diagonal?DiagonalTrapdoorGeometry.corners(shape-1,inverted,turn,sliding,inverted,pose):TrapdoorGeometry.corners(1,sliding,turn,pose),uv=new double[8][3];for(int i=0;i<8;i++){uv[i][0]=(i&1)==0?0:1;uv[i][1]=(i&2)==0?0:tall?1:diagonal?2/16D:TrapdoorGeometry.THICKNESS;uv[i][2]=(i&4)==0?0:tall?2/16D:1;}
   BufferBuilder b=new BufferBuilder(4096);b.begin(7,BlockSurfaceFormat.get());if(args.length>1)TrapdoorRigidEdges.emit(corners,diagonal,tall,(x,y,z,u,v,half,nx,ny,nz)->b.pos(x,y,z).color(255,255,255,255).tex(sprite.getInterpolatedU(u*16),sprite.getInterpolatedV(v*16)).lightmap(0xD0,0x70).normal(nx,ny,nz).endVertex());else for(int face=0;face<6;face++)if(face<first||face>=first+2)TEProgrammableTrapdoor.drawMesh(b,sprite,corners,uv,0xD00070,face,face+1);b.finishDrawing();JsonObject row=new JsonObject();row.addProperty("shape",shape);row.addProperty("turn",turn);row.addProperty("inverted",inverted);row.addProperty("sliding",sliding);row.addProperty("pose",pose);JsonArray data=new JsonArray();for(int i=0;i<b.getVertexCount()*32;i+=4)data.add(b.getByteBuffer().getInt(i));row.add("vertices",data);out.println(gson.toJson(row));profiles++;vertices+=b.getVertexCount();
  }}SegmentedReferenceGolden.check(file,"1f9a043c9951aad28d1ac1a4661344561028374612c311199c569cb541bd2ebf");System.out.println("Trapdoor actual metal edges PASS profiles="+profiles+" vertices="+vertices+" all diagonal modes/pose/normal/thickness UV");
 }
}
