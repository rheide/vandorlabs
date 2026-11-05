package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableWall;
import java.lang.reflect.*;
import java.security.MessageDigest;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Raw float baseline of the actual legacy whole-quad connector filter. */
public final class DiagonalArmFilterChecks {
    private static final String GOLDEN="a1cc45e3b5e60d243049f001aec2caafb9aa6a7ac19f44170cee59dcffc79885";
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();Field data=StaticSurfaceMesh.class.getDeclaredField("vertices");data.setAccessible(true);Constructor<BlockProgrammableWall.Corner> ctor=BlockProgrammableWall.Corner.class.getDeclaredConstructor(Boolean.class,Boolean.class,boolean.class,boolean.class);ctor.setAccessible(true);
        TextureAtlasSprite wall=new TextureAtlasSprite("arm_filter_wall"){};wall.setIconWidth(16);wall.setIconHeight(16);wall.initSprite(16,16,0,0,false);MessageDigest hash=MessageDigest.getInstance("SHA-256");int cases=0,values=0;
        for(Boolean front:new Boolean[]{null,false,true})for(Boolean back:new Boolean[]{null,false,true})for(boolean left:new boolean[]{false,true})for(boolean right:new boolean[]{false,true})for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int end=0;end<3;end++){
            double low=end==1?4:Double.NaN,high=end==2?12:Double.NaN;StaticSurfaceMesh.Capture c=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.renderDiagonalWall(c,wall,wall,inverted,ctor.newInstance(front,back,left,right),mode==1?16:8,0,mode==0?0:inverted?-8:0,mode==0?16:inverted?8:16,mode==0?Double.NaN:inverted?8:0,low,high,false,false,null);
            float[] result=(float[])data.get(c.finish().diagonalArms(mode,inverted,low,high));put(hash,cases++);put(hash,result.length);for(float value:result){put(hash,Float.floatToRawIntBits(value));values++;}
        }
        StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format("%02x",b&255));if(!GOLDEN.isEmpty()&&!GOLDEN.equals(out.toString()))throw new AssertionError("Diagonal arm filter changed "+out);System.out.println("Diagonal arm filter actual PASS cases="+cases+" values="+values+" SHA256="+out);
    }
    private static void put(MessageDigest hash,int value){for(int shift=24;shift>=0;shift-=8)hash.update((byte)(value>>>shift));}
}
