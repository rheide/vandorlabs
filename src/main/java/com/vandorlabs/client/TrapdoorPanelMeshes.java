package com.vandorlabs.client;

import com.vandorlabs.render.PanelMotion;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.*;

/** Cache atlas-space cuts; animate by shifting vertices without rebuilding artwork. */
final class TrapdoorPanelMeshes {
    private static final Map<TileEntityProgrammableTrapdoor,Entry> CACHE=new WeakHashMap<>();
    static void clear(){CACHE.clear();}
    static void draw(BufferBuilder buffer,TextureAtlasSprite sprite,TileEntityProgrammableTrapdoor tile,IBlockState state,double pose,int light) {
        PanelMotion motion=tile.panelMotion(state);Entry entry=CACHE.get(tile);
        int key=31*(31*tile.getHousingTexture()+tile.getPosition())+(tile.isTileTexture()?1:0);
        if(entry==null || !entry.motion.equals(motion) || entry.key!=key || entry.sprite!=sprite || entry.state!=state) {
            StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
            TEProgrammableTrapdoor.drawConfiguredLeafRaw(capture,sprite,tile,state,0,light);
            StaticSurfaceMesh full=capture.finish();double[][] closed=TrapdoorPanelMotion.closed(tile,state);
            int end=tile instanceof TileEntityProgrammableDiagonalTrapdoor && tile.getPosition()!=2?4:2;
            entry=new Entry(motion,key,sprite,state,full.panels(motion,closed,end));CACHE.put(tile,entry);
        }
        for(int panel=0;panel<entry.meshes.length;panel++) {
            double[] shift=motion.shift(panel,pose);buffer.setTranslation(shift[0],shift[1],shift[2]);
            entry.meshes[panel].draw(buffer,light>>>16,light&65535);
        }
        buffer.setTranslation(0,0,0);
    }
    private static final class Entry {
        final PanelMotion motion;final int key;final TextureAtlasSprite sprite;final IBlockState state;final StaticSurfaceMesh[] meshes;
        Entry(PanelMotion motion,int key,TextureAtlasSprite sprite,IBlockState state,StaticSurfaceMesh[] meshes){this.motion=motion;this.key=key;this.sprite=sprite;this.state=state;this.meshes=meshes;}
    }
}
