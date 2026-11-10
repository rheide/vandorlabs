package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.tiles.FrameAppearance;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.*;

/** Authored frame widths keep opaque inner sides aligned with the front opening. */
final class GlassFrameMesh {
    private static final long MAX_BYTES=8L*1024*1024;
    private static long retainedBytes;
    private static final Map<Key,StaticSurfaceMesh> CACHE=new LinkedHashMap<>(64,.75F,true);
    static void clear(){CACHE.clear();retainedBytes=0;PROBE.set(null,null,null,0,0,0,0);}
    static boolean probeEmpty(){return PROBE.front==null&&PROBE.inner==null&&PROBE.outer==null;}
    static long retainedBytes(){return retainedBytes;}
    // Render-thread-only mutable probe; retained keys are owned immutable copies.
    private static final Key PROBE=new Key();
    private static final class Key{
        TextureAtlasSprite front,inner,outer;int mask,choice,columns,rows;
        Key(){}Key(Key source){set(source.front,source.inner,source.outer,source.mask,source.choice,source.columns,source.rows);}
        Key set(TextureAtlasSprite f,TextureAtlasSprite i,TextureAtlasSprite o,int m,int c,int w,int h){front=f;inner=i;outer=o;mask=m;choice=c;columns=w;rows=h;return this;}
        public int hashCode(){int h=System.identityHashCode(front);h=31*h+System.identityHashCode(inner);h=31*h+System.identityHashCode(outer);h=31*h+mask;h=31*h+choice;h=31*h+columns;return 31*h+rows;}
        public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return front==k.front&&inner==k.inner&&outer==k.outer&&mask==k.mask&&choice==k.choice&&columns==k.columns&&rows==k.rows;}
    }
    private static final GlassSurfaceGroups.Layout UNIT=new GlassSurfaceGroups.Layout(0,0,1,1);
    static StaticSurfaceMesh get(TileEntityProgrammableGlass tile){return get(tile,UNIT);}
    static StaticSurfaceMesh get(TileEntityProgrammableGlass tile,GlassSurfaceGroups.Layout layout){
        Key key=key(tile,layout);
        try {
        StaticSurfaceMesh mesh=CACHE.get(key);if(mesh!=null)return mesh;key=new Key(key);StaticSurfaceMesh.Capture b=StaticSurfaceMesh.capture();
        emit(b,key);
        mesh=b.finish();long cost=128+mesh.cacheRetainedBytes();
        if(cost<=MAX_BYTES){CACHE.put(key,mesh);retainedBytes+=cost;
            while(CACHE.size()>256||retainedBytes>MAX_BYTES){Key oldest=CACHE.keySet().iterator().next();retainedBytes-=128+CACHE.remove(oldest).cacheRetainedBytes();}}
        return mesh;
        }finally{PROBE.set(null,null,null,0,0,0,0);}
    }
    private static Key key(TileEntityProgrammableGlass tile,GlassSurfaceGroups.Layout layout){
        net.minecraft.client.renderer.texture.TextureMap atlas=Minecraft.getMinecraft().getTextureMapBlocks();
        return PROBE.set(atlas.getAtlasSprite(tile.frameAppearance.rail("vandorlabs:blocks/space_doors/medium/double_frame_metal")),atlas.getAtlasSprite(tile.frameAppearance.interior("vandorlabs:blocks/programmable_glass/metal_side")),atlas.getAtlasSprite(tile.frameAppearance.exterior("vandorlabs:blocks/programmable_glass/metal_side")),GlassSurfaceGroups.frameMask(tile),tile.frameAppearance.front,layout.columns,layout.rows);
    }
    /** The chunk capture uses this same emitter and keeps each authored atlas sprite. */
    static void emit(TileEntityProgrammableGlass tile,BufferBuilder buffer){
        try{emit(buffer,key(tile,UNIT));}finally{PROBE.set(null,null,null,0,0,0,0);}
    }
    interface Sprites { void texture(TextureAtlasSprite sprite); }
    private static void texture(BufferBuilder buffer,TextureAtlasSprite sprite){if(buffer instanceof Sprites)((Sprites)buffer).texture(sprite);}
    private static void quad(BufferBuilder buffer,TextureAtlasSprite sprite,boolean reverse,double[]... points){
        texture(buffer,FrameEdgeTextures.edge(sprite,points));WorldSurface.quad(buffer,sprite,reverse,points);
    }
    private static void emit(BufferBuilder b,Key key){
        int mask=key.mask;
        double[] widths=ScreenFrameBorders.get(key.choice);
        double left=(mask&1)!=0?(key.choice<0?1:widths[0]*16):0;
        double right=(mask&2)!=0?(key.choice<0?1:widths[1]*16):0;
        double bottom=(mask&4)!=0?(key.choice<0?1:widths[3]*16):0;
        double top=(mask&8)!=0?(key.choice<0?1:widths[2]*16):0;
        if(left>0)box(b,key,0,0,left,16);if(right>0)box(b,key,16-right,0,16,16);
        if(bottom>0)box(b,key,left,0,16-right,bottom);if(top>0)box(b,key,left,16-top,16-right,16);
        for(int corner=0;corner<4;corner++)if((mask&(16<<corner))!=0){
            boolean rightCorner=(corner&1)!=0,topCorner=(corner&2)!=0;double w=key.choice<0?1:widths[rightCorner?1:0]*16,h=key.choice<0?1:widths[topCorner?2:3]*16;
            box(b,key,rightCorner?16-w:0,topCorner?16-h:0,rightCorner?16:w,topCorner?16:h);
        }
        if(key.choice>=0&&FrameAppearance.border(key.choice))front(b,key,Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(key.choice)),widths);
    }
    private static void front(BufferBuilder b,Key key,TextureAtlasSprite sprite,double[] border){
        texture(b,sprite);
        double l=(key.mask&1)!=0?border[0]:0,r=(key.mask&2)!=0?border[1]:0;
        double t=(key.mask&8)!=0?border[2]:0,d=(key.mask&4)!=0?border[3]:0;
        // Same nine-slice geometry/source cuts as programmable screen frames.
        // Interior cells omit borders; outer cells repeat only the middle edge.
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)if(row!=1||col!=1){
            double x0=ScreenFrameBorders.cut(col,l,r)*16,x1=ScreenFrameBorders.cut(col+1,l,r)*16;
            double y0=(1-ScreenFrameBorders.cut(row+1,t,d))*16,y1=(1-ScreenFrameBorders.cut(row,t,d))*16;
            if(x1<=x0||y1<=y0)continue;
            double u0=sprite.getInterpolatedU(ScreenFrameBorders.cut(col,border[0],border[1])*16),u1=sprite.getInterpolatedU(ScreenFrameBorders.cut(col+1,border[0],border[1])*16);
            double v0=sprite.getInterpolatedV(ScreenFrameBorders.cut(row,border[2],border[3])*16/FrameAppearance.aspect(key.choice)),v1=sprite.getInterpolatedV(ScreenFrameBorders.cut(row+1,border[2],border[3])*16/FrameAppearance.aspect(key.choice));
            WorldSurface.rawQuad(b,false,x0,y0,6,u0,v1,x0,y1,6,u0,v0,x1,y1,6,u1,v0,x1,y0,6,u1,v1);
            WorldSurface.rawQuad(b,true,x0,y0,10,u0,v1,x0,y1,10,u0,v0,x1,y1,10,u1,v0,x1,y0,10,u1,v1);
        }
        concave(b,key,sprite,border);
    }
    private static void concave(BufferBuilder b,Key key,TextureAtlasSprite sprite,double[] border){
        texture(b,sprite);
        for(int corner=0;corner<4;corner++)if((key.mask&(16<<corner))!=0){
            boolean right=(corner&1)!=0,top=(corner&2)!=0;double l=border[right?1:0],h=border[top?2:3];
            double x0=right?16-l*16:0,x1=right?16:l*16,y0=top?16-h*16:0,y1=top?16:h*16;
            double u0=sprite.getInterpolatedU(right?(1-l)*16:0),u1=sprite.getInterpolatedU(right?16:l*16),v0=sprite.getInterpolatedV(top?0:(1-h)*16/FrameAppearance.aspect(key.choice)),v1=sprite.getInterpolatedV(top?h*16/FrameAppearance.aspect(key.choice):16/FrameAppearance.aspect(key.choice));
            WorldSurface.rawQuad(b,false,x0,y0,6,u0,v1,x0,y1,6,u0,v0,x1,y1,6,u1,v0,x1,y0,6,u1,v1);
            WorldSurface.rawQuad(b,true,x0,y0,10,u0,v1,x0,y1,10,u0,v0,x1,y1,10,u1,v0,x1,y0,10,u1,v1);
        }
    }
    private static void box(BufferBuilder b,Key k,double x,double y,double r,double t){
        double u0=x,u1=r,v0=16-t,v1=16-y;
        // Opaque backing remains behind authored alpha, including canvas padding.
        double inset=k.choice>=0&&FrameAppearance.border(k.choice)?.016:0;
        if(k.front!=null)quad(b,k.front,false,new double[]{x,y,6+inset,u0,v1},new double[]{x,t,6+inset,u0,v0},new double[]{r,t,6+inset,u1,v0},new double[]{r,y,6+inset,u1,v1});
        if(k.front!=null)quad(b,k.front,true,new double[]{x,y,10-inset,u0,v1},new double[]{x,t,10-inset,u0,v0},new double[]{r,t,10-inset,u1,v0},new double[]{r,y,10-inset,u1,v1});
        quad(b,y==0?k.outer:k.inner,false,new double[]{x,y,6,x,0},new double[]{r,y,6,r,0},new double[]{r,y,10,r,4},new double[]{x,y,10,x,4});
        quad(b,t==16?k.outer:k.inner,true,new double[]{x,t,6,x,0},new double[]{r,t,6,r,0},new double[]{r,t,10,r,4},new double[]{x,t,10,x,4});
        quad(b,x==0?k.outer:k.inner,true,new double[]{x,y,6,0,16-y},new double[]{x,t,6,0,16-t},new double[]{x,t,10,4,16-t},new double[]{x,y,10,4,16-y});
        quad(b,r==16?k.outer:k.inner,false,new double[]{r,y,6,0,16-y},new double[]{r,t,6,0,16-t},new double[]{r,t,10,4,16-t},new double[]{r,y,10,4,16-y});
    }
}
