package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.render.DiagonalPortholeProjectionCases;
import java.security.MessageDigest;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Actual legacy output, including atlas interpolation and float/packed normal inputs. */
public final class DiagonalPortholeProjectionLegacyChecks {
    private static final String FULL="976f3815066b953966d70feae9d91420add49b26c2e7c09b2012df32d76a77b3",RAW="33e5559edac3c9c0b439d1ae7433fc03a3d543060db7b04b892c40711b629f25";
    private static java.lang.reflect.Constructor<BlockProgrammableWall.Corner> constructor;
    private static BlockProgrammableWall.Corner corner(com.vandorlabs.render.PanelCornerGeometry.Corner source){try{BlockProgrammableWall.Corner result=constructor.newInstance(source.frontRight,source.backRight,source.straightLeft,source.straightRight);result.frontFlat=source.frontFlat;result.backFlat=source.backFlat;return result;}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    private static MessageDigest full,raw;private static int cases,vertices,rawVertices;
    private static void number(MessageDigest digest,long value){for(int shift=56;shift>=0;shift-=8)digest.update((byte)(value>>>(shift)));}
    private static void integer(MessageDigest digest,int value){for(int shift=24;shift>=0;shift-=8)digest.update((byte)(value>>>(shift)));}
    private static final class Capture extends BufferBuilder {
        private final boolean rawCase;private double x,y,z,u,v;private float nx,ny,nz;
        Capture(boolean rawCase){super(16);this.rawCase=rawCase;}
        @Override public BufferBuilder pos(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
        @Override public BufferBuilder tex(double u,double v){this.u=u;this.v=v;return this;}
        @Override public BufferBuilder color(int r,int g,int b,int a){return this;}
        @Override public BufferBuilder lightmap(int sky,int block){return this;}
        @Override public BufferBuilder normal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
        private void record(MessageDigest digest){number(digest,Double.doubleToRawLongBits(x));number(digest,Double.doubleToRawLongBits(y));number(digest,Double.doubleToRawLongBits(z));number(digest,Double.doubleToRawLongBits(u));number(digest,Double.doubleToRawLongBits(v));integer(digest,Float.floatToRawIntBits(nx));integer(digest,Float.floatToRawIntBits(ny));integer(digest,Float.floatToRawIntBits(nz));integer(digest,((byte)(int)(nx*127)&255)|(((byte)(int)(ny*127)&255)<<8)|(((byte)(int)(nz*127)&255)<<16));}
        @Override public void endVertex(){record(full);vertices++;if(rawCase){record(raw);rawVertices++;}}
    }
    private static String hash(MessageDigest digest){StringBuilder text=new StringBuilder();for(byte value:digest.digest())text.append(String.format("%02x",value&255));return text.toString();}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();constructor=BlockProgrammableWall.Corner.class.getDeclaredConstructor(Boolean.class,Boolean.class,boolean.class,boolean.class);constructor.setAccessible(true);full=MessageDigest.getInstance("SHA-256");raw=MessageDigest.getInstance("SHA-256");final TextureAtlasSprite sprite=new TextureAtlasSprite("projection_golden"){};sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(64,64,16,32,false);
        DiagonalPortholeProjectionCases.visit(c->{integer(full,c.id);if(c.raw)integer(raw,c.id);BlockProgrammableWall.Corner corner=null;if(c.corner!=null){corner=corner(c.corner);}DiagonalPortholeMesh mesh=c.mode<0?null:new DiagonalPortholeMesh(c.mode,c.inverted,c.lower,c.upper,c.bounds,corner);DiagonalPortholeMesh.quad(new Capture(c.raw),c.raw?null:sprite,c.textured,mesh,c.reverse,c.vertices);cases++;});
        String all=hash(full),unmapped=hash(raw);if(!FULL.isEmpty()&&!FULL.equals(all)||!RAW.isEmpty()&&!RAW.equals(unmapped))throw new AssertionError("Frozen projection changed full="+all+" raw="+unmapped);System.out.println("Actual diagonal porthole projection PASS cases="+cases+" vertices="+vertices+" rawVertices="+rawVertices+" full="+all+" raw="+unmapped);
    }
}
