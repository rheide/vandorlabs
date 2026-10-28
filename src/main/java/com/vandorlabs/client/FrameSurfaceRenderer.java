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
        if(FrameAppearance.border(frame.front))region(b,quad,sprite,layout,input,0,0,1,1);
        else{
            if(layout.column==0)region(b,quad,sprite,layout,input,0,0,1D/16,1);
            if(layout.column==layout.columns-1)region(b,quad,sprite,layout,input,15D/16,0,1,1);
            if(layout.row==0)region(b,quad,sprite,layout,input,0,15D/16,1,1);
            if(layout.row==layout.rows-1)region(b,quad,sprite,layout,input,0,0,1,1D/16);
        }
        Tessellator.getInstance().draw();GL11.glPolygonOffset(0,0);GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.disableBlend();
    }
    private static void region(BufferBuilder b,ScreenSurface.Quad quad,TextureAtlasSprite sprite,GlassSurfaceGroups.Layout layout,boolean input,double left,double top,double right,double bottom){
        for(int i=0;i<4;i++){
            double x=i==1||i==2?right:left,y=i<2?top:bottom;
            double px=quad.topLeft.x+(quad.topRight.x-quad.topLeft.x)*x,py=quad.topLeft.y+(quad.bottomLeft.y-quad.topLeft.y)*y,pz=quad.topLeft.z+(quad.bottomLeft.z-quad.topLeft.z)*y;
            double u=(input?layout.column+x:layout.columns-layout.column-x)/layout.columns,v=(layout.rows-layout.row-1+y)/layout.rows;
            WorldSurface.vertex(b,px,py,pz,sprite.getInterpolatedU(u*16),sprite.getInterpolatedV(v*16),quad.nx,quad.ny,quad.nz);
        }
    }

}
