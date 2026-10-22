package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fluids.*;

/** Cached exposed volume faces use each liquid's atlas artwork, tint and luminosity. */
final class WaterloggedRenderer {
    static TextureAtlasSprite sprite(FluidStack stack){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(stack.getFluid().getStill(stack).toString());}
    static void render(TileEntity tile,double x,double y,double z){
        ContainedFluids data=WaterloggedParts.data(tile);if(data==null||!WaterloggedParts.wet(tile.getWorld(),tile.getPos()))return;
        WaterloggedSurfaceMesh.Entry entry=WaterloggedSurfaceMesh.entry(tile);
        java.util.List<WaterloggedSurfaceMesh.Surface> surfaces=entry.mesh;if(surfaces.isEmpty())return;
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        boolean depthMask=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);
        try{
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.depthMask(false);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableLighting();GlStateManager.enableCull();GlStateManager.color(1,1,1,1);
            Tessellator tess=Tessellator.getInstance();BufferBuilder buffer=tess.getBuffer();buffer.begin(7,BlockSurfaceFormat.get());
            for(int side=0;side<2;side++){
                FluidStack stack=entry.fluids[side];if(stack==null)continue;Fluid fluid=stack.getFluid();
                TextureAtlasSprite still=sprite(stack);
                int color=fluid==FluidRegistry.WATER?0xFF000000|net.minecraft.world.biome.BiomeColorHelper.getWaterColorAtPos(tile.getWorld(),tile.getPos()):fluid.getColor(stack);
                for(WaterloggedSurfaceMesh.Surface face:surfaces)if(face.side==side){
                    Vec3d normal=face.normal;
                    float shade=(float)(normal.x*normal.x*.6+normal.y*normal.y*(normal.y<0?.5:1)+normal.z*normal.z*.8);
                    net.minecraft.util.math.BlockPos sample=face.boundary==null||face.boundary==net.minecraft.util.EnumFacing.UP?tile.getPos():tile.getPos().offset(face.boundary);
                    int light=fluid.getBlock()==null?tile.getWorld().getCombinedLight(sample,fluid.getLuminosity(stack)):fluid.getBlock().getDefaultState().getPackedLightmapCoords(tile.getWorld(),sample);
                    TextureAtlasSprite sprite=still;
                    if(face.vertices.length==4)for(Vec3d vertex:face.vertices)vertex(buffer,sprite,face.axis,vertex,color,shade,light,normal);
                    else for(int i=1;i+1<face.vertices.length;i++){
                        vertex(buffer,sprite,face.axis,face.vertices[0],color,shade,light,normal);
                        vertex(buffer,sprite,face.axis,face.vertices[i],color,shade,light,normal);
                        vertex(buffer,sprite,face.axis,face.vertices[i+1],color,shade,light,normal);
                        vertex(buffer,sprite,face.axis,face.vertices[i+1],color,shade,light,normal);
                    }
                }
            }
            tess.draw();
        }finally{GlStateManager.depthMask(depthMask);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.disableBlend();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.popMatrix();}
    }
    private static void vertex(BufferBuilder buffer,TextureAtlasSprite sprite,int axis,Vec3d p,int color,float shade,int light,Vec3d normal){
        double u=axis==0?p.z:p.x,v=axis==1?p.z:1-p.y;
        double scale=16;
        buffer.pos(p.x,p.y,p.z).color((color>>16&255)*shade/255F,(color>>8&255)*shade/255F,(color&255)*shade/255F,(color>>>24)/255F).tex(sprite.getInterpolatedU(u*scale),sprite.getInterpolatedV(v*scale)).lightmap(light&65535,light>>16).normal((float)normal.x,(float)normal.y,(float)normal.z).endVertex();
    }
}
