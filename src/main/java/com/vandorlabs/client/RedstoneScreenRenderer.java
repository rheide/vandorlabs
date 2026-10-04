package com.vandorlabs.client;

import com.vandorlabs.blocks.RedstoneScreenInteractions;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.tiles.TileEntityRedstoneScreen;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;

/** Text and buttons share an affine 128-pixel display plane with hit testing. */
final class RedstoneScreenRenderer {
    private static final FloatBuffer FLAT=matrix(ScreenSurface.quad(ScreenSurface.Kind.FLAT,false));
    private static final FloatBuffer DIAGONAL=matrix(ScreenSurface.quad(ScreenSurface.Kind.DIAGONAL,false));
    private static final FloatBuffer INVERTED=matrix(ScreenSurface.quad(ScreenSurface.Kind.DIAGONAL,true));
    private static FloatBuffer matrix(ScreenSurface.Quad q){
        FloatBuffer b=BufferUtils.createFloatBuffer(16);
        b.put(new float[]{(float)(q.topLeft.x-q.topRight.x)/128,0,0,0,
            0,(float)(q.bottomRight.y-q.topRight.y)/128,(float)(q.bottomRight.z-q.topRight.z)/128,0,
            0,q.ny,q.nz,0,(float)q.topRight.x,(float)q.topRight.y+q.ny*.02F,(float)q.topRight.z+q.nz*.02F,1});b.flip();return b;
    }
    static void draw(TileEntityRedstoneScreen tile,IBlockState state){
        ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state);
        FloatBuffer transform=q==ScreenSurface.quad(ScreenSurface.Kind.FLAT,false)?FLAT:
            q==ScreenSurface.quad(ScreenSurface.Kind.DIAGONAL,false)?DIAGONAL:INVERTED;
        GlStateManager.pushMatrix();transform.rewind();GlStateManager.multMatrix(transform);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        // Submit every solid panel together, before the font pass. Small depth
        // layers prevent coplanar text/background flicker at oblique angles.
        GlStateManager.disableTexture2D();
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();
        buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        rect(buffer,0,0,128,128,0,0xFF0A141D);rect(buffer,3,3,125,20,.01,0xFF23465A);
        for(int i=0;i<tile.rows().size();i++){
            TileEntityRedstoneScreen.Row row=tile.rows().get(i);int y=RedstoneScreenInteractions.ROW_TOP+i*RedstoneScreenInteractions.ROW_HEIGHT;
            rect(buffer,6,y,122,y+10,.01,row.active()?0xFF174C3B:0xFF1C2B37);
            rect(buffer,98,y,120,y+10,.02,row.active()?0xFF30C58A:0xFF45576A);
        }
        Tessellator.getInstance().draw();GlStateManager.enableTexture2D();
        FontRenderer font=Minecraft.getMinecraft().fontRenderer;
        text(font,"REDSTONE CONTROL",8,8,0xC8EEFF);
        if(tile.rows().isEmpty()){
            text(font,"No controls",8,40,0xB8C8D0);
            text(font,"Configure to add",8,54,0x748C9C);
        }
        for(int i=0;i<tile.rows().size();i++){
            TileEntityRedstoneScreen.Row row=tile.rows().get(i);int y=RedstoneScreenInteractions.ROW_TOP+i*RedstoneScreenInteractions.ROW_HEIGHT;
            text(font,font.getStringWidth(row.label)<=86?row.label:font.trimStringToWidth(row.label,86),8,y+1,row.active()?0xDEFFF0:0xB8C8D8);
            text(font,row.active()?"ON":"OFF",100,y+1,row.active()?0x082419:0xE0E8EF);
        }
        GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();GlStateManager.popMatrix();
    }
    private static void rect(BufferBuilder b,int left,int top,int right,int bottom,double z,int color){
        int r=color>>16&255,g=color>>8&255,blue=color&255,a=color>>>24;
        b.pos(left,bottom,z).color(r,g,blue,a).endVertex();b.pos(right,bottom,z).color(r,g,blue,a).endVertex();
        b.pos(right,top,z).color(r,g,blue,a).endVertex();b.pos(left,top,z).color(r,g,blue,a).endVertex();
    }
    private static void text(FontRenderer font,String value,int x,int y,int color){
        GlStateManager.pushMatrix();GlStateManager.translate(0,0,.04F);
        font.drawString(value,x,y,color);GlStateManager.popMatrix();
    }
    private RedstoneScreenRenderer(){}
}
