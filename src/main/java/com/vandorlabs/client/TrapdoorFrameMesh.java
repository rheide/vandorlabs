package com.vandorlabs.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.*;

/** Authored frame strips extruded through the moving leaf, with an open central aperture. */
final class TrapdoorFrameMesh {
    private static final com.google.common.cache.Cache<Key,List<Strip>> CACHE=com.google.common.cache.CacheBuilder.newBuilder()
            .maximumWeight(2*1024*1024).weigher((Key k,List<Strip> v)->1024+v.size()*1536).build();
    private static final class Strip {
        final double[][] corners=new double[8][3],uv=new double[8][3],edgeUv=new double[8][3];
    }
    private static final class Key {
        final int first,choice,joins;final double edgeDepth;final boolean tiled;double[][] uv;final int hash;
        Key(int first,int choice,boolean tiled,double[][] uv,double edgeDepth,int joins){this.joins=joins;this.edgeDepth=edgeDepth;this.first=first;this.choice=choice;this.tiled=tiled;this.uv=uv;hash=31*(31*(31*(31*(31*first+choice)+(tiled?1:0))+Arrays.deepHashCode(uv))+joins)+Double.hashCode(edgeDepth);}
        void freeze(){double[][] copy=new double[8][];for(int i=0;i<8;i++)copy[i]=uv[i].clone();uv=copy;}
        public int hashCode(){return hash;}public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return first==k.first&&choice==k.choice&&joins==k.joins&&edgeDepth==k.edgeDepth&&tiled==k.tiled&&Arrays.deepEquals(uv,k.uv);}
    }
    static void clear(){CACHE.invalidateAll();}
    static long cachedLayouts(){CACHE.cleanUp();return CACHE.size();}
    static void draw(BufferBuilder buffer,TextureAtlasSprite artwork,TextureAtlasSprite edge,double[][] moving,double[][] uv,double[][] work,int light,int choice,int first,boolean tiled){
        draw(buffer,artwork,edge,moving,uv,work,light,choice,first,tiled,first==0?com.vandorlabs.render.TrapdoorGeometry.THICKNESS:2/16D,-1);
    }
    static void draw(BufferBuilder buffer,TextureAtlasSprite artwork,TextureAtlasSprite edge,double[][] moving,double[][] uv,double[][] work,int light,int choice,int first,boolean tiled,double edgeDepth,int joins){
        Key key=new Key(first,choice,tiled,uv,edgeDepth,joins);List<Strip> strips=CACHE.getIfPresent(key);
        if(strips==null){strips=build(key,ScreenFrameBorders.get(choice));key.freeze();CACHE.put(key,strips);}
        for(Strip strip:strips){
            for(int i=0;i<8;i++)for(int axis=0;axis<3;axis++)work[i][axis]=moving[0][axis]+strip.corners[i][0]*(moving[1][axis]-moving[0][axis])+strip.corners[i][1]*(moving[2][axis]-moving[0][axis])+strip.corners[i][2]*(moving[4][axis]-moving[0][axis]);
            TrapdoorSurfaceMesh.draw(buffer,artwork,artwork,work,strip.uv,light,first,first+2,false,false,false);
            for(int face=0;face<6;face++)if(face<first||face>=first+2)TEProgrammableTrapdoor.drawMesh(buffer,edge,work,strip.edgeUv,light,face,face+1);
        }
    }
    private static List<Strip> build(Key key,double[] borders){
        double[][] uv=key.uv;int v=key.first==0?2:1;
        int uAxis=axis(uv,0),vAxis=axis(uv,v),depth=3-uAxis-vAxis;
        double du=uv[1<<uAxis][0]-uv[0][0],dv=uv[1<<vAxis][v]-uv[0][v];
        double u0=Math.min(uv[0][0],uv[1<<uAxis][0]),u1=Math.max(uv[0][0],uv[1<<uAxis][0]);
        double v0=Math.min(uv[0][v],uv[1<<vAxis][v]),v1=Math.max(uv[0][v],uv[1<<vAxis][v]);
        // Tiled artwork has one aperture per leaf. Joined Fit/Stretch keeps one
        // outer frame and omits internal rails, retaining authored width per block.
        boolean left=key.tiled||(key.joins<0?u0<.002:(key.joins&1)==0),right=key.tiled||(key.joins<0?u1>1-.002:(key.joins&2)==0),top=key.tiled||(key.joins<0?v0<.002:(key.joins&4)==0),bottom=key.tiled||(key.joins<0?v1>1-.002:(key.joins&8)==0);
        double l=left?borders[0]:0,r=right?borders[1]:0,t=top?borders[2]:0,b=bottom?borders[3]:0;
        double[] us={0,l,1-r,1},vs={0,t,1-b,1};
        double[] sourceU={left?0:borders[0],borders[0],1-borders[1],right?1:1-borders[1]};
        double[] sourceV={top?0:borders[2],borders[2],1-borders[3],bottom?1:1-borders[3]};
        List<Strip> result=new ArrayList<>(8);
        for(int row=0;row<3;row++)for(int col=0;col<3;col++){
            if(row==1&&col==1 || us[col+1]-us[col]<1e-9 || vs[row+1]-vs[row]<1e-9)continue;
            Strip strip=new Strip();
            for(int i=0;i<8;i++){
                int ui=col+(((i&(1<<uAxis))==0)==(du>0)?0:1),vi=row+(((i&(1<<vAxis))==0)==(dv>0)?0:1);
                double pu=us[ui],pv=vs[vi];
                if(du<0)pu=1-pu;if(dv<0)pv=1-pv;
                strip.corners[i][uAxis]=pu;strip.corners[i][vAxis]=pv;strip.corners[i][depth]=(i&(1<<depth))==0?0:1;
                // Preserve the ordinary leaf's pixel scale on every outside and aperture wall.
                for(int axis=0;axis<3;axis++)strip.edgeUv[i][axis]=strip.corners[i][axis]*(axis==depth?key.edgeDepth:1);
                strip.uv[i][0]=sourceU[ui];strip.uv[i][v]=sourceV[vi]/com.vandorlabs.tiles.FrameAppearance.aspect(key.choice);strip.uv[i][depth]=(i&(1<<depth))==0?0:1;
            }
            result.add(strip);
        }
        return result;
    }
    private static int axis(double[][] uv,int component){int best=0;for(int axis=1;axis<3;axis++)if(Math.abs(uv[1<<axis][component]-uv[0][component])>Math.abs(uv[1<<best][component]-uv[0][component]))best=axis;return best;}
    private TrapdoorFrameMesh(){}
}
