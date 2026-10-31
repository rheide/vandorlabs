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
    private static final com.google.common.cache.Cache<Key,StaticSurfaceMesh[]> CACHE=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(4*1024*1024).weigher((Key k,StaticSurfaceMesh[] v)->1024+Arrays.stream(v).mapToInt(m->m.vertexCount()*32).sum()+(k.model instanceof DoorGlassModel?((DoorGlassModel)k.model).memoryBytes():0)).build();
    static void clear(){CACHE.invalidateAll();}
    static StaticSurfaceMesh[] get(TileEntitySpaceDoor tile,IBlockState state,boolean right,int part) {
        DoorRenderModels.Entry entry=DoorRenderModels.get(state.getBlock(),tile.metadata(true,right,part));
        int choice=part==1?tile.getFaceTexture():-1;
        double scale=tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor?1.5:1;IBakedModel model=part==2?DoorGlassModel.get(entry.model,tile,scale):FrameDoorModel.applies(tile.frameAppearance,part)?entry.framed(tile.frameAppearance,scale):entry.model;
        Key key=new Key(model,right,choice,tile.getTextureLayout(),scale);
        StaticSurfaceMesh[] panels=CACHE.getIfPresent(key);if(panels!=null)return panels;
        TextureAtlasSprite sprite=choice<0?null:Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(choice));
        panels=build(model,right,choice,tile.getTextureLayout(),sprite,part==1,scale);
        CACHE.put(key,panels);
        return panels;
    }
    static StaticSurfaceMesh[] build(IBakedModel model,boolean right,int choice,boolean tiled,TextureAtlasSprite sprite,boolean caps) {
        return build(model,right,choice,tiled?1:0,sprite,caps,1);
    }
    private static StaticSurfaceMesh[] build(IBakedModel model,boolean right,int choice,int layout,TextureAtlasSprite sprite,boolean caps,double scale) {
        SelectedDoorGeometry selected=choice<0?null:new SelectedDoorGeometry(model);
        StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
        if(selected!=null){double aspect=UnifiedTextureSprites.aspect(sprite);boolean fit=com.vandorlabs.render.TextureLayoutMode.fit(layout,(selected.bounds[3]-selected.bounds[0])*scale,(selected.bounds[4]-selected.bounds[1])*scale,aspect);if(fit)SelectedDoorFaceCache.emit(capture,selected.bounds,sprite,choice,false,0);else DoorFaceTextureMesh.draw(capture,sprite,selected.bounds,0,aspect,scale);}
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
        final IBakedModel model;final boolean right;final int choice,layout;final double scale;
        Key(IBakedModel model,boolean right,int choice,int layout,double scale){this.model=model;this.right=right;this.choice=choice;this.layout=layout;this.scale=scale;}
        public int hashCode(){return Objects.hash(System.identityHashCode(model),right,choice,layout,scale);}
        public boolean equals(Object other){if(!(other instanceof Key))return false;Key k=(Key)other;return model==k.model&&right==k.right&&choice==k.choice&&layout==k.layout&&scale==k.scale;}
    }
}
