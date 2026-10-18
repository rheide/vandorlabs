package com.vandorlabs.client;

import com.vandorlabs.blocks.WaterloggedParts;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.AxisAlignedBB;

/** Water uses vanilla still-water artwork and biome tint, clipped to the open shape. */
final class WaterloggedRenderer {
    static void render(TileEntityAnimatedScreenSelector tile,double x,double y,double z){
        List<AxisAlignedBB> cells=WaterloggedParts.gaps(tile.getWorld(),tile.getPos());
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("minecraft:blocks/water_still");
        int color=net.minecraft.world.biome.BiomeColorHelper.getWaterColorAtPos(tile.getWorld(),tile.getPos());
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        GlStateManager.resetColor();GlStateManager.color(1,1,1,1);
        GlStateManager.pushMatrix();GlStateManager.translate(x-tile.getPos().getX(),y-tile.getPos().getY(),z-tile.getPos().getZ());
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);GlStateManager.disableLighting();GlStateManager.disableCull();
        // Stairs can occlude their own cell's skylight; the contained water is
        // exposed in the empty quarter, so use the same neighboring light as its housing.
        int light=TEAnimatedScreenSelector.neighborLight(tile);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
        Tessellator tess=Tessellator.getInstance();BufferBuilder buffer=tess.getBuffer();buffer.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR);
        for(AxisAlignedBB raw:cells){AxisAlignedBB a=raw.shrink(.0005);double[][][] faces={{{a.minX,a.maxY,a.minZ},{a.minX,a.maxY,a.maxZ},{a.maxX,a.maxY,a.maxZ},{a.maxX,a.maxY,a.minZ}},{{a.minX,a.minY,a.minZ},{a.maxX,a.minY,a.minZ},{a.maxX,a.minY,a.maxZ},{a.minX,a.minY,a.maxZ}},{{a.minX,a.maxY,a.minZ},{a.minX,a.minY,a.minZ},{a.minX,a.minY,a.maxZ},{a.minX,a.maxY,a.maxZ}},{{a.maxX,a.maxY,a.minZ},{a.maxX,a.maxY,a.maxZ},{a.maxX,a.minY,a.maxZ},{a.maxX,a.minY,a.minZ}},{{a.minX,a.maxY,a.minZ},{a.maxX,a.maxY,a.minZ},{a.maxX,a.minY,a.minZ},{a.minX,a.minY,a.minZ}},{{a.minX,a.maxY,a.maxZ},{a.minX,a.minY,a.maxZ},{a.maxX,a.minY,a.maxZ},{a.maxX,a.maxY,a.maxZ}}};
            for(double[][] face:faces){double cx=0,cy=0,cz=0;for(double[] v:face){cx+=v[0]/4;cy+=v[1]/4;cz+=v[2]/4;}
                // Skip faces between water subcells to avoid internal transparent seams.
                boolean internal=false;for(AxisAlignedBB other:cells)if(other!=raw && other.grow(.001).contains(new net.minecraft.util.math.Vec3d(cx,cy,cz))){internal=true;break;}if(internal)continue;
                for(int i=0;i<4;i++){double[] v=face[i];buffer.pos(v[0],v[1],v[2]).tex(sprite.getInterpolatedU(i<2?0:16),sprite.getInterpolatedV(i==0||i==3?0:16)).color(color>>16&255,color>>8&255,color&255,175).endVertex();}
            }
        }
        tess.draw();GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.disableBlend();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.popMatrix();
    }
}
