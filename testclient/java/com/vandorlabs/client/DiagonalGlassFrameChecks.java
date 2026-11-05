package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableWall;
import java.lang.reflect.*;
import java.security.MessageDigest;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Before/after raw-float baseline of the actual cached diagonal glass frame emitter. */
public final class DiagonalGlassFrameChecks {
    private static volatile Object blackhole;
    private static final String GOLDEN="5be730c727631da70bf16b00ee67618791c19e24f46290e1771526ac51e1ea74";
    private static void put(MessageDigest hash,int n){for(int shift=24;shift>=0;shift-=8)hash.update((byte)(n>>>shift));}
    private static TextureAtlasSprite sprite(String id,int x){TextureAtlasSprite s=new TextureAtlasSprite(id){};s.setIconWidth(16);s.setIconHeight(16);s.initSprite(64,16,x,0,false);return s;}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();Field data=StaticSurfaceMesh.class.getDeclaredField("vertices");data.setAccessible(true);Constructor<BlockProgrammableWall.Corner> ctor=BlockProgrammableWall.Corner.class.getDeclaredConstructor(Boolean.class,Boolean.class,boolean.class,boolean.class);ctor.setAccessible(true);
        TextureAtlasSprite pane=sprite("frame_pane",0),plain=sprite("frame_rail",16),authored=sprite("reference/frames/authored",16),inner=sprite("frame_inner",32),outer=sprite("frame_outer",48);MessageDigest hash=MessageDigest.getInstance("SHA-256");int cases=0,values=0;StaticSurfaceMesh measured=null;
        Boolean[][] corners={{null,null},{true,null},{false,null},{true,true},{true,false},{false,true}};
        for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int fill=0;fill<4;fill++)for(int ci=0;ci<corners.length;ci++)for(int end=0;end<2;end++){
            double lower=end==0?Double.NaN:.25,upper=end==0?Double.NaN:.75;StaticSurfaceMesh.Capture c=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.renderDiagonalWall(c,pane,pane,inverted,ctor.newInstance(corners[ci][0],corners[ci][1],(ci&1)!=0,(ci&2)!=0),mode==1?16:8,fill,mode==2&&inverted?-8:0,mode==2&&inverted?8:16,mode==2?(inverted?8:0):Double.NaN,lower*16,upper*16,false,false,null);StaticSurfaceMesh mesh=c.finish();if(!inverted&&mode==1&&fill==0&&ci==4&&end==1)measured=mesh;
            for(int mask:new int[]{0,63,21,42})for(TextureAtlasSprite rail:new TextureAtlasSprite[]{plain,authored}){
                StaticSurfaceMesh frame=mesh.outlineFrame(rail,pane,inner,outer,(mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,(mask&16)!=0,(mask&32)!=0,mode==2?2:1);float[] out=(float[])data.get(frame);put(hash,cases++);put(hash,out.length);for(float value:out){put(hash,Float.floatToRawIntBits(value));values++;}
            }
        }
        if(Boolean.getBoolean("vandorlabs.diagonalFrameProfile")){
            com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().getId();
            for(int i=0;i<5000;i++)blackhole=measured.outlineFrame(authored,pane,inner,outer,false,false,false,false,false,false,1);
            for(int batch=0;batch<5;batch++){long bytes=bean.getThreadAllocatedBytes(id),time=System.nanoTime();for(int i=0;i<1000;i++)blackhole=measured.outlineFrame(authored,pane,inner,outer,false,false,false,false,false,false,1);long elapsed=System.nanoTime()-time,allocated=bean.getThreadAllocatedBytes(id)-bytes;System.out.println("Diagonal frame cold-cache profile batch="+batch+" bytesPerCall="+(allocated/1000D)+" nsPerCall="+(elapsed/1000D));}
        }
        StringBuilder result=new StringBuilder();for(byte b:hash.digest())result.append(String.format("%02x",b&255));if(!GOLDEN.isEmpty()&&!GOLDEN.equals(result.toString()))throw new AssertionError("Diagonal frame emitter changed "+result);System.out.println("Diagonal glass frame actual PASS cases="+cases+" values="+values+" SHA256="+result);
    }
}
