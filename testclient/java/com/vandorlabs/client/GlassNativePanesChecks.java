package com.vandorlabs.client;

import com.vandorlabs.blocks.PanelDepth;
import com.vandorlabs.tiles.GlassTextures;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.*;

/** Exact delivered-patch pane records, resource lifetimes and real CPU emission costs. */
public final class GlassNativePanesChecks {
    private static int sink;
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class Sprite extends TextureAtlasSprite {
        long uv;
        Sprite(String name,int index,int generation){
            super(name);setIconWidth(16+(index%3)*8);setIconHeight(16+(index%5)*8);
            initSprite(1024,1024,(index%16)*48+generation*2,(index/16)*64+generation*3,false);
        }
        @Override public float getInterpolatedU(double value){uv++;return super.getInterpolatedU(value);}
        @Override public float getInterpolatedV(double value){uv++;return super.getInterpolatedV(value);}
    }
    private static final class Atlas extends TextureMap {
        final Map<String,Sprite> sprites=new HashMap<>();long reads;
        Atlas(){super("textures");replace(0);}
        void replace(int generation){
            sprites.clear();int index=0;
            for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<4;tint++){
                String name=GlassTextures.texture(shade,1,tint);
                if(!sprites.containsKey(name))sprites.put(name,new Sprite(name,index++,generation));
            }
        }
        @Override public TextureAtlasSprite getAtlasSprite(String name){reads++;Sprite sprite=sprites.get(name);if(sprite==null)throw new AssertionError("unknown native sprite "+name);return sprite;}
        void reset(){reads=0;for(Sprite sprite:sprites.values())sprite.uv=0;}
        long uv(){long count=0;for(Sprite sprite:sprites.values())count+=sprite.uv;return count;}
    }
    /** A changing extension written at endVertex catches bypasses of the shader writer. */
    private static final class Records extends BufferBuilder {
        int material,ends;
        Records(int capacity){super(capacity);}
        @Override public void endVertex(){
            super.endVertex();ends++;
            if(getVertexFormat().getNextOffset()>32){
                int at=(getVertexCount()-1)*getVertexFormat().getNextOffset()+32;
                for(int i=0;i<4;i++)getByteBuffer().putInt(at+i*4,material+getVertexCount()*31+i);
            }
        }
        @Override public void addVertexData(int[] data){throw new AssertionError("native panes bypassed the live vertex writer");}
    }
    private static VertexFormat expanded(){return new VertexFormat(BlockSurfaceFormat.get())
            .addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.GENERIC,4));}

    // Native append path copied from delivered checkpoint 00e15269c4e3a947505ae15c98ac7000ed4da795.
    // World/GL work is intentionally outside this geometry/preparation-only CPU comparison.
    private static void previous(Atlas atlas,BufferBuilder buffer,int shade,int size,int tint,boolean rotated,int depth,
            double x,double y,double z,int sky,int block){
        TextureAtlasSprite sprite=atlas.getAtlasSprite(GlassTextures.texture(shade,size,tint));
        double offset=PanelDepth.offset(depth)/16D;float opacity=1F;
        for(int face=0;face<2;face++)for(int i=0;i<4;i++){
            boolean near=face==0;
            int index=near?(4-i)&3:i;double px=index==1||index==2?1:0,py=index<2?0:1,d=(near?7D/16:9D/16)+offset;
            buffer.pos(x+(rotated?1-d:px),y+py,z+(rotated?px:d)).color(1,1,1,opacity)
                    .tex(sprite.getInterpolatedU(px*16),sprite.getInterpolatedV((1-py)*16)).lightmap(sky,block)
                    .normal(rotated?(near?1:-1):0,0,rotated?0:(near?-1:1)).endVertex();
        }
    }
    private static void same(Records old,Records current,String description){
        need(old.getVertexCount()==8&&current.getVertexCount()==8,"native pane vertex count "+description);
        int bytes=old.getVertexCount()*old.getVertexFormat().getNextOffset();ByteBuffer a=old.getByteBuffer(),b=current.getByteBuffer();
        for(int i=0;i<bytes;i++)if(a.get(i)!=b.get(i))throw new AssertionError("pane byte "+i+" differs "+description);
    }
    private static int parity(Atlas atlas){
        GlassNativePanes.clear();GlassNativePanes.Palette palette=GlassNativePanes.palette(atlas);
        Records old=new Records(4096),current=new Records(4096);int cases=0;
        for(VertexFormat format:new VertexFormat[]{BlockSurfaceFormat.get(),expanded()})
            for(int shade=-1;shade<=GlassTextures.IDS.length;shade++)for(int tint=-1;tint<=4;tint++)
                for(int size:new int[]{0,1,2,3,8})for(int rotation=0;rotation<2;rotation++)for(int depth=-1;depth<=3;depth++){
                    double origin=cases%3==0?0:cases%3==1?-12.375:29999999.125;
                    int sky=(cases*16)&240,block=(cases*80+32)&240;
                    old.material=current.material=cases*103;
                    old.begin(7,format);current.begin(7,format);
                    previous(atlas,old,shade,size,tint,rotation!=0,depth,origin,origin/4,-origin,sky,block);
                    palette.get(shade,size,tint,rotation!=0,depth).draw(current,origin,origin/4,-origin,sky,block);
                    old.finishDrawing();current.finishDrawing();same(old,current,"shade="+shade+" tint="+tint+" size="+size+" rotation="+rotation+" depth="+depth);
                    cases++;
                }
        need(old.ends==cases*8&&current.ends==old.ends,"live per-vertex hooks differ");
        return cases;
    }
    private static void reload(Atlas atlas){
        GlassNativePanes.Palette old=GlassNativePanes.palette(atlas);GlassNativePanes.Pane pane=old.get(17,1,2,true,1);
        atlas.replace(1);GlassNativePanes.clear();GlassNativePanes.Palette next=GlassNativePanes.palette(atlas);
        need(old!=next&&pane!=next.get(17,1,2,true,1),"same atlas reload retained native geometry");
        Records expected=new Records(4096),actual=new Records(4096);
        expected.begin(7,expanded());actual.begin(7,expanded());
        previous(atlas,expected,17,1,2,true,1,0,0,0,240,32);next.get(17,3,2,true,1).draw(actual,0,0,0,240,32);
        expected.finishDrawing();actual.finishDrawing();same(expected,actual,"resource reload");
        Atlas replacement=new Atlas();need(GlassNativePanes.palette(replacement)!=next,"new atlas identity reused old coordinates");
        need(GlassNativePanes.palette(replacement)==GlassNativePanes.palette(replacement),"warm palette rebuilt");
        GlassNativePanes.clear();
    }
    private static void emit(Atlas atlas,BufferBuilder buffer,int count,boolean prepared,boolean colors){
        buffer.begin(7,BlockSurfaceFormat.get());
        for(int i=0;i<count;i++){
            int shade=colors?i%19:0,tint=colors?(i/19)%3:1,depth=colors?i%3:0;boolean rotated=colors&&(i&1)!=0;
            double x=(i%128)+.125,y=i/128D,z=-i%16;int sky=i&240,block=(i*3)&240;
            if(prepared)GlassNativePanes.palette(atlas).get(shade,i%3+1,tint,rotated,depth).draw(buffer,x,y,z,sky,block);
            else previous(atlas,buffer,shade,i%3+1,tint,rotated,depth,x,y,z,sky,block);
        }
        buffer.finishDrawing();sink=buffer.getByteBuffer().getInt((count*8-1)*32);
    }
    private static void benchmark(int count,boolean colors){
        Atlas atlas=new Atlas();GlassNativePanes.clear();GlassNativePanes.palette(atlas);BufferBuilder buffer=new BufferBuilder(count*8*8+1024);
        long[] first=new long[2];for(int mode=0;mode<2;mode++){long start=System.nanoTime();emit(atlas,buffer,count,mode==1,colors);first[mode]=System.nanoTime()-start;}
        // Every density gets enough calls to compile the real BufferBuilder path.
        // A fixed number of frames left the 256-pane case in a different JIT tier
        // after parity exercised the shader-recording subclass instead.
        int warm=Math.max(12,131072/count);
        for(int i=0;i<warm;i++){emit(atlas,buffer,count,false,colors);emit(atlas,buffer,count,true,colors);}
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        long[][] nanos=new long[2][7],bytes=new long[2][7];long[] reads=new long[2],uv=new long[2];
        for(int round=0;round<7;round++)for(int order=0;order<2;order++){
            int mode=(round+order)&1;atlas.reset();long allocated=meter.getThreadAllocatedBytes(thread),started=System.nanoTime();
            emit(atlas,buffer,count,mode==1,colors);
            nanos[mode][round]=System.nanoTime()-started;bytes[mode][round]=meter.getThreadAllocatedBytes(thread)-allocated;reads[mode]=atlas.reads;uv[mode]=atlas.uv();
        }
        for(int mode=0;mode<2;mode++){Arrays.sort(nanos[mode]);Arrays.sort(bytes[mode]);}
        need(reads[0]==count&&uv[0]==count*16L&&reads[1]==0&&uv[1]==0,"warm atlas/UV work was not eliminated");
        need(bytes[1][3]<1024&&bytes[0][3]>count*80L,"native plan allocation baseline/current "+bytes[0][3]+"/"+bytes[1][3]);
        System.out.println("Native glass preparation+emission panes="+count+" colors="+colors+" previous/current medianNs="+nanos[0][3]+"/"+nanos[1][3]+" allocated="+bytes[0][3]+"/"+bytes[1][3]+" atlasReads="+Arrays.toString(reads)+" UVCalls="+Arrays.toString(uv)+" firstBatchNs="+Arrays.toString(first)+" warmCallsPerMode="+(warm*count));
    }
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();Atlas atlas=new Atlas();int cases=parity(atlas);reload(atlas);
        for(int count:new int[]{256,4096,16384})for(boolean colors:new boolean[]{false,true})benchmark(count,colors);
        GlassNativePanes.clear();
        System.out.println("Native glass plans PASS exact byte cases="+cases+" native/expanded records; live shader-writer hooks, light, all shades/tints/depths/legacy sizes, zero inset, reload and finite 456-plan dense reuse");
    }
}
