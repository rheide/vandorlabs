package com.vandorlabs.client;

import com.vandorlabs.programmer.*;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.util.EnumFacing;
import org.lwjgl.BufferUtils;

/** Physical journal uses the same batched solid controls and font pass as Redstone screens. */
public final class ProgrammerJournalRenderer {
    private static final java.util.Map<EnumFacing,FloatBuffer> MATRICES=new java.util.EnumMap<>(EnumFacing.class);
    private static FloatBuffer matrix(EnumFacing facing){return MATRICES.computeIfAbsent(facing,f->{
        ProgrammerTransform.Affine a=ProgrammerGeometry.transform(f);net.minecraft.util.math.Vec3d across=ProgrammerJournalSurface.ACROSS.scale(1D/ProgrammerJournalSurface.WIDTH),down=ProgrammerJournalSurface.DOWN.scale(1D/ProgrammerJournalSurface.HEIGHT),n=ProgrammerJournalSurface.NORMAL,origin=ProgrammerJournalSurface.ORIGIN.add(n.scale(.001));
        FloatBuffer b=BufferUtils.createFloatBuffer(16);double depth=Math.sqrt(across.lengthSquared());b.put(new float[]{
            (float)(a.xx*across.x+a.xz*across.z),(float)across.y,(float)(a.zx*across.x+a.zz*across.z),0,
            (float)(a.xx*down.x+a.xz*down.z),(float)down.y,(float)(a.zx*down.x+a.zz*down.z),0,
            (float)((a.xx*n.x+a.xz*n.z)*depth),(float)(n.y*depth),(float)((a.zx*n.x+a.zz*n.z)*depth),0,
            (float)a.x(origin.x,origin.z),(float)origin.y,(float)a.z(origin.x,origin.z),1});b.flip();return b;
    });}
    public static void draw(ProgrammerTile tile,double x,double y,double z){
        ProgrammerJournalView view=tile.journalDisplay();if(view==null)return;int page=tile.journalDisplayPage();
        GlStateManager.pushMatrix();try{
            GlStateManager.translate(x,y,z);FloatBuffer transform=matrix(tile.getProgrammerFacing());transform.rewind();GlStateManager.multMatrix(transform);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);RedstoneScreenRenderer.bindWhite();
            BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,BlockSurfaceFormat.get());
            RedstoneScreenRenderer.rect(buffer,0,0,320,136,0,0xff000000);RedstoneScreenRenderer.rect(buffer,3,3,317,20,.01,0xff152330);
            RedstoneScreenRenderer.rect(buffer,ProgrammerJournalSurface.PREVIOUS_LEFT,116,ProgrammerJournalSurface.PREVIOUS_RIGHT,132,.01,page>0?0xff23465a:0xff121b23);
            RedstoneScreenRenderer.rect(buffer,ProgrammerJournalSurface.NEXT_LEFT,116,ProgrammerJournalSurface.NEXT_RIGHT,132,.01,page+1<view.pages.size()?0xff23465a:0xff121b23);
            Tessellator.getInstance().draw();FontRenderer font=Minecraft.getMinecraft().fontRenderer;
            fitted(font,view.title.isEmpty()?"Untitled Journal":view.title,7,8,306,0xc8eeff);
            java.util.List<String> lines=view.pages.get(page).lines;for(int i=0;i<lines.size();i++)fitted(font,lines.get(i),8,23+i*9,304,0xe2edf5);
            RedstoneScreenRenderer.text(font,"< Previous",12,120,page>0?0xc8eeff:0x697987);RedstoneScreenRenderer.text(font,"Next >",270,120,page+1<view.pages.size()?0xc8eeff:0x697987);
            String counter=view.source+"  "+(page+1)+" / "+view.pages.size();RedstoneScreenRenderer.text(font,counter,(320-font.getStringWidth(counter))/2,120,0xa6bfd0);
            GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();
        }finally{GlStateManager.popMatrix();}
    }
    private static void fitted(FontRenderer font,String text,int x,int y,int width,int color){
        int measured=font.getStringWidth(text);if(measured<=width){RedstoneScreenRenderer.text(font,text,x,y,color);return;}
        GlStateManager.pushMatrix();try{GlStateManager.translate(x,y,0);float scale=width/(float)measured;GlStateManager.scale(scale,scale,1);RedstoneScreenRenderer.text(font,text,0,0,color);}finally{GlStateManager.popMatrix();}
    }
    private ProgrammerJournalRenderer(){}
}
