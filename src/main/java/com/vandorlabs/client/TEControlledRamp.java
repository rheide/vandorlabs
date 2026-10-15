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
        boolean glass=com.vandorlabs.compat.RampMaterials.isFullGlass(te.source);
        if(!glass){renderMaterial(te,x,y,z,partial);return;}
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int sourceRgb=GL11.glGetInteger(GL14_BLEND_SRC_RGB),destinationRgb=GL11.glGetInteger(GL14_BLEND_DST_RGB);
        int sourceAlpha=GL11.glGetInteger(GL14_BLEND_SRC_ALPHA),destinationAlpha=GL11.glGetInteger(GL14_BLEND_DST_ALPHA);
        if(glass){GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,771);GlStateManager.depthMask(false);}
        try{renderMaterial(te,x,y,z,partial);}finally{
            if(glass){GlStateManager.depthMask(depth);GlStateManager.tryBlendFuncSeparate(sourceRgb,destinationRgb,sourceAlpha,destinationAlpha);if(!blend)GlStateManager.disableBlend();}
        }
    }
    private static final int GL14_BLEND_SRC_RGB=32969,GL14_BLEND_DST_RGB=32968,
            GL14_BLEND_SRC_ALPHA=32971,GL14_BLEND_DST_ALPHA=32970;
    private void renderMaterial(TileEntityControlledRamp te,double x,double y,double z,float partial) {
        if (te.getWorld()==null) return;
        if(te.moat){renderMoat(te,x,y,z,partial);return;}
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

    private void renderMoat(TileEntityControlledRamp te,double x,double y,double z,float partial){
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.disableLighting();GlStateManager.disableCull();
        int light=te.getWorld().getCombinedLight(te.getPos(),0);float oldX=OpenGlHelper.lastBrightnessX,oldY=OpenGlHelper.lastBrightnessY;OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
        EnumFacing facing=te.getWorld().getBlockState(te.getPos()).getValue(com.vandorlabs.blocks.BlockVandorDirectional.FACING);RampGeometry.Direction direction=RampGeometry.Direction.valueOf(facing.name());
        int hinge=te.getPos().getX()*facing.getFrontOffsetX()+te.getPos().getZ()*facing.getFrontOffsetZ()-te.row;
        double[] clip={te.getPos().getX(),te.getPos().getY(),te.getPos().getZ(),te.getPos().getX()+1,te.getPos().getY()+1,te.getPos().getZ()+1};
        net.minecraft.client.renderer.block.model.IBakedModel model=Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(te.source);
        java.util.Set<net.minecraft.util.math.BlockPos> glassSources=java.util.Collections.emptySet();
        if(com.vandorlabs.compat.RampMaterials.isFullGlass(te.source)){
            net.minecraft.tileentity.TileEntity owner=te.getWorld().getTileEntity(te.controller);
            java.util.List<net.minecraft.util.math.BlockPos> sources=owner instanceof com.vandorlabs.tiles.TileEntityRampController
                    ?((com.vandorlabs.tiles.TileEntityRampController)owner).vehicleSources():te.origins;
            glassSources=new java.util.HashSet<>(sources);
        }
        for(net.minecraft.util.math.BlockPos origin:te.origins){
            TileEntityControlledRamp.SourceMaterial material=te.sourceMaterial(origin);
            java.util.List<RampGeometry.Box> shapes=com.vandorlabs.compat.RampStairs.supported(te.source)
                    ?com.vandorlabs.compat.RampStairs.boxes(te.getWorld(),te.sourceState(origin))
                    :java.util.Collections.singletonList(new RampGeometry.Box(0,te.low,0,1,te.high,1));
            for(RampGeometry.Box original:shapes)for(RampGeometry.Box shape:te.verticalMoatSources?com.vandorlabs.ramp.MoatGeometry.slices(original,te.treadPixels):java.util.Collections.singletonList(original))for(EnumFacing face:FACES){
                if(face==EnumFacing.UP&&shape.maxY<original.maxY-1e-8||face==EnumFacing.DOWN&&shape.minY>original.minY+1e-8)continue;
                // Source neighbors share an internal face. Drawing both copies
                // would accumulate stained-glass opacity inside the solid bridge.
                if(glassSources.contains(origin.offset(face)))continue;
                java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads=model.getQuads(te.source,face,0);int housing=material.texture(face);TextureAtlasSprite sprite=housing>=0?Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.texture(housing)):quads.isEmpty()?model.getParticleTexture():quads.get(0).getSprite();
                int tint=!quads.isEmpty()&&quads.get(0).hasTintIndex()?Minecraft.getMinecraft().getBlockColors().colorMultiplier(te.source,te.getWorld(),te.getPos(),quads.get(0).getTintIndex()):0xFFFFFF;
                java.util.List<double[]> vertices=new java.util.ArrayList<>();
                CuboidMesh.emitFace(shape,MESH_FACES[face.ordinal()],material.stretchSides()?(original.maxY-shape.minY)/(original.maxY-original.minY):1-shape.minY,material.stretchSides()?(original.maxY-shape.maxY)/(original.maxY-original.minY):1-shape.maxY,(vx,vy,vz,u,v)->{double[] point=moatVertex(te,direction,hinge,partial,origin.getX()+vx,origin.getY()+vy,origin.getZ()+vz);vertices.add(new double[]{point[0],point[1],point[2],u,v});});
                double[] center=moatVertex(te,direction,hinge,partial,origin.getX(),origin.getY(),origin.getZ()),normal=moatVertex(te,direction,hinge,partial,origin.getX()+face.getFrontOffsetX(),origin.getY()+face.getFrontOffsetY(),origin.getZ()+face.getFrontOffsetZ());
                float nx=(float)(normal[0]-center[0]),ny=(float)(normal[1]-center[1]),nz=(float)(normal[2]-center[2]),shade=.6F+.4F*Math.max(0,ny);
                for(double[] v:com.vandorlabs.render.DiagonalMeshClip.quads(clip,vertices.toArray(new double[vertices.size()][])))b.pos(v[0]-te.getPos().getX(),v[1]-te.getPos().getY(),v[2]-te.getPos().getZ()).color((tint>>16&255)/255F*shade,(tint>>8&255)/255F*shade,(tint&255)/255F*shade,1).tex(sprite.getInterpolatedU(v[3]*16),sprite.getInterpolatedV(v[4]*16)).lightmap((int)OpenGlHelper.lastBrightnessY,(int)OpenGlHelper.lastBrightnessX).normal(nx,ny,nz).endVertex();
            }
        }
        Tessellator.getInstance().draw();OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldX,oldY);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();
    }
    private static double[] moatVertex(TileEntityControlledRamp tile,RampGeometry.Direction face,int hinge,float partial,double x,double y,double z){
        return tile.verticalMoatSources?com.vandorlabs.ramp.MoatGeometry.vertex(face,hinge,tile.sourceY,tile.length,tile.startOffsetValue(),tile.endOffsetValue(),tile.pose(partial),tile.speed==0,x,y,z):com.vandorlabs.ramp.LegacyMoatGeometry.vertex(face,hinge,tile.sourceY,tile.high,tile.startOffsetValue(),tile.endOffsetValue(),tile.pose(partial),tile.speed==0,x,y,z);
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
            emitter.emit(a,face,sprite,material.stretchSides()?1:vBottom,material.stretchSides()?0:vTop,uShift,vShift,tint);
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
