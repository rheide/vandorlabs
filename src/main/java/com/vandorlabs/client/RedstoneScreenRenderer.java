package com.vandorlabs.client;

import com.vandorlabs.blocks.RedstoneScreenInteractions;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.tiles.RedstoneScreenContents;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;

/** Text and buttons share an affine 128-pixel display plane with hit testing. */
final class RedstoneScreenRenderer {
    private static final java.util.Map<ScreenSurface.Quad,FloatBuffer> MATRICES=new java.util.IdentityHashMap<>();
    private static FloatBuffer matrix(ScreenSurface.Quad q,int height){
        FloatBuffer b=BufferUtils.createFloatBuffer(16);
        b.put(new float[]{(float)(q.topLeft.x-q.topRight.x)/128,0,0,0,
            0,(float)(q.bottomRight.y-q.topRight.y)/height,(float)(q.bottomRight.z-q.topRight.z)/height,0,
            0,q.ny,q.nz,0,(float)q.topRight.x,(float)q.topRight.y+q.ny*.02F,(float)q.topRight.z+q.nz*.02F,1});b.flip();return b;
    }
    static void draw(RedstoneScreenContents tile,IBlockState state){
        ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state,tile.tile(),tile.slot());
        int height=RedstoneScreenInteractions.displayHeight(state.getBlock(),tile.slot()),count=Math.min(tile.rows().size(),tile.maxRows());
        FloatBuffer transform=MATRICES.get(q);
        if(transform==null){transform=matrix(q,height);MATRICES.put(q,transform);}
        int top=RedstoneScreenInteractions.rowTop(state.getBlock(),tile.slot());
        boolean header=!RedstoneScreenInteractions.half(state.getBlock(),tile.slot());
        GlStateManager.pushMatrix();transform.rewind();GlStateManager.multMatrix(transform);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        // Submit every solid panel together, before the font pass. Small depth
        // layers prevent coplanar text/background flicker at oblique angles.
        GlStateManager.enableTexture2D();
        Minecraft.getMinecraft().getTextureManager().bindTexture(
                new net.minecraft.util.ResourceLocation("vandorlabs","textures/blocks/screen_white.png"));
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();
        buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,BlockSurfaceFormat.get());
        rect(buffer,0,0,128,height,0,0xFF0A141D);if(header)rect(buffer,3,3,125,20,.01,0xFF23465A);
        for(int i=0;i<count;i++){
            RedstoneScreenContents.Row row=tile.rows().get(i);int y=top+i*RedstoneScreenInteractions.ROW_HEIGHT;
            rect(buffer,RedstoneScreenInteractions.ROW_LEFT,y,RedstoneScreenInteractions.ROW_RIGHT,y+10,.01,row.active()?0xFF174C3B:0xFF1C2B37);
            if(row.slider){
                for(int segment=0;segment<row.segments();segment++){
                    int left=42+78*segment/row.segments(),right=42+78*(segment+1)/row.segments();
                    rect(buffer,left,y,right,y+10,.02,0xFF91B5C8);
                    rect(buffer,left+1,y+1,right-1,y+9,.03,row.level()>=row.segmentValue(segment)?0xFF30C58A:0xFF22394A);
                }
            }else rect(buffer,98,y,120,y+10,.02,row.active()?0xFF30C58A:0xFF45576A);
        }
        Tessellator.getInstance().draw();GlStateManager.enableTexture2D();
        FontRenderer font=Minecraft.getMinecraft().fontRenderer;
        if(header)text(font,font.trimStringToWidth(tile.title(),RedstoneScreenText.TITLE_WIDTH),8,8,0xC8EEFF);
        if(tile.rows().isEmpty()){
            text(font,"No controls",8,40,0xB8C8D0);
            text(font,"Configure to add",8,54,0x748C9C);
        }
        for(int i=0;i<count;i++){
            RedstoneScreenContents.Row row=tile.rows().get(i);int y=top+i*RedstoneScreenInteractions.ROW_HEIGHT;
            text(font,font.trimStringToWidth(row.label,row.slider?32:RedstoneScreenText.LABEL_WIDTH),8,y+1,row.active()?0xDEFFF0:0xB8C8D8);
            if(row.slider)for(int segment=0;segment<row.segments();segment++){
                String value=Integer.toString(row.segmentValue(segment));
                int center=42+78*(2*segment+1)/(2*row.segments());
                GlStateManager.pushMatrix();GlStateManager.translate(center,y+2,.04F);GlStateManager.scale(.65F,.65F,1);
                font.drawString(value,-font.getStringWidth(value)/2,0,0xE0FFF0);GlStateManager.popMatrix();
            }else text(font,row.active()?"ON":"OFF",100,y+1,row.active()?0x082419:0xE0E8EF);
        }
        GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();GlStateManager.popMatrix();
    }
    private static void rect(BufferBuilder b,int left,int top,int right,int bottom,double z,int color){
        int r=color>>16&255,g=color>>8&255,blue=color&255,a=color>>>24;
        b.pos(left,bottom,z).color(r,g,blue,a).tex(.5,.5).lightmap(240,240).normal(0,0,1).endVertex();b.pos(right,bottom,z).color(r,g,blue,a).tex(.5,.5).lightmap(240,240).normal(0,0,1).endVertex();
        b.pos(right,top,z).color(r,g,blue,a).tex(.5,.5).lightmap(240,240).normal(0,0,1).endVertex();b.pos(left,top,z).color(r,g,blue,a).tex(.5,.5).lightmap(240,240).normal(0,0,1).endVertex();
    }
    private static void text(FontRenderer font,String value,int x,int y,int color){
        GlStateManager.pushMatrix();GlStateManager.translate(0,0,.04F);
        font.drawString(value,x,y,color);GlStateManager.popMatrix();
    }
    private RedstoneScreenRenderer(){}
}
