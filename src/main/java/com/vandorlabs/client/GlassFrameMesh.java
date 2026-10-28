package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.*;

/** Native one-pixel rails keep opaque inner sides when front artwork has alpha. */
final class GlassFrameMesh {
    private static final Map<Key,StaticSurfaceMesh> CACHE=new LinkedHashMap<Key,StaticSurfaceMesh>(64,.75F,true){protected boolean removeEldestEntry(Map.Entry<Key,StaticSurfaceMesh> e){return size()>256;}};
    private static final class Key{final TextureAtlasSprite front,inner;final int mask;Key(TextureAtlasSprite f,TextureAtlasSprite i,int m){front=f;inner=i;mask=m;}public int hashCode(){return Objects.hash(System.identityHashCode(front),System.identityHashCode(inner),mask);}public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return front==k.front&&inner==k.inner&&mask==k.mask;}}
    static StaticSurfaceMesh get(TileEntityProgrammableGlass tile,GlassSurfaceGroups.Layout layout){
        net.minecraft.client.renderer.texture.TextureMap atlas=Minecraft.getMinecraft().getTextureMapBlocks();
        int mask=(layout.column==0?1:0)|(layout.column==layout.columns-1?2:0)|(layout.row==0?4:0)|(layout.row==layout.rows-1?8:0);
        Key key=new Key(atlas.getAtlasSprite(tile.frameAppearance.rail("vandorlabs:blocks/space_doors/medium/double_frame_metal")),atlas.getAtlasSprite(tile.frameAppearance.interior("vandorlabs:blocks/programmable_glass/metal_side")),mask);
        StaticSurfaceMesh mesh=CACHE.get(key);if(mesh!=null)return mesh;StaticSurfaceMesh.Capture b=StaticSurfaceMesh.capture();
        if((mask&1)!=0)box(b,key,0,0,1,16);if((mask&2)!=0)box(b,key,15,0,16,16);if((mask&4)!=0)box(b,key,(mask&1)!=0?1:0,0,(mask&2)!=0?15:16,1);if((mask&8)!=0)box(b,key,(mask&1)!=0?1:0,15,(mask&2)!=0?15:16,16);
        mesh=b.finish();CACHE.put(key,mesh);return mesh;
    }
    private static void box(StaticSurfaceMesh.Capture b,Key k,double x,double y,double r,double t){
        boolean original=k.front!=null&&k.front.getIconName().endsWith("/double_frame_metal");
        double u0=original?x:0,u1=original?r:16,v0=original?16-t:0,v1=original?16-y:16;
        if(k.front!=null)WorldSurface.quad(b,k.front,false,new double[]{x,y,6,u0,v1},new double[]{x,t,6,u0,v0},new double[]{r,t,6,u1,v0},new double[]{r,y,6,u1,v1});
        if(k.front!=null)WorldSurface.quad(b,k.front,true,new double[]{x,y,10,u0,v1},new double[]{x,t,10,u0,v0},new double[]{r,t,10,u1,v0},new double[]{r,y,10,u1,v1});
        WorldSurface.quad(b,k.inner,false,new double[]{x,y,6,x,0},new double[]{r,y,6,r,0},new double[]{r,y,10,r,4},new double[]{x,y,10,x,4});
        WorldSurface.quad(b,k.inner,true,new double[]{x,t,6,x,0},new double[]{r,t,6,r,0},new double[]{r,t,10,r,4},new double[]{x,t,10,x,4});
        WorldSurface.quad(b,k.inner,true,new double[]{x,y,6,0,16-y},new double[]{x,t,6,0,16-t},new double[]{x,t,10,4,16-t},new double[]{x,y,10,4,16-y});
        WorldSurface.quad(b,k.inner,false,new double[]{r,y,6,0,16-y},new double[]{r,t,6,0,16-t},new double[]{r,t,10,4,16-t},new double[]{r,y,10,4,16-y});
    }
}
