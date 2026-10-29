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
    static boolean suppressBorders;
    private static final java.util.Map<com.vandorlabs.tiles.TileEntityAnimatedScreenSelector,java.util.Map<ScreenSurface.Quad,Content>> CONTENT=new java.util.WeakHashMap<>();
    private static final class Content {double l,r,t,b;ScreenSurface.Quad quad;}
    static ScreenSurface.Quad content(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile,ScreenSurface.Quad quad){
        if(!tile.frameAppearance.hasFront())return quad;
        double[] b=ScreenFrameBorders.get(tile.frameAppearance.front);
        GlassSurfaceGroups.Layout layout=tile.frameAppearance.tile?new GlassSurfaceGroups.Layout(0,0,1,1):ScreenSurfaceGroups.frameLayout(tile);
        double l=layout.column==0?b[0]:0,r=layout.column==layout.columns-1?b[1]:0,t=layout.row==layout.rows-1?b[2]:0,d=layout.row==0?b[3]:0;
        java.util.Map<ScreenSurface.Quad,Content> entries=CONTENT.computeIfAbsent(tile,k->new java.util.IdentityHashMap<>());
        Content c=entries.computeIfAbsent(quad,k->new Content());
        if(c.quad==null||c.l!=l||c.r!=r||c.t!=t||c.b!=d){c.l=l;c.r=r;c.t=t;c.b=d;c.quad=ScreenSurface.inset(quad,l,r,t,d);}
        return c.quad;
    }
    static void screen(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile,ScreenSurface.Quad quad){
        FrameAppearance frame=tile.frameAppearance;
        if(!frame.hasFront()||suppressBorders)return;
        float oldBlock=OpenGlHelper.lastBrightnessX,oldSky=OpenGlHelper.lastBrightnessY;
        if(frame.light()>0)OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,oldSky);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(frame.front));
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        GlStateManager.alphaFunc(GL11.GL_GREATER,.01F);GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glPolygonOffset(-6,-6);
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
        GlassSurfaceGroups.Layout layout=frame.tile?new GlassSurfaceGroups.Layout(0,0,1,1):ScreenSurfaceGroups.frameLayout(tile);
        boolean input=tile.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableInput||tile.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableHalfConsole;
        double[] border=ScreenFrameBorders.get(frame.front);
        double l=layout.column==0?border[0]:0,r=layout.column==layout.columns-1?border[1]:0;
        double t=layout.row==layout.rows-1?border[2]:0,d=layout.row==0?border[3]:0;
        GlStateManager.disableCull();
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)if(row!=1||col!=1){
            double x0=cut(col,l,r),x1=cut(col+1,l,r),y0=cut(row,t,d),y1=cut(row+1,t,d);
            if(x1>x0&&y1>y0)region(b,quad,sprite,input,FrameAppearance.aspect(frame.front),x0,y0,x1,y1,cut(col,border[0],border[1]),cut(row,border[2],border[3]),cut(col+1,border[0],border[1]),cut(row+1,border[2],border[3]));
        }
        Tessellator.getInstance().draw();GL11.glPolygonOffset(0,0);GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.disableBlend();GlStateManager.enableCull();OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldBlock,oldSky);
    }
    private static double cut(int index,double first,double last){return index==0?0:index==1?first:index==2?1-last:1;}
    private static void region(BufferBuilder b,ScreenSurface.Quad quad,TextureAtlasSprite sprite,boolean input,double aspect,double left,double top,double right,double bottom,double u0,double v0,double u1,double v1){
        for(int i=0;i<4;i++){
            boolean rightVertex=i==1||i==2;double x=rightVertex?right:left,y=i<2?top:bottom;
            double px=quad.topLeft.x+(quad.topRight.x-quad.topLeft.x)*x+quad.nx*.06,py=quad.topLeft.y+(quad.bottomLeft.y-quad.topLeft.y)*y+quad.ny*.06,pz=quad.topLeft.z+(quad.bottomLeft.z-quad.topLeft.z)*y+quad.nz*.06;
            double u=rightVertex?u1:u0,v=i<2?v0:v1;if(!input)u=1-u;
            WorldSurface.vertex(b,px,py,pz,sprite.getInterpolatedU(u*16),sprite.getInterpolatedV(v*16/aspect),quad.nx,quad.ny,quad.nz);
        }
    }

}
