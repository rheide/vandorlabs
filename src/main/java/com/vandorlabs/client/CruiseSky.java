package com.vandorlabs.client;

import com.vandorlabs.network.MessageCruiseTerrain;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/** Low-detail geographic background. One texture for the local rider, reused between updates. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class CruiseSky {
    private static final double[] UV = {0, .12, .88, 1};
    private static EntityGroundVehicle owner;
    private static MessageCruiseTerrain terrain;
    private static DynamicTexture texture;
    private static ResourceLocation location;

    public static boolean ready(){
        return owner!=null && owner.cruise.active() && buffered();
    }

    public static boolean buffered(){
        return terrain!=null &&
                Math.abs(terrain.centerX-owner.cruise.x())<terrain.step*MessageCruiseTerrain.SIZE/4 &&
                Math.abs(terrain.centerZ-owner.cruise.z())<terrain.step*MessageCruiseTerrain.SIZE/4;
    }

    public static void receive(MessageCruiseTerrain image) {
        Minecraft mc=Minecraft.getMinecraft();
        if (mc.player==null || !(mc.player.getRidingEntity() instanceof EntityGroundVehicle)) return;
        EntityGroundVehicle craft=(EntityGroundVehicle)mc.player.getRidingEntity();
        if (image.entity!=craft.getEntityId()) return;
        if (texture==null) {
            texture=new DynamicTexture(MessageCruiseTerrain.SIZE,MessageCruiseTerrain.SIZE);
            location=mc.getTextureManager().getDynamicTextureLocation("flight_terrain",texture);
        }
        owner=craft;
        terrain=image;
        System.arraycopy(image.pixels,0,texture.getTextureData(),0,image.pixels.length);
        texture.updateDynamicTexture();
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase!=TickEvent.Phase.END || owner==null) return;
        Minecraft mc=Minecraft.getMinecraft();
        if (mc.player!=null && mc.player.getRidingEntity()==owner && (owner.cruise.active() || owner.flightAltitude()>=200 && owner.flying())) return;
        mc.getTextureManager().deleteTexture(location);
        location=null; texture=null; terrain=null; owner=null;
    }

    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        if (terrain==null || !owner.cruise.active()) return;
        Minecraft mc=Minecraft.getMinecraft();
        if (mc.player==null || mc.player.getRidingEntity()!=owner) return;
        double span=MessageCruiseTerrain.SIZE*terrain.step;
        double width=Math.max(24,mc.gameSettings.renderDistanceChunks*12);
        double scale=width/span;
        double x=(terrain.centerX-owner.cruise.x())*scale-width*.5;
        double z=(terrain.centerZ-owner.cruise.z())*scale-width*.5;
        double y=-Math.max(5,owner.cruise.altitude()*scale);
        GlStateManager.pushMatrix();
        GlStateManager.depthMask(false);
        // Draw only against the sky. A camera under a large hull still sees the hull in front.
        GL11.glDepthRange(1,1);
        try {
            GlStateManager.disableLighting();
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            GlStateManager.color(1,1,1,1);
            mc.getTextureManager().bindTexture(location);
            BufferBuilder b=Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);
            // Fade the distant edge of the generated terrain into the sky.
            for(int i=0;i<3;i++)for(int j=0;j<3;j++){
                vertex(b,x,y,z,width,i,j);
                vertex(b,x,y,z,width,i,j+1);
                vertex(b,x,y,z,width,i+1,j+1);
                vertex(b,x,y,z,width,i+1,j);
            }
            Tessellator.getInstance().draw();
        } finally {
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.disableBlend();
            GL11.glDepthRange(0,1);
            GlStateManager.depthMask(true);
            GlStateManager.popMatrix();
        }
    }
    private static void vertex(BufferBuilder b,double x,double y,double z,double width,int col,int row){
        double u=UV[col],v=UV[row];
        int alpha=col==0||col==3||row==0||row==3?0:255;
        b.pos(x+u*width,y,z+v*width).tex(u,v).color(255,255,255,alpha).endVertex();
    }
    private CruiseSky() {}
}
