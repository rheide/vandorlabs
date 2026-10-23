package com.vandorlabs.client;

import com.vandorlabs.cockpit.CockpitData;
import com.vandorlabs.tiles.TileEntityCockpit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;

/** Render the actual slanted display planes with clean and contaminated shared buffers. */
final class CockpitRedstoneRenderChecks {
    static void run(Minecraft mc,File output)throws Exception{
        TileEntityCockpit tile=new TileEntityCockpit();tile.setWorld(mc.world);
        java.lang.reflect.Method rows=TECockpit.class.getDeclaredMethod("rows",TileEntityCockpit.class,CockpitData.Surface.class);rows.setAccessible(true);
        for(CockpitData.Surface plane:tile.data().surfaces)if(plane.id.equals("screen_left")||plane.id.equals("screen_right")){
            tile.display(plane.id).rows=3;
            for(int i=0;i<3;i++){TileEntityCockpit.Control control=tile.control(plane.id+"/"+i);control.label="MMMM";control.kind=i==1?1:i==2?2:0;control.level=15;}
            BufferedImage clean=capture(mc,tile,plane,rows,false),dirty=capture(mc,tile,plane,rows,true);
            javax.imageio.ImageIO.write(clean,"png",new File(output,"cockpit-redstone-"+plane.id+".png"));
            javax.imageio.ImageIO.write(dirty,"png",new File(output,"cockpit-redstone-"+plane.id+"-offset.png"));
            int minX=999,minY=999,maxX=-1,maxY=-1;
            for(int y=0;y<clean.getHeight()/3;y++)for(int x=0;x<clean.getWidth()/2;x++)if((clean.getRGB(x,y)&0xffffff)==0xffffff){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
            double ratio=(maxX-minX+1D)/(maxY-minY+1D);
            boolean same=java.util.Arrays.equals(clean.getRGB(0,0,clean.getWidth(),clean.getHeight(),null,0,clean.getWidth()),dirty.getRGB(0,0,dirty.getWidth(),dirty.getHeight(),null,0,dirty.getWidth()));
            System.out.println("[vandorlabs][reprolab] cockpit-redstone-measure "+plane.id+" glyphAspect="+ratio+" bufferTranslationInvariant="+same);
            if(!Boolean.getBoolean("vandorlabs.cockpitScreenBaseline")){
                require(ratio>2.5&&ratio<4.5,"square font pixels on "+plane.id);
                require(same,"shared buffer translation moved buttons on "+plane.id);
                int buttons=0;for(int y=0;y<clean.getHeight();y++)for(int x=clean.getWidth()/2;x<clean.getWidth();x++)if((clean.getRGB(x,y)&0xffffff)==0x46d4b5)buttons++;
                require(buttons>500,"visible toggle and slider controls on "+plane.id);
            }
        }
        if(!Boolean.getBoolean("vandorlabs.cockpitScreenBaseline"))System.out.println("[vandorlabs][reprolab] cockpit-redstone-render PASS side screens square text visible controls shared-buffer isolation");
    }
    private static BufferedImage capture(Minecraft mc,TileEntityCockpit tile,CockpitData.Surface plane,java.lang.reflect.Method rows,boolean dirty)throws Exception{
        int width=544,height=(int)Math.round(512*plane.v.lengthVector()/plane.u.lengthVector())+32;
        Framebuffer frame=new Framebuffer(width,height,true);
        Vec3d u=plane.u.normalize(),v=plane.v.normalize(),n=plane.normal,o=plane.point(0,1);double scale=512/plane.u.lengthVector();
        FloatBuffer camera=BufferUtils.createFloatBuffer(16);camera.put(new float[]{(float)(u.x*scale),(float)(-v.x*scale),(float)n.x,0,(float)(u.y*scale),(float)(-v.y*scale),(float)n.y,0,(float)(u.z*scale),(float)(-v.z*scale),(float)n.z,0,(float)(16-o.dotProduct(u)*scale),(float)(16+o.dotProduct(v)*scale),(float)-o.dotProduct(n),1}).flip();
        GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,width,height,0,-10,10);
        GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.multMatrix(camera);
            GlStateManager.clearColor(0,0,0,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);
            Tessellator.getInstance().getBuffer().setTranslation(dirty?123:0,dirty?456:0,dirty?789:0);
            rows.invoke(null,tile,plane);
            return net.minecraft.util.ScreenShotHelper.createScreenshot(width,height,frame);
        }finally{
            Tessellator.getInstance().getBuffer().setTranslation(0,0,0);GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);
        }
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Cockpit Redstone render: "+message);}
}
