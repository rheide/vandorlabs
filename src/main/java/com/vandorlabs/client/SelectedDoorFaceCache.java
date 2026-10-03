package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native leaf artwork reused across animation poses; discarded with model/atlas reloads. */
final class SelectedDoorFaceCache {
    private static final int LIMIT=256;
    private static final Map<Key,StaticSurfaceMesh> MESHES=new LinkedHashMap<>(64,.75F,true);
    private SelectedDoorFaceCache() { }
    static void clear() { MESHES.clear(); }
    static StaticSurfaceMesh get(SelectedDoorGeometry geometry,int choice,boolean tiled) {
        Key key=new Key(geometry,choice,tiled);StaticSurfaceMesh cached=MESHES.get(key);
        if(cached!=null)return cached;
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(choice));
        StaticSurfaceMesh mesh=build(geometry.bounds,sprite,choice,tiled);
        MESHES.put(key,mesh);if(MESHES.size()>LIMIT)MESHES.remove(MESHES.keySet().iterator().next());
        return mesh;
    }
    static StaticSurfaceMesh build(double[] bounds,TextureAtlasSprite sprite,int choice,boolean tiled) {
        StaticSurfaceMesh.Capture buffer=StaticSurfaceMesh.capture();
        emit(buffer,bounds,sprite,choice,tiled,0);
        return buffer.finish();
    }
    static void emit(net.minecraft.client.renderer.BufferBuilder buffer,double[] bounds,TextureAtlasSprite sprite,int choice,boolean tiled,int light) {
        if(tiled && !ScreenHousingTextures.isDoor(choice))DoorFaceTextureMesh.draw(buffer,sprite,bounds,light);
        else {
            double[][] points=new double[8][3],uv=new double[8][3];
            for(int i=0;i<8;i++) {
                points[i]=new double[]{bounds[(i&1)==0?0:3],bounds[(i&2)==0?1:4],bounds[(i&4)==0?2:5]};
                uv[i]=new double[]{(i&1)==0?0:1,(i&2)==0?1:0,(i&4)==0?0:1};
            }
            TEProgrammableTrapdoor.drawMaterialMesh(buffer,sprite,points,uv,light,choice,2,4);
        }
    }
    private static final class Key {
        final SelectedDoorGeometry geometry;
        final int choice;
        final boolean tiled;
        Key(SelectedDoorGeometry geometry,int choice,boolean tiled) { this.geometry=geometry;this.choice=choice;this.tiled=tiled; }
        @Override public int hashCode() { return 31*(31*System.identityHashCode(geometry)+choice)+(tiled?1:0); }
        @Override public boolean equals(Object value) {
            if(!(value instanceof Key))return false;Key other=(Key)value;
            return geometry==other.geometry && choice==other.choice && tiled==other.tiled;
        }
    }
}
