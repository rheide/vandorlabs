package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableWall;
import java.lang.reflect.Constructor;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Manual warmed allocation probe; identical source runs against the pre-extraction and current adapters. */
public final class PanelMeshAllocationChecks {
    private static volatile Object consumed;
    private static volatile long sampled;
    private static final int WARM=10000,SAMPLES=2000;
    private static BufferBuilder buffer;
    private static TextureAtlasSprite wall,metal,rail;
    private static BlockProgrammableWall.Corner corner;
    private static BlockProgrammableWall.FlatCorner flat;
    private static StaticSurfaceMesh mesh;
    private static PortholeHex.Slice aperture;
    private static GlassSurfaceGroups.Layout layout;
    private static final double[] CLIP={0,0,0,16,16,16};
    private static TextureAtlasSprite sprite(String name,int x){TextureAtlasSprite sprite=new TextureAtlasSprite(name){};sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(32,16,x,0,false);return sprite;}
    private static void run(int profile){
        if(profile>=4&&profile<6){StaticSurfaceMesh mapped=mesh.textureLayout(wall,layout,false,0,0,profile==4?1:2);consumed=mapped;sampled^=mapped.vertexCount();return;}
        buffer.begin(7,BlockSurfaceFormat.get());
        if(profile==7)TEAnimatedScreenSelector.drawFlatBar(buffer,wall,rail,rail,aperture);
        else if(profile==0)TEAnimatedScreenSelector.renderFlatWall(buffer,wall,metal,6,null);
        else if(profile==1)TEAnimatedScreenSelector.renderFlatWall(buffer,wall,metal,6,flat);
        else TEAnimatedScreenSelector.renderDiagonalWall(buffer,wall,profile==6?rail:metal,profile==3,corner,16,profile==2?3:0,0,16,profile==3?8:Double.NaN,2,10,false,false,profile==3?CLIP:null);
        buffer.finishDrawing();sampled^=buffer.getVertexCount();if(buffer.getVertexCount()>0)sampled^=buffer.getByteBuffer().getLong(0);
    }
    public static void main(String[] ignored)throws Exception{
        net.minecraft.init.Bootstrap.register();
        java.lang.reflect.Field registry=FrameEdgeTextures.class.getDeclaredField("EDGES");registry.setAccessible(true);@SuppressWarnings("unchecked") java.util.Map<String,Object> edges=(java.util.Map<String,Object>)registry.get(null);
        Class<?> edgeType=Class.forName("com.vandorlabs.client.FrameEdgeTextures$Edge");Constructor<?> edgeConstructor=edgeType.getDeclaredConstructor(String.class,String.class,int.class,double.class);edgeConstructor.setAccessible(true);Object variants=java.lang.reflect.Array.newInstance(edgeType,4);
        for(int i=0;i<4;i++){TextureAtlasSprite sprite=(TextureAtlasSprite)edgeConstructor.newInstance("allocation_rail"+(i==0?"":"_edge_"+i),"allocation_source",i,1D);sprite.setIconWidth(64);sprite.setIconHeight(64);sprite.initSprite(256,64,i*64,0,false);java.lang.reflect.Array.set(variants,i,sprite);}edges.put("allocation_rail",variants);rail=(TextureAtlasSprite)java.lang.reflect.Array.get(variants,0);
        buffer=new BufferBuilder(1<<20);wall=sprite("allocation_wall",0);metal=sprite("allocation_metal",16);
        Constructor<BlockProgrammableWall.Corner> constructor=BlockProgrammableWall.Corner.class.getDeclaredConstructor(Boolean.class,Boolean.class,boolean.class,boolean.class);constructor.setAccessible(true);corner=constructor.newInstance(false,true,false,true);
        Constructor<BlockProgrammableWall.FlatCorner> flatConstructor=BlockProgrammableWall.FlatCorner.class.getDeclaredConstructor(Double.class,Double.class,Boolean.class,Boolean.class,boolean.class,boolean.class);flatConstructor.setAccessible(true);flat=flatConstructor.newInstance(0D,12D,false,true,false,false);
        StaticSurfaceMesh.Capture source=StaticSurfaceMesh.capture();WorldSurface.quad(source,wall,false,new double[]{0,0,0,0,16},new double[]{48,0,0,16,16},new double[]{48,16,0,16,0},new double[]{0,16,0,0,0});mesh=source.finish();layout=new GlassSurfaceGroups.Layout(1,1,2,3,false,false);aperture=new PortholeHex(3,2,3).slice(0,0);
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();String[] names={"flat-plain","flat-corner","diagonal-filled","diagonal-half-clipped","uv-tile","uv-fit","diagonal-authored-frame","aperture-authored-frame"};
        boolean frameOnly=Boolean.getBoolean("vandorlabs.panelFrameOnly"),apertureOnly=Boolean.getBoolean("vandorlabs.panelApertureOnly");int warm=frameOnly?50000:WARM,samples=frameOnly?10000:SAMPLES,batches=frameOnly?7:1;
        for(int profile=0;profile<names.length;profile++){
            if(frameOnly&&profile!=6||apertureOnly&&profile!=7)continue;
            for(int batch=0;batch<batches;batch++){
            for(int i=0;i<warm;i++)run(profile);long before=bean.getThreadAllocatedBytes(thread),cpu=bean.getCurrentThreadCpuTime(),start=System.nanoTime();for(int i=0;i<samples;i++)run(profile);long nanos=System.nanoTime()-start,cpuNanos=bean.getCurrentThreadCpuTime()-cpu,bytes=bean.getThreadAllocatedBytes(thread)-before;System.out.println("Panel warmed allocation profile="+names[profile]+" bytesPerCall="+(bytes/(double)samples)+" nsPerCall="+(nanos/(double)samples)+" cpuNsPerCall="+(cpuNanos/(double)samples)+" batch="+batch+" warm="+warm+" samples="+samples+" buffer="+(profile<4||profile>=6?"BufferBuilder":"StaticSurfaceMesh.Capture"));
            }
        }
    }
}
