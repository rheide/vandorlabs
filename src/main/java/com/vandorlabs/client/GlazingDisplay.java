package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;

/** Reuses mapped glazing meshes; only UVs change as a display advances. */
final class GlazingDisplay {
    static int material(TileEntity tile){return tile instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)tile).getMaterial():((TileEntityAnimatedScreenSelector)tile).getGlassMaterial();}
    /** Only the static atlas image can share a draw with other panes. */
    static boolean atlasStatic(TileEntity tile){
        int material=material(tile);
        return material<0||!"Screens".equals(ScreenHousingTextures.category(material))||GlassPlayback.of(tile).mode()==1;
    }
    static void draw(StaticSurfaceMesh mesh,TileEntity tile,BufferBuilder buffer,int sky,int block,float opacity){
        int material=material(tile);GlassPlayback playback=GlassPlayback.of(tile);
        if(atlasStatic(tile)){mesh.drawColored(buffer,sky,block,1,1,1,opacity);return;}
        TextureAtlasSprite source=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(material));
        double[] uv=TEAnimatedScreenSelector.bindDisplay(tile,material,playback.mode()!=0,playback.mode()==2,playback.speed());
        mesh.drawColoredMapped(buffer,sky,block,opacity,source,uv);
    }
}
