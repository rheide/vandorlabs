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
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/** Render the saved source material using the same geometry as server collision. */
@SideOnly(Side.CLIENT)
public class TEControlledRamp extends TileEntitySpecialRenderer<TileEntityControlledRamp> {
    private static final EnumFacing[] FACES=EnumFacing.values();
    private static final CuboidMesh.Face[] MESH_FACES=new CuboidMesh.Face[FACES.length];
    static { for(EnumFacing face:FACES)MESH_FACES[face.ordinal()]=CuboidMesh.Face.valueOf(face.name()); }
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
        b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
        FaceEmitter emitter=new FaceEmitter(b);
        for (RampGeometry.Box box:geometry) materialCuboid(emitter,box,te,partial);
        Tessellator.getInstance().draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldX,oldY);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.color(1,1,1,1);
        GlStateManager.popMatrix();
    }

    private static void materialCuboid(FaceEmitter emitter,RampGeometry.Box a,TileEntityControlledRamp te,float partial) {
        net.minecraft.client.renderer.block.model.IBakedModel model=Minecraft.getMinecraft()
                .getBlockRendererDispatcher().getModelForState(te.source);
        double y=a.minY,Y=a.maxY;
        double vBottom=te.sideTextureV(a,y,partial),vTop=te.sideTextureV(a,Y,partial);
        double[] shift=te.textureShift(a,partial);
        TileEntityControlledRamp.SourceMaterial material=te.sourceMaterial(a,partial);
        for (EnumFacing face:FACES) {
            int housing=material.texture(face);
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads=model.getQuads(te.source,face,0);
            TextureAtlasSprite sprite=housing>=0
                    ? Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(
                    com.vandorlabs.tiles.ScreenHousingTextures.texture(housing))
                    : quads.isEmpty()?model.getParticleTexture():quads.get(0).getSprite();
            int tint=0xFFFFFF;
            if (!quads.isEmpty() && quads.get(0).hasTintIndex()) tint=Minecraft.getMinecraft().getBlockColors()
                    .colorMultiplier(te.source,te.getWorld(),te.getPos(),quads.get(0).getTintIndex());
            double uShift=face==EnumFacing.WEST||face==EnumFacing.EAST?shift[1]:shift[0];
            double vShift=face==EnumFacing.UP||face==EnumFacing.DOWN?shift[1]:0;
            emitter.emit(a,face,sprite,vBottom,vTop,uShift,vShift,tint);
        }
    }

    /** Shared by the live renderer and packed-vertex regression checks. */
    static void emitFace(BufferBuilder b,RampGeometry.Box a,EnumFacing face,TextureAtlasSprite sprite,
            double vBottom,double vTop,double uShift,double vShift,int tint) {
        new FaceEmitter(b).emit(a,face,sprite,vBottom,vTop,uShift,vShift,tint);
    }

    /** One consumer per rendered cell, shared by all slice faces. */
    static final class FaceEmitter implements CuboidMesh.VertexConsumer {
        private final BufferBuilder buffer;
        private TextureAtlasSprite sprite;
        private EnumFacing face;
        private double uShift,vShift;
        private float red,green,blue;
        FaceEmitter(BufferBuilder buffer) { this.buffer=buffer; }
        void emit(RampGeometry.Box box,EnumFacing face,TextureAtlasSprite sprite,
                double vBottom,double vTop,double uShift,double vShift,int tint) {
            this.face=face;this.sprite=sprite;this.uShift=uShift;this.vShift=vShift;
            float shade=face==EnumFacing.UP?1:face==EnumFacing.DOWN?.5F:face.getAxis()==EnumFacing.Axis.X?.6F:.8F;
            red=((tint>>16)&255)/255F*shade;green=((tint>>8)&255)/255F*shade;blue=(tint&255)/255F*shade;
            CuboidMesh.emitFace(box,MESH_FACES[face.ordinal()],vBottom,vTop,this);
        }
        @Override public void vertex(double x,double y,double z,double u,double v) {
            // Attribute order must match BlockSurfaceFormat: position, color, UV, lightmap, normal.
            buffer.pos(x,y,z).color(red,green,blue,1)
                    .tex(sprite.getInterpolatedU((u+uShift)*16),sprite.getInterpolatedV((v+vShift)*16))
                    .lightmap((int)OpenGlHelper.lastBrightnessY,(int)OpenGlHelper.lastBrightnessX)
                    .normal(face.getFrontOffsetX(),face.getFrontOffsetY(),face.getFrontOffsetZ()).endVertex();
        }
    }
}
