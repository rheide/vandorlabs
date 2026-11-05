package com.vandorlabs.client;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
/** Actual legacy aperture output frozen before sharing polygon construction. */
public final class PanelApertureEmitterChecks {
    private static final String GOLDEN="aefb7e1555d58a08080b4d144cd372e9f1e833f7a649ffbfdccfc6700ced126a";
    private static MessageDigest digest;private static int cases,vertices;
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
        net.minecraft.init.Bootstrap.register();digest=MessageDigest.getInstance("SHA-256");TextureAtlasSprite wall=sprite("aperture_wall",0),metal=sprite("aperture_metal",16);
        Method panel=TEAnimatedScreenSelector.class.getDeclaredMethod("panelQuad",BufferBuilder.class,TextureAtlasSprite.class,DiagonalPortholeMesh.class,double.class,double.class,double.class,double.class,double.class,double.class,double.class,double.class);
        Method top=TEAnimatedScreenSelector.class.getDeclaredMethod("topRim",BufferBuilder.class,TextureAtlasSprite.class,DiagonalPortholeMesh.class,double.class,double.class,double.class,double.class);
        Method side=TEAnimatedScreenSelector.class.getDeclaredMethod("sideRim",BufferBuilder.class,TextureAtlasSprite.class,DiagonalPortholeMesh.class,double.class,double.class,double.class,double.class);
        Method rim=TEAnimatedScreenSelector.class.getDeclaredMethod("rimSegment",BufferBuilder.class,TextureAtlasSprite.class,DiagonalPortholeMesh.class,double.class,double.class,double.class,double.class,double.class);
        panel.setAccessible(true);top.setAccessible(true);side.setAccessible(true);rim.setAccessible(true);
        for(int turns=0;turns<4;turns++)for(int columns:new int[]{1,2,3})for(int rows:new int[]{1,2})for(int shape=0;shape<4;shape++)for(int border=0;border<2;border++){
            PortholeHex outline=new PortholeHex(columns,rows,shape,border);
            for(int row=0;row<rows;row++)for(int column=0;column<columns;column++){
                PortholeHex.Slice slice=outline.slice(column,row);put(cases++);TEAnimatedScreenSelector.drawFlatBar(new Capture(turns),wall,metal,metal,slice);
                for(int mode=-1;mode<3;mode++)for(boolean inverted:new boolean[]{false,true}){
                    DiagonalPortholeMesh mesh=mode<0?null:new DiagonalPortholeMesh(mode,inverted,2,10,new double[]{0,-8,-2,16,24,18});Capture output=new Capture(turns);put(cases++);
                    for(double[] q:slice.frameQuads)panel.invoke(null,output,wall,mesh,q[0],q[1],q[2],q[3],q[4],q[5],q[6],q[7]);
                    for(double[] e:slice.hexEdges)rim.invoke(null,output,metal,mesh,e[0],e[1],e[2],e[3],mode==0?4D:4*Math.sqrt(2));
                    for(double y:new double[]{0,16}){top.invoke(null,output,metal,mesh,y,0D,7.25D,4D);top.invoke(null,output,metal,mesh,y,7.25D,16D,4D);top.invoke(null,output,metal,mesh,y,3D,3D,4D);}
                    for(double x:new double[]{0,16}){side.invoke(null,output,metal,mesh,x,0D,5.5D,4D);side.invoke(null,output,metal,mesh,x,5.5D,16D,4D);side.invoke(null,output,metal,mesh,x,3D,3D,4D);}
                }
            }
        }
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));if(!GOLDEN.isEmpty()&&!GOLDEN.equals(hash.toString()))throw new AssertionError("Aperture output changed "+hash);if(vertices%4!=0)throw new AssertionError("Partial quad");System.out.println("Panel aperture reference PASS cases="+cases+" vertices="+vertices+" SHA256="+hash+" actual BufferBuilder UV/winding/normals");
    }
}
