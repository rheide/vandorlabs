package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

/** Actual GPU submission with vanilla and shader-sized records; no shader pack is activated. */
final class VehicleVertexFormatRuntimeChecks {
    static VertexFormat format(boolean extended){
        VertexFormat f=new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F).addElement(DefaultVertexFormats.COLOR_4UB).addElement(DefaultVertexFormats.TEX_2F).addElement(DefaultVertexFormats.TEX_2S);
        if(extended)f.addElement(DefaultVertexFormats.NORMAL_3B).addElement(DefaultVertexFormats.PADDING_1B).addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.PADDING,6));
        return f;
    }
    static void run(Minecraft mc,File output)throws Exception{
        VertexFormat vanilla=format(false),extended=format(true),snapshot=new VertexFormat(vanilla);vanilla.clear();for(VertexFormatElement e:extended.getElements())vanilla.addElement(e);
        require(snapshot.getNextOffset()==28&&!snapshot.equals(vanilla),"cached format does not follow global mutation");
        BufferedImage reference=capture(mc,snapshot,false),wide=capture(mc,extended,false),old=capture(mc,extended,true);
        require(same(reference,wide),"28/56-byte VBO output differs");require(!same(reference,old),"negative control failed to reproduce hard-coded stride corruption");
        javax.imageio.ImageIO.write(reference,"png",new File(output,"vehicle-vbo-vanilla.png"));javax.imageio.ImageIO.write(wide,"png",new File(output,"vehicle-vbo-extended.png"));javax.imageio.ImageIO.write(old,"png",new File(output,"vehicle-vbo-old-stride.png"));
        BufferBuilder batch=new BufferBuilder(512);batch.begin(GL11.GL_QUADS,extended);int[][] parts={new int[56],new int[56]};for(int p=0;p<2;p++)for(int i=0;i<parts[p].length;i++)parts[p][i]=p*1000+i;
        BlockMeshVbo.merge(batch,java.util.Arrays.asList(parts),extended);int[] merged=batch.getVertexState().getRawBuffer();require(merged.length==112,"complete extended records merged");for(int i=0;i<merged.length;i++)require(merged[i]==parts[i/56][i%56],"shader payload changed while batching");batch.finishDrawing();
        System.out.println("[vandorlabs][reprolab] vehicle-vertex-format-live PASS vanilla/56-byte GPU pixel parity, old-stride negative control, immutable format snapshots and preserved shader payload; shader-pack hardware acceptance separate");
    }
    private static BufferedImage capture(Minecraft mc,VertexFormat format,boolean old){
        ByteBuffer data=BufferUtils.createByteBuffer(4*format.getNextOffset());float[][] points={{8,8},{56,8},{56,56},{8,56}};
        for(int i=0;i<4;i++){int at=i*format.getNextOffset();data.putFloat(at,points[i][0]);data.putFloat(at+4,points[i][1]);data.putFloat(at+8,0);data.put(at+12,(byte)255);data.put(at+13,(byte)96);data.put(at+14,(byte)32);data.put(at+15,(byte)255);if(format.getNextOffset()>28){data.put(at+30,(byte)127);data.putFloat(at+32,500);data.putFloat(at+36,-10000);}}
        VertexBuffer buffer=new VertexBuffer(format);buffer.bufferData(data);Framebuffer frame=new Framebuffer(64,64,true);
        GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,64,64,0,-10,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(0,0,0,1);GlStateManager.clear(16640);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.disableBlend();GlStateManager.disableAlpha();mc.entityRenderer.disableLightmap();GlStateManager.disableTexture2D();
            if(old){buffer.bindBuffer();GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);GL11.glVertexPointer(3,GL11.GL_FLOAT,28,0L);GL11.glColorPointer(4,GL11.GL_UNSIGNED_BYTE,28,12L);buffer.drawArrays(GL11.GL_QUADS);GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);buffer.unbindBuffer();}
            else BlockMeshVbo.draw(buffer,format,BlockRenderLayer.SOLID);
            return net.minecraft.util.ScreenShotHelper.createScreenshot(64,64,frame);
        }finally{buffer.deleteGlBuffers();GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static boolean same(BufferedImage a,BufferedImage b){return java.util.Arrays.equals(a.getRGB(0,0,64,64,null,0,64),b.getRGB(0,0,64,64,null,0,64));}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Vehicle VBO format: "+message);}
}
