package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableWall;
import java.lang.reflect.Constructor;
import java.security.MessageDigest;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Frozen real legacy emitter calls, including atlas UVs, winding, clipping and packed normals. */
public final class PanelMeshEmitterChecks {
    private static final String GOLDEN="ff0000a21533d100969c1e57e1f3fddd7605734d1f527f22f2485287aa2f6f24";
    private static MessageDigest digest;
    private static int cases,vertices;
    private static final class Capture extends BufferBuilder {
        private final int turns;private double x,y,z,u,v;private int normal;
        Capture(int turns){super(16);this.turns=turns;}
        @Override public BufferBuilder pos(double x,double y,double z){double a=x/16,c=z/16;this.x=turns==1?c:turns==2?1-a:turns==3?1-c:a;this.y=y/16;this.z=turns==1?1-a:turns==2?1-c:turns==3?a:c;return this;}
        @Override public BufferBuilder tex(double u,double v){this.u=u;this.v=v;return this;}
        @Override public BufferBuilder color(int r,int g,int b,int a){return this;}
        @Override public BufferBuilder lightmap(int sky,int block){return this;}
        @Override public BufferBuilder normal(float x,float y,float z){int a=(byte)(int)(x*127),b=(byte)(int)(y*127),c=(byte)(int)(z*127);int nx=turns==1?c:turns==2?-a:turns==3?-c:a,nz=turns==1?-a:turns==2?-c:turns==3?a:c;normal=(nx&255)|((b&255)<<8)|((nz&255)<<16);return this;}
        @Override public void endVertex(){put(x);put(y);put(z);put(u);put(v);put(normal);vertices++;}
    }
    private static void put(double value){long bits=Double.doubleToRawLongBits(value);for(int shift=56;shift>=0;shift-=8)digest.update((byte)(bits>>>shift));}
    private static void put(int value){for(int shift=24;shift>=0;shift-=8)digest.update((byte)(value>>>shift));}
    private static TextureAtlasSprite sprite(String name,int x){TextureAtlasSprite sprite=new TextureAtlasSprite(name){};sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(32,16,x,0,false);return sprite;}
    public static void main(String[] ignored)throws Exception{
        net.minecraft.init.Bootstrap.register();digest=MessageDigest.getInstance("SHA-256");TextureAtlasSprite wall=sprite("panel_reference",0),metal=sprite("panel_metal_reference",16);
        Constructor<BlockProgrammableWall.Corner> diagonal=BlockProgrammableWall.Corner.class.getDeclaredConstructor(Boolean.class,Boolean.class,boolean.class,boolean.class);diagonal.setAccessible(true);
        Constructor<BlockProgrammableWall.FlatCorner> flat=BlockProgrammableWall.FlatCorner.class.getDeclaredConstructor(Double.class,Double.class,Boolean.class,Boolean.class,boolean.class,boolean.class);flat.setAccessible(true);
        Boolean[] hands={null,false,true};
        for(int turns=0;turns<4;turns++)for(double near:new double[]{0,2,6,10,12})for(Boolean front:hands)for(Boolean back:hands)for(int tails=0;tails<4;tails++){
            BlockProgrammableWall.FlatCorner corner=flat.newInstance(front==null?null:front?12D:0D,back==null?null:back?12D:0D,front,back,(tails&1)!=0,(tails&2)!=0);put(cases++);TEAnimatedScreenSelector.renderFlatWall(new Capture(turns),wall,metal,near,corner);
        }
        for(int turns=0;turns<4;turns++)for(boolean inverted:new boolean[]{false,true})for(double span:new double[]{8,16})for(int fill=0;fill<4;fill++)for(Boolean front:hands)for(Boolean back:hands)for(int tails=0;tails<4;tails++){
            BlockProgrammableWall.Corner corner=diagonal.newInstance(front,back,(tails&1)!=0,(tails&2)!=0);if(cases%5==0)corner.frontFlat=2D;if(cases%7==0)corner.backFlat=10D;
            double offset=cases%3==0?Double.NaN:inverted?8:0,lower=cases%4==0?Double.NaN:2,upper=cases%7==0?Double.NaN:10;
            double[] clip=cases%3==0?null:cases%3==1?new double[]{0,0,0,16,16,16}:new double[]{0,-8,-2,8,16,18};put(cases++);
            TEAnimatedScreenSelector.renderDiagonalWall(new Capture(turns),wall,metal,inverted,corner,span,fill,offset==8?-8:0,offset==8?8:16,offset,lower,upper,(cases&1)!=0,(cases&2)!=0,clip);
        }
        for(int turns=0;turns<4;turns++)for(double near:new double[]{0,2,6,10,12}){put(cases++);TEAnimatedScreenSelector.renderFlatWall(new Capture(turns),wall,metal,near,null);}
        for(int turns=0;turns<4;turns++)for(boolean inverted:new boolean[]{false,true})for(double span:new double[]{8,16})for(int fill=0;fill<4;fill++){put(cases++);TEAnimatedScreenSelector.renderDiagonalWall(new Capture(turns),wall,metal,inverted,null,span,fill,0,16,Double.NaN,Double.NaN,Double.NaN,false,false,null);}
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));if(!GOLDEN.isEmpty()&&!GOLDEN.equals(hash.toString()))throw new AssertionError("Current panel emitter changed "+hash);if(vertices%4!=0)throw new AssertionError("partial quad");System.out.println("Panel emitted reference PASS cases="+cases+" vertices="+vertices+" SHA256="+hash+" actual atlasUVs/coordinates/winding/packednormals");
    }
}
