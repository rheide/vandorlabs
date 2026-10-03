package com.vandorlabs.client;

import com.vandorlabs.render.DiagonalTrapdoorGeometry;
import com.vandorlabs.render.TrapdoorGeometry;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import java.nio.ByteBuffer;

/** Compare moving and stationary surfaces against the released clipping algorithm. */
final class TrapdoorMeshParityChecks {
    static void run() {
        TextureAtlasSprite sprite=new TextureAtlasSprite("trapdoor_parity"){};
        sprite.setIconWidth(16);sprite.setIconHeight(32);sprite.initSprite(256,256,32,64,false);
        VertexFormat format=BlockSurfaceFormat.get();
        BufferBuilder expected=new BufferBuilder(65536),actual=new BufferBuilder(65536);
        int cases=0;
        for(int shape=0;shape<4;shape++)for(int turn=0;turn<4;turn++)
            for(boolean inverted:new boolean[]{false,true})for(boolean sliding:new boolean[]{false,true})
                for(double pose:new double[]{0,.17,.5,.91,1})for(boolean tiled:new boolean[]{false,true})
                    for(boolean mirror:new boolean[]{false,true})for(int layout=0;layout<3;layout++) {
                        double[][] vertices=shape==0?TrapdoorGeometry.corners(1,sliding,turn,pose)
                                :DiagonalTrapdoorGeometry.corners(shape-1,inverted,turn,sliding,inverted,pose);
                        int first=shape==1 || shape==2?2:0;
                        double[][] uv=new double[8][3];
                        for(int i=0;i<8;i++) {
                            double u=((i&1)==0?0:1)*(layout==0?1:layout==1?2.5:.125)+(layout==1?-.25:0);
                            double v=((i&(first==0?4:2))==0?0:1)*(layout==0?1:layout==1?1.75:.5)+(layout==2?.5:0);
                            uv[i]=new double[]{u,first==0?((i&2)==0?0:1):v,first==0?v:((i&4)==0?0:1)};
                        }
                        expected.begin(7,format);actual.begin(7,format);
                        ReferenceTrapdoorSurfaceMesh.draw(expected,sprite,vertices,uv,0xD00070,0,first,first+2,tiled,mirror);
                        TrapdoorSurfaceMesh.draw(actual,sprite,vertices,uv,0xD00070,0,first,first+2,tiled,mirror);
                        expected.finishDrawing();actual.finishDrawing();
                        if(expected.getVertexCount()!=actual.getVertexCount())throw new AssertionError("surface vertex count case "+cases);
                        ByteBuffer a=expected.getByteBuffer(),b=actual.getByteBuffer();
                        int stride=format.getNextOffset(),tex=format.getUvOffsetById(0);
                        for(int i=0;i<expected.getVertexCount();i++)for(int offset=0;offset<stride;offset+=4) {
                            int at=i*stride+offset;
                            if(offset<12 || offset==tex || offset==tex+4) {
                                if(Math.abs(a.getFloat(at)-b.getFloat(at))>1e-6)throw new AssertionError("surface position/UV case "+cases+" offset "+offset);
                            } else if(a.getInt(at)!=b.getInt(at))throw new AssertionError("surface attributes case "+cases+" offset "+offset);
                        }
                        expected.reset();actual.reset();cases++;
                    }
        System.out.println("PASS: "+cases+" trapdoor mesh oracle comparisons across rotation, motion, slopes, clipping and UV layouts");
    }
}
