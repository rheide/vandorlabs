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
    @Override public void render(TileEntityCockpit tile,double x,double y,double z,float partial,int stage,float alpha){
        if(!tile.hasLiveBlock())return;
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0);EnumFacing facing=tile.facing();
        CockpitRenderer.renderAnimated(tile.design(),x,y,z,facing,light,id->{TileEntityCockpit.Control c=tile.control(id);return c==null?0:c.level/5D;},tile.sideTexture());
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        boolean lighting=GL11.glIsEnabled(GL11.GL_LIGHTING),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND);
        int oldActive=GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);int oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        FloatBuffer color=org.lwjgl.BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(x+.5,y,z+.5);GlStateManager.rotate(facing==EnumFacing.EAST?-90:facing==EnumFacing.WEST?90:facing==EnumFacing.SOUTH?180:0,0,1,0);GlStateManager.translate(-.5,0,-.5);
            GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
            for(CockpitData.Surface plane:tile.data().surfaces){TileEntityCockpit.Display screen=tile.display(plane.id);boolean blank=screen.texture<0||!screen.active;double[] uv=blank?new double[]{0,1,0,1}:TEAnimatedScreenSelector.bindCockpit(tile,screen);if(blank)GlStateManager.disableTexture2D();
                BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
                double[][] points={{0,0},{1,0},{1,1},{0,1}};
                for(double[] p:points){Vec3d v=plane.point(p[0],p[1]);float colorValue=blank?0:1;buffer.pos(v.x,v.y,v.z).tex(uv[2]+p[0]*(uv[3]-uv[2]),uv[1]+p[1]*(uv[0]-uv[1])).color(colorValue,colorValue,colorValue,1F).normal((float)plane.normal.x,(float)plane.normal.y,(float)plane.normal.z).endVertex();}
                Tessellator.getInstance().draw();
                if(blank)GlStateManager.enableTexture2D();
                if("touch".equals(plane.role) && screen.active && screen.texture>=0)rows(tile,plane);
            }
        }finally{
            GlStateManager.popMatrix();OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.bindTexture(oldTexture);GlStateManager.setActiveTexture(oldActive);
            if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();GlStateManager.color(color.get(0),color.get(1),color.get(2),color.get(3));
        }
    }
    private static void rows(TileEntityCockpit tile,CockpitData.Surface plane){
        Vec3d o=plane.point(0,1).add(plane.normal.scale(.0005)),u=plane.u.scale(1D/192),v=plane.v.scale(-1D/64),n=plane.normal.scale(1D/192);
        FloatBuffer matrix=org.lwjgl.BufferUtils.createFloatBuffer(16);matrix.put(new float[]{(float)u.x,(float)u.y,(float)u.z,0,(float)v.x,(float)v.y,(float)v.z,0,(float)n.x,(float)n.y,(float)n.z,0,(float)o.x,(float)o.y,(float)o.z,1});matrix.flip();
        GlStateManager.pushMatrix();GlStateManager.multMatrix(matrix);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        for(int row=0;row<3;row++){TileEntityCockpit.Control c=tile.control(plane.id+"/"+row);int y=row*21;Gui.drawRect(0,y,192,y+20,0x99071018);String label=c.label.length()>18?c.label.substring(0,18):c.label;Minecraft.getMinecraft().fontRenderer.drawString(label,4,y+6,0xffffff);Minecraft.getMinecraft().fontRenderer.drawString(Integer.toString(c.level),166,y+6,c.level>0?0x63ffd5:0x9daab0);
            if(c.kind==1)for(int i=0;i<4;i++)Gui.drawRect(116+i*10,y+6,123+i*10,y+14,i*5<=c.level?0xff46d4b5:0xff33434d);
        }
        GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);
    }
}
