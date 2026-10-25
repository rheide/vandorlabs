package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.*;
import com.vandorlabs.surface.SquareLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/** Surface-owned artwork spans a square; turntables additionally rotate its circular center. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class TETurntableSurface extends TileEntitySpecialRenderer<TileEntityTurntableSurface> {
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event){for(String id:TurntableTextures.IDS)for(boolean high:new boolean[]{false,true})event.getMap().registerSprite(new ResourceLocation(TurntableTextures.texture(id,high)));}
    @SubscribeEvent public static void stitched(TextureStitchEvent.Post event){RenderTurntable.clear();SurfaceTextureMesh.clear();}
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)Minecraft.getMinecraft().addScheduledTask(()->{RenderTurntable.clear();SurfaceTextureMesh.clear();});}
    @Override public boolean isGlobalRenderer(TileEntityTurntableSurface tile){SquareLayout layout=tile.appearanceLayout();return layout.size>1&&tile.getPos().equals(layout.root);}
    @Override public void render(TileEntityTurntableSurface tile,double x,double y,double z,float partial,int destroy,float alpha){
        SquareLayout layout=tile.appearanceLayout();if(!tile.getPos().equals(layout.root))return;
        TileEntityTurntableController controller=tile.controller();TurntableGeometry.Face face=layout.basis;
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(TurntableTextures.texture(tile.selectedTexture(),layout.size>1));
        int light=tile.getWorld().getCombinedLight(tile.getPos().offset(layout.normal),0);
        GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.depthFunc(GL11.GL_LEQUAL);
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.disableCull();GlStateManager.disableLighting();GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);
            GlStateManager.translate(x-tile.getPos().getX()+layout.pivot.x,y-tile.getPos().getY()+layout.pivot.y,z-tile.getPos().getZ()+layout.pivot.z);
            draw(sprite,layout,false,.002,light,tile.textureLayout);
            if(controller!=null){GlStateManager.rotate((float)-controller.angle(partial),face.x,face.y,face.z);draw(sprite,layout,true,.003,light,tile.textureLayout);}
        }finally{GlStateManager.popMatrix();}
    }
    private static void draw(TextureAtlasSprite sprite,SquareLayout layout,boolean disc,double depth,int light,int mode){
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);
        // A real opaque atlas sprite supplies Fit margins and backs material cutouts, including with shaders.
        TextureAtlasSprite black=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("minecraft:blocks/concrete_black");
        buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());SurfaceTextureMesh.get(black,layout.size,0,disc).draw(buffer,layout.basis,black,depth-.0001,light);Tessellator.getInstance().draw();
        buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());SurfaceTextureMesh.get(sprite,layout.size,mode,disc).draw(buffer,layout.basis,sprite,depth,light);Tessellator.getInstance().draw();
    }
}
