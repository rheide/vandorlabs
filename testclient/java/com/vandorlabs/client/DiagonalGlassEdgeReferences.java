package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockProgrammableWall;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Optional actual directional-rail atlas records supplementing the frozen geometry baseline. */
final class DiagonalGlassEdgeReferences {
    private static TextureAtlasSprite sprite(String id,int x){return sprite(id,x,16,16);}
    private static TextureAtlasSprite sprite(String id,int x,int w,int h){TextureAtlasSprite s=new TextureAtlasSprite(id){};s.setIconWidth(w);s.setIconHeight(h);s.initSprite(1024,128,x,0,false);return s;}
    static void write(String path)throws Exception{
        Field registry=FrameEdgeTextures.class.getDeclaredField("EDGES");registry.setAccessible(true);Map<String,Object> registered=(Map<String,Object>)registry.get(null);Class<?> edgeClass=Class.forName("com.vandorlabs.client.FrameEdgeTextures$Edge");Constructor<?> edgeCtor=edgeClass.getDeclaredConstructor(String.class,String.class,int.class,double.class);edgeCtor.setAccessible(true);TextureAtlasSprite[][] edges=new TextureAtlasSprite[3][4];String[] names={"glass_reference_rail","glass_reference_inner","glass_reference_outer"};
        Map<String,Object> old=new HashMap<>();for(int role=0;role<3;role++){Object array=Array.newInstance(edgeClass,4);for(int side=0;side<4;side++){TextureAtlasSprite edge=(TextureAtlasSprite)edgeCtor.newInstance(names[role]+(side==0?"":"_edge_"+side),"reference",side,1D);edge.setIconWidth(64);edge.setIconHeight(64);edge.initSprite(1024,128,128+role*256+side*64,64,false);edges[role][side]=edge;Array.set(array,side,edge);}old.put(names[role],registered.put(names[role],array));}
        TextureAtlasSprite wide=sprite("glass_edge_wide",0,32,16),portrait=sprite("glass_edge_portrait",0,16,32),plainInner=sprite("glass_edge_inner_plain",32),plainOuter=sprite("glass_edge_outer_plain",64),originalFront=sprite("vandorlabs:blocks/frames/full_original",96);Field vertices=StaticSurfaceMesh.class.getDeclaredField("vertices");vertices.setAccessible(true);Constructor<BlockProgrammableWall.Corner> cornerCtor=BlockProgrammableWall.Corner.class.getDeclaredConstructor(Boolean.class,Boolean.class,boolean.class,boolean.class);cornerCtor.setAccessible(true);Gson gson=new Gson();int records=0;
        try(PrintWriter writer=new PrintWriter(path,"UTF-8")){
            for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int corner=0;corner<2;corner++)for(int mask:new int[]{0,21})for(int finishes=0;finishes<4;finishes++){
                TextureAtlasSprite pane=corner==0?wide:portrait;double lower=corner==0?Double.NaN:.25,upper=corner==0?Double.NaN:.75;StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.renderDiagonalWall(capture,pane,pane,inverted,cornerCtor.newInstance(true,corner==0?false:true,true,corner!=0),mode==1?16:8,3,mode==2&&inverted?-8:0,mode==2&&inverted?8:16,mode==2?(inverted?8:0):Double.NaN,lower*16,upper*16,false,false,null);StaticSurfaceMesh mesh=capture.finish();if((mask&1)!=0)mesh=mesh.withoutPlane(0,0);if((mask&4)!=0)mesh=mesh.withoutPlane(2,0);
                TextureAtlasSprite rail=finishes==3?originalFront:finishes==2?plainOuter:edges[0][0],inner=finishes==1?plainInner:edges[1][0],outer=finishes==1?plainOuter:edges[2][0];StaticSurfaceMesh frame=mesh.outlineFrame(rail,pane,inner,outer,(mask&1)!=0,false,(mask&4)!=0,false,(mask&16)!=0,false,mode==2?2:1);JsonObject row=new JsonObject();row.addProperty("mode",mode);row.addProperty("inverted",inverted);row.addProperty("corner",corner);row.addProperty("mask",mask);row.addProperty("finishes",finishes);row.add("frame",gson.toJsonTree(vertices.get(frame)));writer.println(gson.toJson(row));records++;
            }
        }finally{for(String name:names){Object previous=old.get(name);if(previous==null)registered.remove(name);else registered.put(name,previous);}}
        if(records!=96)throw new AssertionError(records);System.out.println("Diagonal glass directional edge actual PASS records="+records);
    }
}
