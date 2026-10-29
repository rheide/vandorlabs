package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.*;

/** Authored frame widths keep opaque inner sides aligned with the front opening. */
final class GlassFrameMesh {
    private static final Map<Key,StaticSurfaceMesh> CACHE=new LinkedHashMap<Key,StaticSurfaceMesh>(64,.75F,true){protected boolean removeEldestEntry(Map.Entry<Key,StaticSurfaceMesh> e){return size()>256;}};
    private static final class Key{final TextureAtlasSprite front,inner,outer;final int mask,choice,columns,rows;Key(TextureAtlasSprite f,TextureAtlasSprite i,TextureAtlasSprite o,int m,int c,int w,int h){front=f;inner=i;outer=o;mask=m;choice=c;columns=w;rows=h;}public int hashCode(){return Objects.hash(System.identityHashCode(front),System.identityHashCode(inner),System.identityHashCode(outer),mask,choice,columns,rows);}public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return front==k.front&&inner==k.inner&&outer==k.outer&&mask==k.mask&&choice==k.choice&&columns==k.columns&&rows==k.rows;}}
    static StaticSurfaceMesh get(TileEntityProgrammableGlass tile,GlassSurfaceGroups.Layout layout){
        net.minecraft.client.renderer.texture.TextureMap atlas=Minecraft.getMinecraft().getTextureMapBlocks();
        int mask=(layout.column==0?1:0)|(layout.column==layout.columns-1?2:0)|(layout.row==0?4:0)|(layout.row==layout.rows-1?8:0);
        Key key=new Key(atlas.getAtlasSprite(tile.frameAppearance.rail("vandorlabs:blocks/space_doors/medium/double_frame_metal")),atlas.getAtlasSprite(tile.frameAppearance.interior("vandorlabs:blocks/programmable_glass/metal_side")),atlas.getAtlasSprite(tile.frameAppearance.exterior("vandorlabs:blocks/programmable_glass/metal_side")),mask,tile.frameAppearance.front,layout.columns,layout.rows);
        StaticSurfaceMesh mesh=CACHE.get(key);if(mesh!=null)return mesh;StaticSurfaceMesh.Capture b=StaticSurfaceMesh.capture();
        double[] widths=ScreenFrameBorders.get(key.choice);
        double left=(mask&1)!=0?(key.choice<0?1:Math.min(16,widths[0]*layout.columns*16)):0;
        double right=(mask&2)!=0?(key.choice<0?1:Math.min(16,widths[1]*layout.columns*16)):0;
        double bottom=(mask&4)!=0?(key.choice<0?1:Math.min(16,widths[3]*layout.rows*16)):0;
        double top=(mask&8)!=0?(key.choice<0?1:Math.min(16,widths[2]*layout.rows*16)):0;
        if(left>0)box(b,key,0,0,left,16);if(right>0)box(b,key,16-right,0,16,16);
        if(bottom>0)box(b,key,left,0,16-right,bottom);if(top>0)box(b,key,left,16-top,16-right,16);
        mesh=b.finish();CACHE.put(key,mesh);return mesh;
    }
    private static void box(StaticSurfaceMesh.Capture b,Key k,double x,double y,double r,double t){
        double u0=x,u1=r,v0=16-t,v1=16-y;
        if(k.front!=null)WorldSurface.quad(b,k.front,false,new double[]{x,y,6,u0,v1},new double[]{x,t,6,u0,v0},new double[]{r,t,6,u1,v0},new double[]{r,y,6,u1,v1});
        if(k.front!=null)WorldSurface.quad(b,k.front,true,new double[]{x,y,10,u0,v1},new double[]{x,t,10,u0,v0},new double[]{r,t,10,u1,v0},new double[]{r,y,10,u1,v1});
        WorldSurface.quad(b,y==0?k.outer:k.inner,false,new double[]{x,y,6,x,0},new double[]{r,y,6,r,0},new double[]{r,y,10,r,4},new double[]{x,y,10,x,4});
        WorldSurface.quad(b,t==16?k.outer:k.inner,true,new double[]{x,t,6,x,0},new double[]{r,t,6,r,0},new double[]{r,t,10,r,4},new double[]{x,t,10,x,4});
        WorldSurface.quad(b,x==0?k.outer:k.inner,true,new double[]{x,y,6,0,16-y},new double[]{x,t,6,0,16-t},new double[]{x,t,10,4,16-t},new double[]{x,y,10,4,16-y});
        WorldSurface.quad(b,r==16?k.outer:k.inner,false,new double[]{r,y,6,0,16-y},new double[]{r,t,6,0,16-t},new double[]{r,t,10,4,16-t},new double[]{r,y,10,4,16-y});
    }
}
