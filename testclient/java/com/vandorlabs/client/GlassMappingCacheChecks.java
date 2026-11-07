package com.vandorlabs.client;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Real UV transformations and warmed interleaved cache hits, without a graphics context. */
public final class GlassMappingCacheChecks {
    private static void need(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static TextureAtlasSprite sprite(int offset){TextureAtlasSprite sprite=new TextureAtlasSprite("mapping-check/"+offset){};sprite.setIconWidth(16);sprite.setIconHeight(32);sprite.initSprite(256,256,offset,64,false);return sprite;}
    private static StaticSurfaceMesh mesh(int quads){StaticSurfaceMesh.Capture out=StaticSurfaceMesh.independentCapture();for(int q=0;q<quads;q++)for(int v=0;v<4;v++)out.pos(v==1||v==2?16:0,v>=2?16:0,q/16D).tex(v==1||v==2?1:0,v>=2?0:1).normal(0,0,-1).endVertex();return out.finish();}
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();Field vertices=StaticSurfaceMesh.class.getDeclaredField("vertices");vertices.setAccessible(true);
        StaticSurfaceMesh source=mesh(4);TextureAtlasSprite sprite=sprite(32);
        GlassSurfaceGroups.Layout[] layouts=new GlassSurfaceGroups.Layout[16];StaticSurfaceMesh[] expected=new StaticSurfaceMesh[16];
        for(int i=0;i<16;i++)layouts[i]=new GlassSurfaceGroups.Layout(i%4,i/4,4,4,(i&1)!=0,(i&2)!=0);
        for(int mode=0;mode<3;mode++)for(boolean raw:new boolean[]{false,true})for(int i=0;i<16;i++){
            StaticSurfaceMesh direct=mode==0?source.glassTexture(sprite,layouts[i],raw,.125,.25):source.textureLayout(sprite,layouts[i],raw,.125,.25,mode);
            StaticSurfaceMesh cached=GlassSurfaceMapping.map(source,sprite,layouts[i],raw,.125,.25,mode);
            need(Arrays.equals((float[])vertices.get(direct),(float[])vertices.get(cached)),"exact mapped vertices mode="+mode+" raw="+raw+" layout="+i);
        }
        for(int i=0;i<16;i++)expected[i]=GlassSurfaceMapping.map(source,sprite,layouts[i],true,0,0,0);
        for(int n=0;n<20000;n++){int i=n&15;need(GlassSurfaceMapping.map(source,sprite,layouts[i],true,0,0,0)==expected[i],"warm identity");}
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId(),before=meter.getThreadAllocatedBytes(thread);
        for(int n=0;n<4096;n++){int i=n&15;if(GlassSurfaceMapping.map(source,sprite,layouts[i],true,0,0,0)!=expected[i])throw new AssertionError("changed warm identity");}
        long allocated=meter.getThreadAllocatedBytes(thread)-before;
        System.out.println("Glass UV cache calls4096 layouts16 allocated="+allocated+" exactProfiles=96");
        if(args.length==0||!args[0].equals("baseline"))need(allocated==0,"warmed UV-cache allocation="+allocated);
        if(args.length>0&&args[0].equals("baseline"))return;
        GlassSurfaceMapping.clear();need(GlassSurfaceMapping.entries()==0&&GlassSurfaceMapping.retainedBytes()==0,"explicit retirement");
        StaticSurfaceMesh large=mesh(1000),first=null;
        for(int i=0;i<64;i++){
            StaticSurfaceMesh mapped=GlassSurfaceMapping.map(large,sprite,new GlassSurfaceGroups.Layout(i,0,64,1),true,0,0,0);if(i==0)first=mapped;
            need(GlassSurfaceMapping.retainedBytes()<=8L*1024*1024&&GlassSurfaceMapping.entries()<=1024,"byte and entry budgets");
        }
        need(GlassSurfaceMapping.entries()<64,"large meshes evict below entry ceiling");
        StaticSurfaceMesh fresh=GlassSurfaceMapping.map(large,sprite,new GlassSurfaceGroups.Layout(0,0,64,1),true,-0D,0,0);
        need(fresh!=first&&Arrays.equals((float[])vertices.get(fresh),(float[])vertices.get(first)),"eviction rebuild preserves exact output");
        need(GlassSurfaceMapping.map(large,sprite,new GlassSurfaceGroups.Layout(0,0,64,1),true,0,0,0)==fresh,"signed-zero equal lookup hashes");
        long retained=GlassSurfaceMapping.retainedBytes();int entries=GlassSurfaceMapping.entries();StaticSurfaceMesh oversized=mesh(20000);
        StaticSurfaceMesh uncached=GlassSurfaceMapping.map(oversized,sprite,layouts[0],true,0,0,0);
        need(GlassSurfaceMapping.map(oversized,sprite,layouts[0],true,0,0,0)!=uncached,"oversized output remains uncached");
        need(GlassSurfaceMapping.retainedBytes()==retained&&GlassSurfaceMapping.entries()==entries,"oversized admission preserves bounded cache");
        StaticSurfaceMesh replaced=GlassSurfaceMapping.map(large,sprite(96),new GlassSurfaceGroups.Layout(0,0,64,1),true,0,0,0);
        need(!Arrays.equals((float[])vertices.get(replaced),(float[])vertices.get(fresh)),"new atlas identity rebuilds UVs");
        GlassSurfaceMapping.clear();need(GlassSurfaceMapping.entries()==0&&GlassSurfaceMapping.retainedBytes()==0,"retirement after pressure");
        System.out.println("Glass UV cache PASS exact output/zero hit allocation/byte eviction/oversized refusal/atlas replacement/retirement");
    }
}
