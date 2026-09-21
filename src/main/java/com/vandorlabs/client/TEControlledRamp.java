package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityControlledRamp;
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
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/** Render the saved source material using the same geometry as server collision. */
@SideOnly(Side.CLIENT)
public class TEControlledRamp extends TileEntitySpecialRenderer<TileEntityControlledRamp> {
    @Override public void render(TileEntityControlledRamp te,double x,double y,double z,
            float partial,int destroyStage,float alpha) {
        if (te.getWorld()==null) return;
        java.util.List<AxisAlignedBB> geometry=te.boxes(partial);
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
        for (AxisAlignedBB box:geometry) materialCuboid(b,box,te,partial);
        Tessellator.getInstance().draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldX,oldY);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.color(1,1,1,1);
        GlStateManager.popMatrix();
    }

    private static void materialCuboid(BufferBuilder b,AxisAlignedBB a,TileEntityControlledRamp te,float partial) {
        net.minecraft.client.renderer.block.model.IBakedModel model=Minecraft.getMinecraft()
                .getBlockRendererDispatcher().getModelForState(te.source);
        double x=a.minX,y=a.minY,z=a.minZ,X=a.maxX,Y=a.maxY,Z=a.maxZ;
        double vBottom=te.sideTextureV(a,y,partial),vTop=te.sideTextureV(a,Y,partial);
        double[][] vertices={
            {x,y,Z,X,y,Z,X,y,z,x,y,z}, {x,Y,z,X,Y,z,X,Y,Z,x,Y,Z},
            {x,y,z,X,y,z,X,Y,z,x,Y,z}, {X,y,Z,x,y,Z,x,Y,Z,X,Y,Z},
            {x,y,Z,x,y,z,x,Y,z,x,Y,Z}, {X,y,z,X,y,Z,X,Y,Z,X,Y,z}};
        for (EnumFacing face:EnumFacing.values()) {
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads=model.getQuads(te.source,face,0);
            TextureAtlasSprite sprite=quads.isEmpty()?model.getParticleTexture():quads.get(0).getSprite();
            int tint=0xFFFFFF;
            if (!quads.isEmpty() && quads.get(0).hasTintIndex()) tint=Minecraft.getMinecraft().getBlockColors()
                    .colorMultiplier(te.source,te.getWorld(),te.getPos(),quads.get(0).getTintIndex());
            float shade=face==EnumFacing.UP?1:face==EnumFacing.DOWN?.5F:face.getAxis()==EnumFacing.Axis.X?.6F:.8F;
            double[] p=vertices[face.getIndex()];
            for (int i=0;i<4;i++) {
                double vx=p[i*3],vy=p[i*3+1],vz=p[i*3+2];
                double u=face.getAxis()==EnumFacing.Axis.X?vz:vx;
                double v=face.getAxis()==EnumFacing.Axis.Y?vz:(vy==y?vBottom:vTop);
                b.pos(vx,vy,vz).tex(sprite.getInterpolatedU(u*16),sprite.getInterpolatedV(v*16))
                        .color(((tint>>16)&255)/255F*shade,((tint>>8)&255)/255F*shade,(tint&255)/255F*shade,1).endVertex();
            }
        }
    }
}
