package com.vandorlabs.client;

import com.vandorlabs.tiles.FrameAppearance;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.render.ScreenSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import org.lwjgl.opengl.GL11;

/** Separate authored front border; alpha leaves the underlying display untouched. */
final class FrameSurfaceRenderer {
    private FrameSurfaceRenderer(){}
    static void screen(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile,ScreenSurface.Quad quad){
        FrameAppearance frame=tile.frameAppearance;
        if(frame.front<0)return;
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(frame.front));
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        GlStateManager.alphaFunc(GL11.GL_GREATER,.01F);GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glPolygonOffset(-6,-6);
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
        GlassSurfaceGroups.Layout layout=frame.tile?new GlassSurfaceGroups.Layout(0,0,1,1):ScreenSurfaceGroups.layout(tile);
        boolean input=tile.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableInput||tile.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableHalfConsole;
        int i=0;for(ScreenSurface.Vertex v:quad.vertices){WorldSurface.vertex(b,v.x,v.y,v.z,sprite.getInterpolatedU((input?layout.column+(i==1||i==2?1:0):layout.columns-layout.column-(i==1||i==2?1:0))*16D/layout.columns),sprite.getInterpolatedV((layout.rows-layout.row-(i<2?1:0))*16D/layout.rows),quad.nx,quad.ny,quad.nz);i++;}
        Tessellator.getInstance().draw();GL11.glPolygonOffset(0,0);GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.disableBlend();
    }
}
