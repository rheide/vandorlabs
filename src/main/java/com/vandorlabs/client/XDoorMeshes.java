package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import java.util.*;

/** Cached diagonal panels for native artwork, panes and replacement/tiled materials. */
final class XDoorMeshes {
    private static final Map<Key,StaticSurfaceMesh[]> CACHE=new LinkedHashMap<>(64,.75F,true);
    static void clear(){CACHE.clear();}
    static StaticSurfaceMesh[] get(TileEntitySpaceDoor tile,IBlockState state,boolean right,int part) {
        DoorRenderModels.Entry entry=DoorRenderModels.get(state.getBlock(),tile.metadata(true,right,part));
        int choice=part==1?tile.getFaceTexture():-1;
        Key key=new Key(entry,right,choice,tile.isTileTexture());
        StaticSurfaceMesh[] panels=CACHE.get(key);if(panels!=null)return panels;
        TextureAtlasSprite sprite=choice<0?null:Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(choice));
        panels=build(entry.model,right,choice,tile.isTileTexture(),sprite,part==1);
        CACHE.put(key,panels);if(CACHE.size()>256)CACHE.remove(CACHE.keySet().iterator().next());
        return panels;
    }
    static StaticSurfaceMesh[] build(IBakedModel model,boolean right,int choice,boolean tiled,TextureAtlasSprite sprite,boolean caps) {
        SelectedDoorGeometry selected=choice<0?null:new SelectedDoorGeometry(model);
        StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
        if(selected!=null)SelectedDoorFaceCache.emit(capture,selected.bounds,sprite,choice,tiled,0);
        IBakedModel source=selected==null?model:selected;
        for(int side=-1;side<6;side++)for(BakedQuad quad:source.getQuads(null,side<0?null:EnumFacing.getFront(side),42)) {
            int[] data=quad.getVertexData();int stride=data.length/4;
            for(int v=0;v<4;v++) {
                int offset=v*stride;EnumFacing normal=quad.getFace();
                capture.pos(Float.intBitsToFloat(data[offset]),Float.intBitsToFloat(data[offset+1]),Float.intBitsToFloat(data[offset+2]))
                        .color(255,255,255,255).tex(Float.intBitsToFloat(data[offset+4]),Float.intBitsToFloat(data[offset+5]))
                        .normal(normal.getFrontOffsetX(),normal.getFrontOffsetY(),normal.getFrontOffsetZ()).endVertex();
            }
        }
        StaticSurfaceMesh full=capture.finish();return full.xPanels(right?1:0,caps);
    }
    private static final class Key {
        final DoorRenderModels.Entry entry;final boolean right,tiled;final int choice;
        Key(DoorRenderModels.Entry entry,boolean right,int choice,boolean tiled){this.entry=entry;this.right=right;this.choice=choice;this.tiled=tiled;}
        @Override public int hashCode(){return 31*(31*System.identityHashCode(entry)+choice)+(right?2:0)+(tiled?1:0);}
        @Override public boolean equals(Object other){if(!(other instanceof Key))return false;Key k=(Key)other;return entry==k.entry && right==k.right && choice==k.choice && tiled==k.tiled;}
    }
}
