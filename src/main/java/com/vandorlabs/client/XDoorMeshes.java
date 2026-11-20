package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntitySpaceDoor;
import com.vandorlabs.render.DoorPanelSplit;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import java.util.*;

/** Cached split panels for native artwork, panes and replacement/tiled materials. */
final class XDoorMeshes {
    private static final com.google.common.cache.Cache<Key,StaticSurfaceMesh[]> CACHE=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(4*1024*1024).weigher((Key k,StaticSurfaceMesh[] v)->1024+Arrays.stream(v).mapToInt(m->m.vertexCount()*32).sum()+(k.model instanceof DoorGlassModel?((DoorGlassModel)k.model).memoryBytes():k.model instanceof StaticSurfaceMesh?((StaticSurfaceMesh)k.model).vertexCount()*32:0)).build();
    static void clear(){CACHE.invalidateAll();}
    static StaticSurfaceMesh[] get(TileEntitySpaceDoor tile,IBlockState state,boolean right,int part) {
        DoorPanelSplit split=tile.panelSplit();
        if(split==null)split=DoorPanelSplit.DIAGONAL;
        if((part==1||part==2)&&com.vandorlabs.tiles.ScreenHousingTextures.reactorArtwork(tile.getFaceTexture())){
            StaticSurfaceMesh mesh=ReactorDoorGlazing.get(DoorRenderModels.get(state.getBlock(),tile.metadata(true,right,1)).selected(),
                    tile.glassAppearance(),part==2,com.vandorlabs.tiles.ScreenHousingTextures.doorArtworkMirror(tile.getFaceTexture(),right),1.5);
            return splitMesh(mesh,right,part==1,split);
        }
        if(part==2&&tile.hasCompanyGlass()){
            StaticSurfaceMesh mesh=CompanyDoorGlazing.mesh(DoorRenderModels.get(state.getBlock(),tile.metadata(true,right,1)).selected());
            return splitMesh(mesh,right,false,split);
        }
        DoorRenderModels.Entry entry=DoorRenderModels.get(state.getBlock(),tile.metadata(true,right,part));
        int choice=part==1?tile.getFaceTexture():-1;
        double scale=tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor?1.5:1;IBakedModel model=part==2?DoorGlassModel.get(entry.model,tile,scale):FrameDoorModel.applies(tile.frameAppearance,part)?entry.framed(tile.frameAppearance,scale):entry.model;
        boolean mirror=com.vandorlabs.tiles.ScreenHousingTextures.doorArtworkMirror(choice,right&&(state.getValue(com.vandorlabs.blocks.BlockConnectingDetailedDoor.PAIRED)||scale>1));
        double scaleY=scale;
        if(tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor){
            com.vandorlabs.render.LargeDoorJoin.Group<?> group=((com.vandorlabs.tiles.TileEntityLargeProgrammableDoor)tile).joinedGroup();
            scale*=group.columns;scaleY*=group.rows;
        }
        Key key=new Key(model,right,choice,tile.getTextureLayout(),scale,scaleY,mirror,part==1,split);
        StaticSurfaceMesh[] panels=CACHE.getIfPresent(key);if(panels!=null)return panels;
        TextureAtlasSprite sprite=choice<0?null:Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(choice));
        panels=build(model,right,choice,tile.getTextureLayout(),sprite,part==1,scale,scaleY,mirror,split);
        CACHE.put(key,panels);
        return panels;
    }
    private static StaticSurfaceMesh[] splitMesh(StaticSurfaceMesh mesh,boolean right,boolean caps,DoorPanelSplit split){
        Key key=new Key(mesh,right,-1,0,1,false,caps,split);
        StaticSurfaceMesh[] panels=CACHE.getIfPresent(key);
        if(panels==null){
            panels=mesh.splitPanels(split,right?1:0,caps);
            CACHE.put(key,panels);
        }
        return panels;
    }
    static StaticSurfaceMesh[] build(IBakedModel model,boolean right,int choice,boolean tiled,TextureAtlasSprite sprite,boolean caps) {
        return build(model,right,choice,tiled?1:0,sprite,caps,1,1,false,DoorPanelSplit.DIAGONAL);
    }
    private static StaticSurfaceMesh[] build(IBakedModel model,boolean right,int choice,int layout,TextureAtlasSprite sprite,boolean caps,double scale,double scaleY,boolean mirror,DoorPanelSplit split) {
        SelectedDoorGeometry selected=choice<0?null:new SelectedDoorGeometry(model);
        StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
        if(selected!=null){double aspect=UnifiedTextureSprites.aspect(sprite);boolean fit=com.vandorlabs.render.TextureLayoutMode.fit(layout,(selected.bounds[3]-selected.bounds[0])*scale,(selected.bounds[4]-selected.bounds[1])*scaleY,aspect);if(fit)SelectedDoorFaceCache.emit(capture,selected.bounds,sprite,choice,false,0);else DoorFaceTextureMesh.draw(capture,sprite,selected.bounds,0,aspect,scale,scaleY);}
        if(selected!=null&&mirror){StaticSurfaceMesh mirrored=capture.finish().mirrorX(selected.bounds[0]+selected.bounds[3]);capture=StaticSurfaceMesh.capture();mirrored.draw(capture,0,0);}
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
        StaticSurfaceMesh full=capture.finish();return full.splitPanels(split,right?1:0,caps);
    }
    private static final class Key {
        final Object model;
        final boolean right,mirror,caps;
        final int choice,layout;
        final double scale,scaleY;
        final DoorPanelSplit split;
        Key(Object model,boolean right,int choice,int layout,double scale,boolean mirror,boolean caps,DoorPanelSplit split){this(model,right,choice,layout,scale,scale,mirror,caps,split);}
        Key(Object model,boolean right,int choice,int layout,double scale,double scaleY,boolean mirror,boolean caps,DoorPanelSplit split){this.scaleY=scaleY;
            this.model=model;this.right=right;this.choice=choice;this.layout=layout;this.scale=scale;this.mirror=mirror;this.caps=caps;this.split=split;
        }
        public int hashCode(){return Objects.hash(System.identityHashCode(model),right,choice,layout,scale,mirror,caps,split);}
        public boolean equals(Object other){
            if(!(other instanceof Key))return false;
            Key k=(Key)other;
            return model==k.model&&right==k.right&&choice==k.choice&&layout==k.layout&&scale==k.scale&&scaleY==k.scaleY&&mirror==k.mirror&&caps==k.caps&&split==k.split;
        }
    }
}
