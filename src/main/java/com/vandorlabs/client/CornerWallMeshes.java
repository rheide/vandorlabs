package com.vandorlabs.client;

import com.vandorlabs.render.CornerGlassGeometry;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Corner Wall meshes: the programmable wall's own straight middle slab (faces, rims and texture
 * layout as on a flat wall), without the end caps it shares with joined walls, bent onto the bevel
 * by the shared corner geometry.
 */
public final class CornerWallMeshes {
    private static final int LIMIT=512;
    private static final GlassSurfaceGroups.Layout SINGLE=new GlassSurfaceGroups.Layout(0,0,1,1);
    private static final Map<List<Object>,StaticSurfaceMesh> CACHE=new LinkedHashMap<List<Object>,StaticSurfaceMesh>(64,.75F,true){
        @Override protected boolean removeEldestEntry(Map.Entry<List<Object>,StaticSurfaceMesh> eldest){return size()>LIMIT;}
    };
    private CornerWallMeshes(){}
    public static void clear(){CACHE.clear();}

    /** {@code joined}: bit 0 the end on the corner's first side, bit 1 its second side. */
    static StaticSurfaceMesh get(TileEntityAnimatedScreenSelector tile,TextureAtlasSprite wall,TextureAtlasSprite metal,CornerGlassGeometry geometry,int joined){
        int layout=tile.getMainTextureLayout();
        List<Object> key=Arrays.asList(wall,metal,geometry,joined,layout);
        StaticSurfaceMesh found=CACHE.get(key);
        if(found!=null)return found;
        StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.independentCapture();
        TEAnimatedScreenSelector.renderFlatWall(capture,wall,metal,6,null);
        StaticSurfaceMesh mesh=capture.finish();
        if((joined&1)!=0)mesh=mesh.withoutPlane(0,0);
        if((joined&2)!=0)mesh=mesh.withoutPlane(0,16);
        // A corner shows the whole artwork on its own bevel, not a share of a joined wall group's.
        if(layout!=0)mesh=GlassSurfaceMapping.map(mesh,wall,SINGLE,false,0,0,layout);
        mesh=mesh.cornerGlass(geometry);
        CACHE.put(key,mesh);
        return mesh;
    }
}
