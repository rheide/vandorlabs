package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityControlledRamp;
import com.vandorlabs.ramp.RampGeometry;
import com.vandorlabs.render.CuboidMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/** Render the saved source material using the same geometry as server collision. */
@SideOnly(Side.CLIENT)
public class TEControlledRamp extends TileEntitySpecialRenderer<TileEntityControlledRamp> {
    @Override public void render(TileEntityControlledRamp te,double x,double y,double z,
            float partial,int destroyStage,float alpha) {
        if (te.getWorld()==null) return;
        java.util.List<RampGeometry.Box> geometry=te.geometry(partial);
        if (geometry.isEmpty()) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x,y,z);
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        int light=te.getWorld().getCombinedLight(te.getPos(),0);
        float oldX=OpenGlHelper.lastBrightnessX,oldY=OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);
        for (RampGeometry.Box box:geometry) materialCuboid(b,box,te,partial);
        Tessellator.getInstance().draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldX,oldY);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.color(1,1,1,1);
        GlStateManager.popMatrix();
    }

    private static void materialCuboid(BufferBuilder b,RampGeometry.Box a,TileEntityControlledRamp te,float partial) {
        net.minecraft.client.renderer.block.model.IBakedModel model=Minecraft.getMinecraft()
                .getBlockRendererDispatcher().getModelForState(te.source);
        double y=a.minY,Y=a.maxY;
        double vBottom=te.sideTextureV(a,y,partial),vTop=te.sideTextureV(a,Y,partial);
        double[] shift=te.textureShift(a,partial);
        int housing=te.sourceHousing(a,partial);
        for (EnumFacing face:EnumFacing.values()) {
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads=model.getQuads(te.source,face,0);
            TextureAtlasSprite sprite=housing>=0
                    ? Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(
                    com.vandorlabs.tiles.ScreenHousingTextures.texture(housing))
                    : quads.isEmpty()?model.getParticleTexture():quads.get(0).getSprite();
            int tint=0xFFFFFF;
            if (!quads.isEmpty() && quads.get(0).hasTintIndex()) tint=Minecraft.getMinecraft().getBlockColors()
                    .colorMultiplier(te.source,te.getWorld(),te.getPos(),quads.get(0).getTintIndex());
            float shade=face==EnumFacing.UP?1:face==EnumFacing.DOWN?.5F:face.getAxis()==EnumFacing.Axis.X?.6F:.8F;
            final TextureAtlasSprite drawSprite=sprite;
            final int drawTint=tint;
            final double uShift=face==EnumFacing.WEST||face==EnumFacing.EAST?shift[1]:shift[0];
            final double vShift=face==EnumFacing.UP||face==EnumFacing.DOWN?shift[1]:0;
            CuboidMesh.emitFace(a,
                    CuboidMesh.Face.valueOf(face.getName().toUpperCase(java.util.Locale.ROOT)),vBottom,vTop,
                    (vx,vy,vz,u,v)->b.pos(vx,vy,vz).tex(drawSprite.getInterpolatedU((u+uShift)*16),drawSprite.getInterpolatedV((v+vShift)*16))
                        .color(((drawTint>>16)&255)/255F*shade,((drawTint>>8)&255)/255F*shade,(drawTint&255)/255F*shade,1).endVertex()
            );
        }
    }
}
