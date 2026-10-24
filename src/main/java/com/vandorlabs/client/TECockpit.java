package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityCockpit;
import com.vandorlabs.cockpit.CockpitData;
import com.vandorlabs.client.cockpits.CockpitRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import java.nio.FloatBuffer;

/** Authored OBJ rig plus individually configured animated and interactive surface planes. */
public final class TECockpit extends TileEntitySpecialRenderer<TileEntityCockpit> {
    private static final double[][] UV_CORNERS={{0,0},{1,0},{1,1},{0,1}};
    private static final java.util.Map<CockpitData.Surface,PlaneCache> PLANES=new java.util.WeakHashMap<>();
    private static final class PlaneCache {
        final Vec3d[] corners=new Vec3d[4];final java.util.Map<Integer,FloatBuffer> matrices=new java.util.HashMap<>();
        PlaneCache(CockpitData.Surface plane){for(int i=0;i<4;i++)corners[i]=plane.point(UV_CORNERS[i][0],UV_CORNERS[i][1]);}
    }
    private static FloatBuffer matrix(CockpitData.Surface plane,int width,int height){
        PlaneCache cache=PLANES.computeIfAbsent(plane,PlaneCache::new);int key=width*65536+height;
        FloatBuffer matrix=cache.matrices.get(key);
        if(matrix==null){Vec3d o=plane.point(0,1).add(plane.normal.scale(.0005)),u=plane.u.scale(1D/width),v=plane.v.scale(-1D/height),n=plane.normal.scale(1D/width);matrix=org.lwjgl.BufferUtils.createFloatBuffer(16);matrix.put(new float[]{(float)u.x,(float)u.y,(float)u.z,0,(float)v.x,(float)v.y,(float)v.z,0,(float)n.x,(float)n.y,(float)n.z,0,(float)o.x,(float)o.y,(float)o.z,1}).flip();cache.matrices.put(key,matrix);}
        matrix.rewind();return matrix;
    }

    private static final FloatBuffer COLOR_SCRATCH=org.lwjgl.BufferUtils.createFloatBuffer(16);
    @Override public void render(TileEntityCockpit tile,double x,double y,double z,float partial,int stage,float alpha){
        if(!tile.hasLiveBlock())return;
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0);EnumFacing facing=tile.facing();
        CockpitRenderer.renderAnimated(tile.modelId(),x,y,z,facing,light,id->tile.visualDetent(id,partial),tile.sideTexture(),tile.getHeight());
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        boolean lighting=GL11.glIsEnabled(GL11.GL_LIGHTING),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND);
        int oldActive=GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);int oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        FloatBuffer color=COLOR_SCRATCH;color.clear();GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(x+.5,y,z+.5);GlStateManager.rotate(facing==EnumFacing.EAST?-90:facing==EnumFacing.WEST?90:facing==EnumFacing.SOUTH?180:0,0,1,0);GlStateManager.translate(-.5,0,-.5);
            GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
            for(CockpitData.Surface plane:tile.data().surfaces){TileEntityCockpit.Display screen=tile.display(plane.id);boolean blank=screen.texture<0||!screen.active;double[] uv=blank?new double[]{0,1,0,1}:TEAnimatedScreenSelector.bindCockpit(tile,screen);if(blank)GlStateManager.disableTexture2D();
                BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
                PlaneCache cache=PLANES.computeIfAbsent(plane,PlaneCache::new);
                for(int corner=0;corner<4;corner++){double[] p=UV_CORNERS[corner];Vec3d v=cache.corners[corner];float colorValue=blank?0:1;buffer.pos(v.x,v.y+("screen".equals(plane.role)?-.75*tile.screens(partial):0),v.z+(tile.design().equals("cockpit")&&plane.id.equals("touch_right_keyboard")?tile.seatSlide(partial):0)).tex(uv[2]+p[0]*(uv[3]-uv[2]),uv[1]+p[1]*(uv[0]-uv[1])).color(colorValue,colorValue,colorValue,1F).normal((float)plane.normal.x,(float)plane.normal.y,(float)plane.normal.z).endVertex();}
                Tessellator.getInstance().draw();
                if(blank)GlStateManager.enableTexture2D();
                if(screen.active&&screen.texture<=com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.REDSTONE_SURFACE){GlStateManager.pushMatrix();if("screen".equals(plane.role))GlStateManager.translate(0,-.75*tile.screens(partial),0);else if(tile.design().equals("cockpit")&&plane.id.equals("touch_right_keyboard"))GlStateManager.translate(0,0,tile.seatSlide(partial));
                if(screen.active&&screen.texture==com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.REDSTONE_SURFACE)rows(tile,plane);
                if(screen.active&&screen.texture==com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.VEHICLE_SURFACE)vehicle(tile,plane);GlStateManager.popMatrix();}
            }
        }finally{
            GlStateManager.popMatrix();OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.bindTexture(oldTexture);GlStateManager.setActiveTexture(oldActive);
            if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();GlStateManager.color(color.get(0),color.get(1),color.get(2),color.get(3));
        }
    }
    private static void vehicle(TileEntityCockpit tile,CockpitData.Surface plane){
        int height="touch".equals(plane.role)?64:128;
        FloatBuffer matrix=matrix(plane,128,height);
        GlStateManager.pushMatrix();GlStateManager.multMatrix(matrix);RedstoneScreenRenderer.drawVehicleContents(tile.getWorld(),tile.getPos(),height,height==64?0:24);GlStateManager.popMatrix();
    }
    private static void rows(TileEntityCockpit tile,CockpitData.Surface plane){
        com.vandorlabs.cockpit.CockpitScreenLayout layout=new com.vandorlabs.cockpit.CockpitScreenLayout(plane.u.lengthVector(),plane.v.lengthVector(),plane.role,tile.display(plane.id).rows);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.multMatrix(matrix(plane,128,layout.height));GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            RedstoneScreenRenderer.bindWhite();BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
            RedstoneScreenRenderer.rect(buffer,0,0,128,layout.height,0,0xff071018);
            for(int row=0;row<layout.rows;row++){
                TileEntityCockpit.Control c=tile.control(plane.id+"/"+row);int y=layout.widgetTop(row);
                RedstoneScreenRenderer.rect(buffer,4,layout.top(row)+1,124,layout.bottom(row)-1,.01,0xff1c2b37);
                if(c.kind==1){for(int i=0;i<4;i++)RedstoneScreenRenderer.rect(buffer,61+i*15,y+1,74+i*15,y+9,.02,c.sliderLevel(i)<=c.level?0xff46d4b5:0xff33434d);}
                else RedstoneScreenRenderer.rect(buffer,98,y,120,y+10,.02,c.level>0?0xff46d4b5:0xff33434d);
            }
            Tessellator.getInstance().draw();net.minecraft.client.gui.FontRenderer font=Minecraft.getMinecraft().fontRenderer;
            if(layout.rows==0)RedstoneScreenRenderer.text(font,"No controls",8,layout.height/2-4,0xb8c8d0);
            for(int row=0;row<layout.rows;row++){
                TileEntityCockpit.Control c=tile.control(plane.id+"/"+row);int y=layout.widgetTop(row);
                RedstoneScreenRenderer.text(font,font.trimStringToWidth(c.label,c.kind==1?50:86),8,y+1,0xffffff);
                if(c.kind!=1)RedstoneScreenRenderer.text(font,c.level>0?"ON":c.kind==2?"GO":"OFF",100,y+1,c.level>0?0x082419:0xe0e8ef);
            }
        }finally{GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);}
    }
}
