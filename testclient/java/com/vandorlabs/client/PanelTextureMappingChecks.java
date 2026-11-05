package com.vandorlabs.client;

import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Real pre-extraction atlas/group mapping and directional rail decisions. */
public final class PanelTextureMappingChecks {
    private static final String GOLDEN="54e15325e6e074ee84680431a91e87408bc31c529b05f611dca7984297a6b357";
    private static MessageDigest digest;private static int mappings,edges;
    private static void put(float value){int bits=Float.floatToRawIntBits(value);for(int shift=24;shift>=0;shift-=8)digest.update((byte)(bits>>>shift));}
    private static void put(double value){long bits=Double.doubleToRawLongBits(value);for(int shift=56;shift>=0;shift-=8)digest.update((byte)(bits>>>shift));}
    private static TextureAtlasSprite sprite(String name,int width,int height){TextureAtlasSprite sprite=new TextureAtlasSprite(name){};sprite.setIconWidth(width);sprite.setIconHeight(height);sprite.initSprite(256,256,64,64,false);return sprite;}
    private static void edgeCases()throws Exception{
        Field registry=FrameEdgeTextures.class.getDeclaredField("EDGES");registry.setAccessible(true);@SuppressWarnings("unchecked") Map<String,Object> map=(Map<String,Object>)registry.get(null);
        Class<?> type=Class.forName("com.vandorlabs.client.FrameEdgeTextures$Edge");Constructor<?> constructor=type.getDeclaredConstructor(String.class,String.class,int.class,double.class);constructor.setAccessible(true);Object choices=Array.newInstance(type,4);for(int i=0;i<4;i++)Array.set(choices,i,constructor.newInstance("reference_rail"+(i==0?"":"_edge_"+i),"reference_source",i,1D));map.put("reference_rail",choices);
        TextureAtlasSprite rail=(TextureAtlasSprite)Array.get(choices,0);
        try{
            for(double x0:new double[]{0,4,8,12})for(double y0:new double[]{0,4,8,12})for(double width:new double[]{0,2,4,8})for(double height:new double[]{2,4,8})for(int uv=0;uv<4;uv++){
                double[][] p={{x0,y0,6,0,0},{x0+width,y0,6,16,0},{x0+width,y0+height,10,16,16},{x0,y0+height,10,0,16}};
                for(double[] point:p){double u=point[3],v=point[4];if(uv==1){point[3]=v;point[4]=u;}else if(uv==2){point[3]=16-u;point[4]=v;}else if(uv==3){point[3]=4;point[4]=4;}}
                TextureAtlasSprite selected=FrameEdgeTextures.map(rail,p);String id=selected.getIconName();digest.update(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));for(double[] point:p)for(double value:point)put(value);edges++;
            }
        }finally{map.remove("reference_rail");}
    }
    public static void main(String[] ignored)throws Exception{
        net.minecraft.init.Bootstrap.register();digest=MessageDigest.getInstance("SHA-256");Field data=StaticSurfaceMesh.class.getDeclaredField("vertices");data.setAccessible(true);
        int[][] groups={{0,0,1,1},{1,0,2,1},{0,1,1,2},{1,2,3,3},{2,0,3,2},{0,2,2,3}};
        for(int imageWidth:new int[]{16,32})for(int imageHeight:new int[]{16,32})for(int width:new int[]{8,16,48})for(int height:new int[]{8,16,48})for(boolean raw:new boolean[]{false,true}){
            TextureAtlasSprite sprite=sprite("group_mapping_reference",imageWidth,imageHeight);StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();double end=raw?1:16;
            WorldSurface.quad(capture,raw?null:sprite,false,new double[]{0,0,0,0,end},new double[]{width,0,0,end,end},new double[]{width,height,0,end,0},new double[]{0,height,0,0,0});StaticSurfaceMesh source=capture.finish();
            for(int[] group:groups)for(boolean flipU:new boolean[]{false,true})for(boolean flipV:new boolean[]{false,true})for(double border:new double[]{0,.0625})for(int mode=0;mode<3;mode++){
                GlassSurfaceGroups.Layout layout=new GlassSurfaceGroups.Layout(group[0],group[1],group[2],group[3],flipU,flipV);StaticSurfaceMesh result=GlassSurfaceMapping.map(source,sprite,layout,raw,border,border/2,mode);float[] vertices=(float[])data.get(result);put((float)vertices.length);for(float value:vertices)put(value);mappings++;
            }
        }
        edgeCases();TextureLayoutMeshChecks.run();StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));if(!GOLDEN.isEmpty()&&!GOLDEN.equals(hash.toString()))throw new AssertionError("Current panel mapping changed "+hash);System.out.println("Panel texture mapping reference PASS mappings="+mappings+" edgeCases="+edges+" SHA256="+hash+" actual raw atlas/group/repeat/fit/flip/border/rail outputs");
    }
}
