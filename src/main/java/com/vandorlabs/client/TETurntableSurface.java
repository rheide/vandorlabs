package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/** One square image per assembly; the circular plate rotates inside its fixed square housing. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class TETurntableSurface extends TileEntitySpecialRenderer<TileEntityTurntableSurface> {
    private static String texture(boolean active,boolean high){return "vandorlabs:blocks/turntable/turntable_block_top"+(active?"_on":"")+(high?"":"_low");}
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event){for(boolean active:new boolean[]{false,true})for(boolean high:new boolean[]{false,true})event.getMap().registerSprite(new ResourceLocation(texture(active,high)));}
    @SubscribeEvent public static void stitched(TextureStitchEvent.Post event){RenderTurntable.clear();}
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)Minecraft.getMinecraft().addScheduledTask(RenderTurntable::clear);}
    // The shared face spans chunks; it must not disappear when its root chunk is culled.
    @Override public boolean isGlobalRenderer(TileEntityTurntableSurface tile){TileEntityTurntableController c=tile.controller();return c!=null&&c.layout!=null&&tile.getPos().equals(c.layout.root);}
    @Override public void render(TileEntityTurntableSurface tile,double x,double y,double z,float partial,int destroy,float alpha){
        TileEntityTurntableController controller=tile.controller();if(controller==null||controller.layout==null||!tile.getPos().equals(controller.layout.root))return;
        TurntableSurfaceLayout layout=controller.layout;TurntableGeometry.Face face=layout.basis;boolean active=controller.running();
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture(active,layout.size>1));
        GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.disableCull();GlStateManager.disableLighting();GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);GlStateManager.translate(x-tile.getPos().getX()+layout.pivot.x,y-tile.getPos().getY()+layout.pivot.y,z-tile.getPos().getZ()+layout.pivot.z);
            int light=tile.getWorld().getCombinedLight(tile.getPos().offset(layout.normal),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
            BufferBuilder buffer=Tessellator.getInstance().getBuffer();double r=layout.size*.5;
            buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);vertex(buffer,face,sprite,-r,-r,0,0,.002);vertex(buffer,face,sprite,r,-r,1,0,.002);vertex(buffer,face,sprite,r,r,1,1,.002);vertex(buffer,face,sprite,-r,r,0,1,.002);Tessellator.getInstance().draw();
            GlStateManager.rotate((float)-controller.angle(partial),face.x,face.y,face.z);buffer.begin(GL11.GL_TRIANGLE_FAN,DefaultVertexFormats.POSITION_TEX);vertex(buffer,face,sprite,0,0,.5,.5,.003);
            for(int i=0;i<=128;i++){double a=2*Math.PI*i/128,cu=Math.cos(a)*TurntableSurfaceLayout.DISC_RADIUS,cv=Math.sin(a)*TurntableSurfaceLayout.DISC_RADIUS;vertex(buffer,face,sprite,cu*layout.size,cv*layout.size,.5+cu,.5+cv,.003);}Tessellator.getInstance().draw();
        }finally{GlStateManager.popMatrix();}
    }
    private static void vertex(BufferBuilder buffer,TurntableGeometry.Face face,TextureAtlasSprite sprite,double u,double v,double tu,double tv,double depth){double[] p=face.point(u,v,depth);buffer.pos(p[0],p[1],p[2]).tex(sprite.getInterpolatedU(tu*16),sprite.getInterpolatedV(tv*16)).endVertex();}
}
