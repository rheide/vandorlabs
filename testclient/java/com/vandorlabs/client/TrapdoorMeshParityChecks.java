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
        TextureAtlasSprite lower=new TextureAtlasSprite("trapdoor_parity_lower"){};
        lower.setIconWidth(32);lower.setIconHeight(16);lower.initSprite(256,256,128,16,false);
        VertexFormat format=BlockSurfaceFormat.get();
        BufferBuilder expected=new BufferBuilder(65536),actual=new BufferBuilder(65536);
        int cases=0;
        for(int shape=0;shape<4;shape++)for(int turn=0;turn<4;turn++)
            for(boolean inverted:new boolean[]{false,true})for(boolean sliding:new boolean[]{false,true})
                for(double pose:new double[]{0,.17,.5,.91,1})for(boolean tiled:new boolean[]{false,true})
                    for(boolean mirror:new boolean[]{false,true})for(int layout=0;layout<3;layout++)for(boolean custom:new boolean[]{false,true}) {
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
                        ReferenceTrapdoorSurfaceMesh.draw(expected,sprite,lower,vertices,uv,0xD00070,first,first+2,tiled,mirror,custom);
                        TrapdoorSurfaceMesh.draw(actual,sprite,lower,vertices,uv,0xD00070,first,first+2,tiled,mirror,custom);
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
        cacheLimits(sprite);
        System.out.println("PASS: "+cases+" trapdoor mesh oracle comparisons across rotation, motion, slopes, clipping and UV layouts");
    }
    private static void cacheLimits(TextureAtlasSprite sprite) {
        TrapdoorSurfaceMesh.clear();
        double[][] corners=TrapdoorGeometry.corners(0,false,0,0),uv=new double[8][3];
        BufferBuilder buffer=new BufferBuilder(262144);
        for(int pass=0;pass<2;pass++)for(int layout=0;layout<600;layout++) {
            for(int i=0;i<8;i++){uv[i][0]=((i&1)==0?0:pass==0?1:8)+layout/1000D;uv[i][2]=(i&4)==0?0:pass==0?1:8;}
            buffer.begin(7,BlockSurfaceFormat.get());
            TrapdoorSurfaceMesh.draw(buffer,sprite,corners,uv,0,0,0,2,true,false);
            buffer.finishDrawing();buffer.reset();
            if(TrapdoorSurfaceMesh.layoutCount()>TrapdoorSurfaceMesh.MAX_LAYOUTS || TrapdoorSurfaceMesh.pointCount()>TrapdoorSurfaceMesh.MAX_POINTS)
                throw new AssertionError("unbounded trapdoor layouts");
        }
        TrapdoorSurfaceMesh.clear();
        if(TrapdoorSurfaceMesh.layoutCount()!=0 || TrapdoorSurfaceMesh.pointCount()!=0)throw new AssertionError("layout clear accounting");
    }
}
