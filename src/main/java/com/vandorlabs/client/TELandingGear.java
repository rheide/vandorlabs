package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockTelescopicLandingGear;
import com.vandorlabs.tiles.TileEntityLandingGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.util.EnumFacing;
import java.util.*;

/** Applies the three transforms supplied with the telescopic model. */
public final class TELandingGear extends TileEntitySpecialRenderer<TileEntityLandingGear> {
    private static final double[] WHEEL_CENTERS={.25,.375,.4375,-.125},WHEEL_RADII={.25,.375,.4375,.875};
    private static final Map<IBakedModel,IBakedModel[]> wheelModels=new IdentityHashMap<>();
    static void clearWheelModels(){wheelModels.clear();}
    static IBakedModel wheelPart(IBakedModel model,boolean tyre){
        IBakedModel[] parts=wheelModels.computeIfAbsent(model,m->new IBakedModel[]{new WheelPart(m,false),new WheelPart(m,true)});
        return parts[tyre?1:0];
    }
    private static final class WheelPart implements IBakedModel {
        final IBakedModel original;final List<BakedQuad> general;final Map<EnumFacing,List<BakedQuad>> faces=new EnumMap<>(EnumFacing.class);
        WheelPart(IBakedModel original,boolean tyre){this.original=original;general=filter(original.getQuads(null,null,0),tyre);for(EnumFacing side:EnumFacing.values())faces.put(side,filter(original.getQuads(null,side,0),tyre));}
        private static List<BakedQuad> filter(List<BakedQuad> source,boolean tyre){List<BakedQuad> result=new ArrayList<>();for(BakedQuad quad:source){String name=quad.getSprite().getIconName();if((name.endsWith("/rubber")||name.endsWith("/hub"))==tyre)result.add(quad);}return Collections.unmodifiableList(result);}
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return side==null?general:faces.get(side);}
        public boolean isAmbientOcclusion(){return original.isAmbientOcclusion();}
        public boolean isGui3d(){return original.isGui3d();}
        public boolean isBuiltInRenderer(){return false;}
        public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleTexture(){return original.getParticleTexture();}
        public ItemCameraTransforms getItemCameraTransforms(){return original.getItemCameraTransforms();}
        public ItemOverrideList getOverrides(){return original.getOverrides();}
    }
    public void render(TileEntityLandingGear tile,double x,double y,double z,float partial,int stage,float alpha) {
        IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof BlockTelescopicLandingGear) || state.getValue(BlockTelescopicLandingGear.LOWER)) return;
        ModelResourceLocation FIXED=RenderIdentifierCache.gear(tile.getSize(),false);
        ModelResourceLocation WHEEL=RenderIdentifierCache.gear(tile.getSize(),true);
        float anchor=BlockTelescopicLandingGear.pistonAnchor(tile.getSize());
        float pistonLength=BlockTelescopicLandingGear.pistonLength(tile.getSize());
        float t=tile.previous+(tile.progress-tile.previous)*partial;
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();GlStateManager.translate(x+.5+(tile.getSize()==4?.5:0),y,z+.5+(tile.getSize()==4?.5:0));
        GlStateManager.rotate(180-state.getValue(BlockTelescopicLandingGear.FACING).getHorizontalAngle(),0,1,0);
        GlStateManager.translate(-.5,0,-.5);
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
        GlStateManager.disableLighting();GlStateManager.disableCull();
        draw(FIXED);
        GlStateManager.pushMatrix();GlStateManager.translate(0,-t,0);
        com.vandorlabs.vehicle.EntityGroundVehicle craft=tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)tile.getWorld()).craft:null;
        if(craft==null)draw(WHEEL);
        else {
            int size=Math.min(3,tile.getSize());double center=WHEEL_CENTERS[size],radius=WHEEL_RADII[size];
            IBakedModel wheel=Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getModelManager().getModel(WHEEL);
            double distance=VehicleWheelMotion.sample(craft,partial);
            GlStateManager.translate(.5,center,.5);GlStateManager.rotate(VehicleWheelMotion.steer(craft),0,1,0);GlStateManager.translate(-.5,-center,-.5);
            draw(wheelPart(wheel,false));
            GlStateManager.translate(.5,center,.5);GlStateManager.rotate(VehicleWheelMotion.roll(distance,radius,VehicleWheelMotion.alignment(craft.structure.facing,state.getValue(BlockTelescopicLandingGear.FACING))),1,0,0);GlStateManager.translate(-.5,-center,-.5);
            draw(wheelPart(wheel,true));
        }
        GlStateManager.popMatrix();
        drawArm(anchor, anchor-pistonLength-t,tile.getSize()>=3);
        GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.popMatrix();
    }
    /** Split the arm at texture repeats; changing length never scales its UVs. */
    private void drawArm(double top, double bottom,boolean extraLarge) {
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = Minecraft.getMinecraft()
                .getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(org.lwjgl.opengl.GL11.GL_QUADS,BlockSurfaceFormat.get());
        double low=extraLarge?4/16D:6/16D,high=extraLarge?12/16D:10/16D;
        for(double upper=top;upper>bottom+1E-8;upper-=1) {
            double lower=Math.max(bottom,upper-1),v=(upper-lower)*16;
            armQuad(b,sprite, low,upper,low, high,upper,low, high,lower,low, low,lower,low,6,0,10,v);
            armQuad(b,sprite, high,upper,high, low,upper,high, low,lower,high, high,lower,high,6,0,10,v);
            armQuad(b,sprite, low,upper,high, low,upper,low, low,lower,low, low,lower,high,6,0,10,v);
            armQuad(b,sprite, high,upper,low, high,upper,high, high,lower,high, high,lower,low,6,0,10,v);
        }
        armQuad(b,sprite, low,top,high, high,top,high, high,top,low, low,top,low,6,6,10,10);
        armQuad(b,sprite, low,bottom,low, high,bottom,low, high,bottom,high, low,bottom,high,6,6,10,10);
        Tessellator.getInstance().draw();
    }
    private static void armQuad(BufferBuilder b,net.minecraft.client.renderer.texture.TextureAtlasSprite s,
            double x0,double y0,double z0,double x1,double y1,double z1,
            double x2,double y2,double z2,double x3,double y3,double z3,
            double u0,double v0,double u1,double v1) {
        WorldSurface.rawQuad(b,false,
                x0,y0,z0,s.getInterpolatedU(u0),s.getInterpolatedV(v0),
                x1,y1,z1,s.getInterpolatedU(u1),s.getInterpolatedV(v0),
                x2,y2,z2,s.getInterpolatedU(u1),s.getInterpolatedV(v1),
                x3,y3,z3,s.getInterpolatedU(u0),s.getInterpolatedV(v1));
    }
    private void draw(ModelResourceLocation model) {
        Minecraft mc=Minecraft.getMinecraft();
        draw(mc.getRenderItem().getItemModelMesher().getModelManager().getModel(model));
    }
    private void draw(IBakedModel model){Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelRenderer().renderModelBrightnessColor(model,1,1,1,1);}
}
