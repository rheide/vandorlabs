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
        ContainedFluids data=WaterloggedParts.data(tile);if(data==null||!data.any())return;
        WaterloggedParts.Volume volume=WaterloggedParts.volume(tile.getWorld(),tile.getPos());
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);
        try{
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);
            for(int side=0;side<2;side++){
                FluidStack stack=data.get(side);if(stack==null)continue;Fluid fluid=stack.getFluid();
                TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(fluid.getStill(stack).toString());
                int color=fluid==FluidRegistry.WATER?0xAF000000|net.minecraft.world.biome.BiomeColorHelper.getWaterColorAtPos(tile.getWorld(),tile.getPos()):fluid.getColor(stack);
                int light=tile.getWorld().getCombinedLight(tile.getPos(),fluid.getLuminosity(stack));for(net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.values()){int neighbor=tile.getWorld().getCombinedLight(tile.getPos().offset(face),fluid.getLuminosity(stack));light=(Math.max(light&65535,neighbor&65535))|(Math.max(light>>16,neighbor>>16)<<16);}
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
                Tessellator tess=Tessellator.getInstance();BufferBuilder buffer=tess.getBuffer();buffer.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR);
                for(WaterloggedParts.Face face:volume.faces)if(face.side==side)for(Vec3d vertex:face.vertices){
                    // Pull outer boundaries in slightly to avoid coplanar glass/fluid faces.
                    double vx=vertex.x==0?.0005:vertex.x==1?.9995:vertex.x,vy=vertex.y==0?.0005:vertex.y==1?.9995:vertex.y,vz=vertex.z==0?.0005:vertex.z==1?.9995:vertex.z;
                    double u=face.axis==0?vertex.z:vertex.x,v=face.axis==1?vertex.z:1-vertex.y;
                    buffer.pos(vx,vy,vz).tex(sprite.getInterpolatedU(u*16),sprite.getInterpolatedV(v*16)).color(color>>16&255,color>>8&255,color&255,color>>>24).endVertex();
                }
                tess.draw();
            }
        }finally{GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.disableBlend();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.popMatrix();}
    }
}
