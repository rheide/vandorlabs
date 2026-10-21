package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fluids.*;

/** Cached exposed volume faces use each liquid's atlas artwork, tint and luminosity. */
final class WaterloggedRenderer {
    static void render(TileEntity tile,double x,double y,double z){
        ContainedFluids data=WaterloggedParts.data(tile);if(data==null||!WaterloggedParts.wet(tile.getWorld(),tile.getPos()))return;
        java.util.List<WaterloggedSurfaceMesh.Surface> surfaces=WaterloggedSurfaceMesh.get(tile);
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        boolean depthMask=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);
        try{
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.depthMask(false);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableLighting();GlStateManager.enableCull();GlStateManager.color(1,1,1,1);
            for(int side=0;side<2;side++){
                FluidStack stack=WaterloggedParts.fluid(tile.getWorld(),tile.getPos(),side);if(stack==null)continue;Fluid fluid=stack.getFluid();
                TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(fluid.getStill(stack).toString());
                int color=fluid==FluidRegistry.WATER?0xAF000000|net.minecraft.world.biome.BiomeColorHelper.getWaterColorAtPos(tile.getWorld(),tile.getPos()):fluid.getColor(stack);
                int light=tile.getWorld().getCombinedLight(tile.getPos(),fluid.getLuminosity(stack));for(net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.values()){int neighbor=tile.getWorld().getCombinedLight(tile.getPos().offset(face),fluid.getLuminosity(stack));light=(Math.max(light&65535,neighbor&65535))|(Math.max(light>>16,neighbor>>16)<<16);}
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
                Tessellator tess=Tessellator.getInstance();BufferBuilder buffer=tess.getBuffer();buffer.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR);
                for(WaterloggedSurfaceMesh.Surface face:surfaces)if(face.side==side){
                    if(face.vertices.length==4)for(Vec3d vertex:face.vertices)vertex(buffer,sprite,face.axis,vertex,color);
                    else for(int i=1;i+1<face.vertices.length;i++)for(Vec3d vertex:new Vec3d[]{face.vertices[0],face.vertices[i],face.vertices[i+1],face.vertices[i+1]})vertex(buffer,sprite,face.axis,vertex,color);
                }
                tess.draw();
            }
        }finally{GlStateManager.depthMask(depthMask);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.disableBlend();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.popMatrix();}
    }
    private static void vertex(BufferBuilder buffer,TextureAtlasSprite sprite,int axis,Vec3d p,int color){
        double u=axis==0?p.z:p.x,v=axis==1?p.z:1-p.y;
        buffer.pos(p.x,p.y,p.z).tex(sprite.getInterpolatedU(u*16),sprite.getInterpolatedV(v*16)).color(color>>16&255,color>>8&255,color&255,color>>>24).endVertex();
    }
}
