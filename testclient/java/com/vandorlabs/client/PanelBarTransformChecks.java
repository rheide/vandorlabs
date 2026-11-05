package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableWall;
import java.lang.reflect.*;
import java.security.MessageDigest;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Actual float meshes frozen before sharing the T-owner transform/clipping kernel. */
public final class PanelBarTransformChecks {
    private static final String GOLDEN="79b3ef5bd840af14e1c28a709d8894e2c272225f86f90d6a50cf204807a7d858";private static MessageDigest digest;private static Field vertices;private static int cases,values;
    private static void put(int value){for(int shift=24;shift>=0;shift-=8)digest.update((byte)(value>>>shift));}
    private static void mesh(StaticSurfaceMesh mesh)throws Exception{float[] data=(float[])vertices.get(mesh);put(cases++);put(data.length);for(float value:data){put(Float.floatToRawIntBits(value));values++;}}
    private static TextureAtlasSprite sprite(String name,int x){TextureAtlasSprite s=new TextureAtlasSprite(name){};s.setIconWidth(16);s.setIconHeight(16);s.initSprite(32,16,x,0,false);return s;}
    public static void main(String[] ignored)throws Exception{
        net.minecraft.init.Bootstrap.register();digest=MessageDigest.getInstance("SHA-256");vertices=StaticSurfaceMesh.class.getDeclaredField("vertices");vertices.setAccessible(true);TextureAtlasSprite wall=sprite("bar_wall",0),metal=sprite("bar_metal",16);
        Constructor<BlockProgrammableWall.FlatCorner> constructor=BlockProgrammableWall.FlatCorner.class.getDeclaredConstructor(Double.class,Double.class,Boolean.class,Boolean.class,boolean.class,boolean.class);constructor.setAccessible(true);java.util.List<StaticSurfaceMesh> sources=new java.util.ArrayList<>();
        for(boolean left:new boolean[]{false,true}){StaticSurfaceMesh.Capture c=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.renderFlatWall(c,wall,metal,6,constructor.newInstance(6D,6D,false,true,left,!left));sources.add(c.finish());}
        for(int shape=0;shape<4;shape++)for(int[] group:new int[][]{{1,1,0,0},{2,3,1,1}}){PortholeHex.Slice slice=new PortholeHex(group[0],group[1],shape).slice(group[2],group[3]);StaticSurfaceMesh.Capture c=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.drawFlatBar(c,wall,metal,metal,slice);sources.add(c.finish());c=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.drawPortholeGlassGeometry(c,slice,null);sources.add(c.finish());}
        for(StaticSurfaceMesh source:sources)for(int turns=0;turns<4;turns++)for(double depth:new double[]{-6,0,6})for(double left:new double[]{0,6})for(double width:new double[]{.625,1}){
            StaticSurfaceMesh transformed=source.panelTransform(turns,depth,left,width);mesh(transformed);
            for(boolean outside:new boolean[]{false,true})for(double near:new double[]{0,6,12})mesh(transformed.box(near,-1,-1,near+4,17,17,outside));
            for(boolean right:new boolean[]{false,true})for(double cut:new double[]{2,6,10})for(double near:new double[]{0,6,12})mesh(transformed.panelEnd(cut,near,metal,right));
        }
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));if(!GOLDEN.isEmpty()&&!GOLDEN.equals(hash.toString()))throw new AssertionError("T mesh transform changed "+hash);System.out.println("Panel bar transform reference PASS cases="+cases+" floatValues="+values+" SHA256="+hash+" actual floatrecords/normalization/UVinterpolation/stemcaps");
    }
}
