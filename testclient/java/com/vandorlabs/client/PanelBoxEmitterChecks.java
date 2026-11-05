package com.vandorlabs.client;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Executes the actual immediate arm-box path with only GPU upload replaced by a CPU consumer. */
public final class PanelBoxEmitterChecks {
    private static final String GOLDEN="ba9d47789c726bfcd588c7910e601ed80df187d10dc12589dbb7209753f61bb8";private static MessageDigest digest;private static int cases,values;private static volatile long consumed;
    private static void put(float value){int bits=Float.floatToRawIntBits(value);for(int shift=24;shift>=0;shift-=8)digest.update((byte)(bits>>>shift));values++;}
    private static final class Capture extends BufferBuilder {
        private float x,y,z,u,v,nx,ny,nz;Capture(){super(16);}
        @Override public BufferBuilder pos(double x,double y,double z){this.x=(float)x;this.y=(float)y;this.z=(float)z;return this;}
        @Override public BufferBuilder tex(double u,double v){this.u=(float)u;this.v=(float)v;return this;}
        @Override public BufferBuilder color(int r,int g,int b,int a){return this;}
        @Override public BufferBuilder lightmap(int sky,int block){return this;}
        @Override public BufferBuilder normal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
        @Override public void endVertex(){put(x);put(y);put(z);put(u);put(v);put(nx);put(ny);put(nz);}
    }
    private static final class CpuTessellator extends Tessellator {
        BufferBuilder active=new Capture();int draws;CpuTessellator(){super(16);}
        @Override public BufferBuilder getBuffer(){return active;}
        @Override public void draw(){active.finishDrawing();draws++;consumed^=active.getVertexCount();if(!(active instanceof Capture)&&active.getVertexCount()>0)consumed^=active.getByteBuffer().getLong(0);}
    }
    private static TextureAtlasSprite sprite(String name,int x){TextureAtlasSprite s=new TextureAtlasSprite(name){};s.setIconWidth(16);s.setIconHeight(16);s.initSprite(1024,128,x,0,false);return s;}
    private static TextureAtlasSprite frame()throws Exception{Field field=FrameEdgeTextures.class.getDeclaredField("EDGES");field.setAccessible(true);@SuppressWarnings("unchecked") java.util.Map<String,Object> map=(java.util.Map<String,Object>)field.get(null);Class<?> type=Class.forName("com.vandorlabs.client.FrameEdgeTextures$Edge");Constructor<?> constructor=type.getDeclaredConstructor(String.class,String.class,int.class,double.class);constructor.setAccessible(true);Object edges=Array.newInstance(type,4);for(int edge=0;edge<4;edge++){TextureAtlasSprite s=(TextureAtlasSprite)constructor.newInstance("box_rail"+(edge==0?"":"_edge_"+edge),"box_rail_source",edge,1D);s.setIconWidth(64);s.setIconHeight(64);s.initSprite(1024,128,256+edge*64,0,false);Array.set(edges,edge,s);}map.put("box_rail",edges);return (TextureAtlasSprite)Array.get(edges,0);}
    public static void main(String[] ignored)throws Throwable{
        net.minecraft.init.Bootstrap.register();digest=MessageDigest.getInstance("SHA-256");Field singleton=Tessellator.class.getDeclaredField("INSTANCE");singleton.setAccessible(true);Field modifiers=Field.class.getDeclaredField("modifiers");modifiers.setAccessible(true);int flags=singleton.getModifiers();modifiers.setInt(singleton,flags&~Modifier.FINAL);Object original=singleton.get(null);CpuTessellator cpu=new CpuTessellator();singleton.set(null,cpu);
        Method method=TEAnimatedScreenSelector.class.getDeclaredMethod("renderWallBox",TextureAtlasSprite.class,TextureAtlasSprite.class,boolean.class,double.class,double.class,double.class,double.class,double.class,double.class,boolean.class);method.setAccessible(true);MethodHandle call=MethodHandles.lookup().unreflect(method);TextureAtlasSprite wall=sprite("box_wall",0),plain=sprite("box_side",32),frame=frame();
        try{
            for(double near:new double[]{0,6,12})for(double arm:new double[]{0,6,12})for(boolean back:new boolean[]{false,true})for(double height:new double[]{8,16})for(boolean along:new boolean[]{false,true})for(boolean front:new boolean[]{false,true})for(boolean authored:new boolean[]{false,true}){
                put(cases++);double z0=back?near+4:0,z1=back?16:near;TextureAtlasSprite side=authored?frame:plain;call.invokeExact(wall,side,along,arm,0D,z0,arm+4,height,z1,front);
            }
            if(cpu.draws!=cases)throw new AssertionError("Missing actual box draw");StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));if(!GOLDEN.isEmpty()&&!GOLDEN.equals(hash.toString()))throw new AssertionError("Immediate box emitter changed "+hash);System.out.println("Panel immediate box reference PASS cases="+cases+" floatValues="+values+" SHA256="+hash+" actual BufferBuilder path, GPU upload only bypassed");
            if(Boolean.getBoolean("vandorlabs.panelBoxAllocation")){cpu.active=new BufferBuilder(1<<16);com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();for(TextureAtlasSprite side:new TextureAtlasSprite[]{plain,frame}){for(int i=0;i<50000;i++)call.invokeExact(wall,side,false,0D,0D,0D,4D,16D,6D,false);long before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();for(int i=0;i<10000;i++)call.invokeExact(wall,side,false,0D,0D,0D,4D,16D,6D,false);System.out.println("Panel box warmed allocation "+side.getIconName()+" bytesPerCall="+(bean.getThreadAllocatedBytes(thread)-before)/10000D+" nsPerCall="+(System.nanoTime()-start)/10000D+" actualBufferBuilder");}}
        }finally{singleton.set(null,original);modifiers.setInt(singleton,flags);}
    }
}
